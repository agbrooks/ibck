package io.github.agbrooks.ibck.tws

import java.util.logging.Level

import com.ib.client.Types.SecType
import com.ib.client.{Contract, ContractDetails, TickAttrib}
import io.github.agbrooks.ibck.tws.types.TickType.{AskPrice, BidExchange, TickType, UnknownTick}


/**
 * Mixin providing most recent market data for stock / options.
 */
trait QuotesFeature extends BaseTWSAdapter {
  //private final val reqIdGenerator: RequestIdGenerator = new IncrementingRequestIdGenerator
  //private final val accountSummarySnapshot: Snapshot[AccountSummary] = Snapshot(requestAccountSummary)

  // TODO: Determine what should be bundled in a quote; need to update return...
  def stockQuote(symbol: String, primaryExchange: String): Unit = {
    val reqId = requestIdGenerator.nextRequestId()
    val contract = new Contract()
    contract.secType(SecType.STK)
    contract.symbol(symbol)
    contract.currency("USD")
    contract.exchange("SMART")
    contract.primaryExch(primaryExchange)
    // TODO: Gross, more magic numbers!
    clientSocket.reqMktData(reqId, contract,"106,165,225", false, false, new java.util.ArrayList())
  }

  // TODO: Define an OptionChain type...
  private def optionQuote(symbol: String, primaryExchange: String): Unit = {
    val reqId = requestIdGenerator.nextRequestId()

    // STEP 1: Need to figure out which options are available
    // we can do that with reqSecDefOptParams, I think?
    // (If we need to reqContractDetails about stock to get the underlyingConId, then that's STEP 0).

    // STEP 2: Need to actually request pricing data on options w/ reqContractDetails.
    // Should that be a separate function?

    // TODO: We should do reqSecDefOptParams, I guess...?
    // TODO: Turns out reqContractDetails is actually how we get the underlying contract ID in order to use...?
    // reqSecDefOptParams
    val contract = new Contract()
    contract.secType(SecType.OPT)
    contract.symbol(symbol)
    contract.currency("USD")
    contract.exchange("SMART")
    contract.primaryExch(primaryExchange)
    clientSocket.reqContractDetails(reqId, contract)
  }

  // TODO: "On-demand" subscription creation / deletion?
  // TODO: Relating request IDs to things we want...?

  /*
   * Overrides for the relevant portion of the IBKR EWrapper interface
   */

  override def tickPrice(tickerId: Int, field: Int, price: Double, tickAttrib: TickAttrib): Unit = {
    val goodField: TickType = field
    logger.finest(f"tickerId: $tickerId / field: $goodField, price: $price, tickAttrib: $tickAttrib")
  }

  override def tickSize(tickerId: Int, field: Int, size: Int): Unit = {
    val goodField: TickType = field
    logger.finest(f"tickerId: $tickerId / field: $goodField / size: $size")
  }

  override def tickOptionComputation
  (tickerId: Int,
   field: Int,
   impliedVolatility: Double,
   delta: Double,
   optPrice: Double,
   pvDividend: Double,
   gamma: Double,
   vega: Double,
   theta: Double,
   undPrice: Double): Unit = ???

  override def tickGeneric(tickerId: Int, field: Int, value: Double): Unit = {
    val goodField: TickType = field
    logger.finest(f"tickerId: $tickerId / field: $goodField / value: $value")
  }

  override def tickReqParams(reqId: Int, minTick: Double, bboExchange: String, snapshotPerms: Int): Unit = {
    logger.finest(f"reqId: $reqId, minTick: $minTick, bboExchange: $bboExchange, snapshotPerms: $snapshotPerms")
  }

  override def tickString(tickerId: Int, field: Int, value: String): Unit = {
    val goodField: TickType = field
    logger.finest(f"tickerId: $tickerId / field: $goodField / value: $value")
  }

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {
    logger.finest(f"reqId: $reqId, type: $marketDataType")
    // YET MORE magic numbers we have to deal with!
    // 1 RT, 2 frozen, 3 delayed, 4 delayed-frozen
  }

  override def contractDetails(reqId: Int, contractDetails: ContractDetails): Unit = ???

  override def contractDetailsEnd(reqId: Int): Unit = ???
}