package io.github.agbrooks.ibck.tws

import io.github.agbrooks.ibck.tws.types.{AccountSummary, AccountSummaryPart}

import scala.util.Try

// FIXME: Apparently, requesting an account summary will trigger an account summary *subscription*.
// FIXME: We need to handle that a bit differently; this code was written assuming it was a one-time thing.

/**
 * Mixin providing high-level account summary features (see getAccountSummary)
 */
trait AccountSummaryFeature extends BaseTWSAdapter {
  private final val accountSummaryCombiner: MessageCombiner[AccountSummaryPart, AccountSummary] =
    new MessageCombiner(parts => Try(AccountSummary(parts)))

  /**
   * Fetches an AccountSummary, requesting everything in the account's default currency.
   * I have not tried this on non-USD accounts, so I have no idea how it fares.
   * @return
   */
  def getAccountSummary: AccountSummary = accountSummaryCombiner.usingRequestId(
    reqId => clientSocket.reqAccountSummary(reqId, "All", "$LEDGER")
  )

  /*
   * Overrides for the relevant portion of the IBKR EWrapper interface
   */

  override def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit =
    accountSummaryCombiner.submit(reqId, AccountSummaryPart(account, tag, value, currency))

  override def accountSummaryEnd(reqId: Int): Unit =
    accountSummaryCombiner.finish(reqId)
}