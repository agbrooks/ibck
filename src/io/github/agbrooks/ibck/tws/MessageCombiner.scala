
package io.github.agbrooks.ibck.tws

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Awaitable, Future, Promise}
import scala.util.Try
import scala.language.postfixOps
import java.util.logging.Logger


/**
 * The MessageCombiner offers a way to incrementally build up complete responses out of piecemeal TWS messages.
 * For reference, the typical usage for an EWrapper/EClientSocket to request a list of all open orders would look
 * something like this:
 *
 * 1. A requestor thread generates a unique request ID.
 * 2. The requestor sends a request with that ID to TWS for all open orders.
 * 3. The requestor blocks while waiting for all open orders to be sent over.
 * 3. The reader thread calls 'openOrder' repeatedly with the applicable request ID (once for each open order) as
 *    TWS sends them. Note that our 'openOrder' implementation needs to track/accumulate these under each request ID.
 * 4. The reader thread calls 'openOrderEnd' to indicate that it's done.
 * 5. The requestor is unblocked, and retrieves all open orders that we tracked.
 *
 * Many other parts of the EWrapper interface (eg, accountSummary or position) follow a similar pattern.
 *
 * A MessageCombiner abstracts away most of the tedious parts of doing this by tracking / combining incremental
 * messages by request ID (eg, resulting from 'openOrder' calls) to form a "whole" (eg, after 'openOrderEnd' is
 * received) and blocking/unblocking the requesting thread until that "whole" is received.
 *
 * An example is worth a thousand words: go look at theAccountSummaryFeature mixin for a simple example using
 * `accountSummary` and `accountSummaryEnd`.
 *
 * MessageCombiners are thread-safe, and performing any operation will lock the object.
 * TODO: Confirm I'm using scala's synchronized correctly; this needs to be true
 *
 * @param combiner Function describing how to merge a List of Parts into a Whole. This function should not block.
 * @tparam Part Type of each incremental message, which can be submit()-ted to the MessageCombiner
 * @tparam Whole Eventual result produced by the MessageCombiner after finish() is called
 */
class MessageCombiner[Part, Whole](val idGenerator: RequestIdGenerator, val combiner: Iterable[Part] => Try[Whole]) {
  private val requests: mutable.Map[Int, Fragments] = mutable.Map.empty
  protected val logger: Logger = Logger.getLogger(getClass.getName)

  // We could probably do something more performant than List. It's pretty expedient, though.
  private case class Fragments(parts: List[Part] = List(), result: Promise[Whole] = Promise()) {
    def submit(part: Part): Fragments = Fragments(part :: parts, result)
    def finish(): Unit = result.complete(combiner.apply(parts.reverse))
    def cancel(): Unit = result.failure(new Exception) // TODO: Use an actually useful exception...
  }

  /**
   * Allocate a new request id and use it to place a request with the function specified, then block until
   * a complete, combined message is ready or 10 seconds have passed.
   *
   * If a failure occurs, or if a response is not received in a timely manner, the MessageCombiner will
   * cancel the request and re-raise the exception.
   *
   * @param makeRequest function to place the request, which should cause another thread to call submit() and eventually
   *                    finish().
   * @return The assembled Whole.
   */
  def usingRequestId(makeRequest: Int => Unit): Future[Whole] = {
    val (req, promise) = nextRequest()
    try {
      makeRequest(req)
      promise.future
    } catch {
      case exc: Exception =>
        cancel(req)
        throw exc
    }
  }

  /**
   * Submit part of the whole to the MessageCombiner, as part of the request associated with a given request id.
   * @param reqId the request id
   * @param part the incremental message
   */
  def submit(reqId: Int, part: Part): Unit = synchronized {
    requests.updateWith(reqId) {
      case None => {
        logger.warning(f"Got msg for unexpected request id $reqId: $part")
        None
      }
      case existing => existing.map(frags => frags.submit(part))
    }
  }

  /**
   * Tell the MessageCombiner that all parts of the message have arrived. All parts of the request are combined
   * and the promise generated with `nextRequest()` is completed with the result.
   *
   * Nothing happens if the request id is not recognized.
   * @param reqId the request id
   */
  def finish(reqId: Int): Unit = synchronized {
    requests.remove(reqId).foreach(_.finish())
  }

  /**
   * Cancel any request with the given request id. All parts pertaining to that request are forgotten, and the promise
   * associated with the request is cancelled.
   *
   * If no such request exists, nothing happens.
   * @param reqId the request id
   */
  def cancel(reqId: Int): Unit = synchronized {
    requests.remove(reqId).foreach(_.cancel())
  }

  /**
   * Generate a unique request identifier and a promise for the eventually generated Whole.
   * When finish() is called for that requestId, the promise will be completed with a Whole generated from all parts
   * that were submitted under that id.
   * @return the integer request ID and a promise for the Whole.
   */
  private def nextRequest(): (Int, Promise[Whole]) = synchronized {
    val reqId = idGenerator.nextRequestId()
    val fragments = Fragments()
    requests.put(reqId, fragments)
    (reqId, fragments.result)
  }
}