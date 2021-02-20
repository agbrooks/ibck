package io.github.agbrooks.ibck.tws

import java.util.concurrent.atomic.AtomicReference

/**
 * Any class that can generate an integer request ID.
 */
trait RequestIdGenerator {
  /**
   * Produce the next request ID.
   * @return the request id
   */
  def nextRequestId(): Int
}

/**
 * Thread-safe RequestIdGenerator that generates request ids by incrementing by 1.
 */
class IncrementingRequestIdGenerator extends RequestIdGenerator {
  private val lastId: AtomicReference[Int] = new AtomicReference(0)
  override def nextRequestId(): Int = lastId.getAndUpdate(x => x + 1)
}

/**
 * A "Dummy" RequestIdGenerator that always returns a constant value.
 */
class DummyRequestIdGenerator(id: Int = 0) extends RequestIdGenerator {
  override def nextRequestId(): Int = id
}