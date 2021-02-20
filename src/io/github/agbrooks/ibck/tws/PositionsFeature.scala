package io.github.agbrooks.ibck.tws
import com.ib.client.Contract
import io.github.agbrooks.ibck.tws.types.Position

import scala.concurrent.{Awaitable, Future}
import scala.language.implicitConversions
import scala.util.Try

/**
 * Mixin allowing inheritors to 'getPositions'.
 */
trait PositionsFeature extends BaseTWSAdapter {
  private final val posCombiner: MessageCombiner[Position, Array[Position]] =
    new MessageCombiner(requestIdGenerator, parts => Try(parts.toArray))

  /**
   * Get all positions associated with the specified account.
   *
   * FIXME: Behavior is not clear if the account does not exist -- TWS seems to be picking some kind of default; I need
   * to figure out how that works.
   *
   * @param account The account number string
   * @return All positions in the account
   */
  def getPositions(account: String): Future[Array[Position]] = posCombiner.usingRequestId(reqId => {
    // FIXME: The "model code" isn't particularly well documented in TWS. I seem to get correct results by
    // FIXME: specifying null, but I don't know if that can be relied on for other users!
    clientSocket.reqPositionsMulti(reqId, account, null)
  })

  /*
   * Overrides for the relevant portion of the IBKR EWrapper interface:
   */

  override def positionMulti
  (reqId: Int, account: String, modelCode: String, contract: Contract, pos: Double, avgCost: Double): Unit = {
    // TODO: I've observed the modelCode to be null whenever I've made a request. No idea if we need to do
    // TODO: anything fancier here.
    posCombiner.submit(reqId, Position(contract, pos, avgCost))
  }
  override def positionMultiEnd(reqId: Int): Unit = posCombiner.finish(reqId)

  // It would be quite odd if these were used, since no requests we made should have caused us to receive messages
  // that call them:

  override def position(account: String, contract: Contract, pos: Double, avgCost: Double): Unit = {
    logger.warning(f"got unexpected 'position' message for $account")
  }
  override def positionEnd(): Unit = {
    logger.warning(f"got unexpected 'positionEnd' message")
  }
}
