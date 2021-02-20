package io.github.agbrooks.ibck.tws

import java.util.logging.Level

import io.github.agbrooks.ibck.tws.types.{AccountSummary, AccountSummaryPart}

import scala.concurrent.Future

/**
 * Mixin providing high-level account summary features (see getAccountSummary)
 */
trait AccountSummaryFeature extends BaseTWSAdapter {
  private final val accountSummarySnapshot: Snapshot[AccountSummary] = Snapshot(requestAccountSummary)
  private var parts: List[AccountSummaryPart] = List.empty
  private var subscriptionReqId: Int = -1

  // should probably only be called once...
  private def requestAccountSummary(): Unit = {
    subscriptionReqId = requestIdGenerator.nextRequestId()
    clientSocket.reqAccountSummary(subscriptionReqId, "All", "$LEDGER")
  }

  /**
   * Fetches an AccountSummary, requesting everything in the account's default currency.
   * I have not tried this on non-USD accounts, so I have no idea how it fares.
   * @return
   */
  def getAccountSummary: Future[AccountSummary] = accountSummarySnapshot.get

  /*
   * Overrides for the relevant portion of the IBKR EWrapper interface
   */
  override def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit = {
    if (reqId != subscriptionReqId) {
      logger.info(f"ignoring account summary data for unrecognized request $reqId")
    } else {
      parts = AccountSummaryPart(account, tag, value, currency) :: parts
    }
  }

  override def accountSummaryEnd(reqId: Int): Unit = {
    if (reqId != subscriptionReqId) {
      logger.info(f"ignoring account summary end for unrecognized request $reqId")
    } else {
      accountSummarySnapshot.replace(AccountSummary(parts.reverse))
      logger.finer("updated account summary data snapshot")
    }
  }
  // TODO: should we do anything special if the request ID is not recognized?
}