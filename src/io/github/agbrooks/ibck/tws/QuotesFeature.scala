package io.github.agbrooks.ibck.tws

import scala.collection.mutable
import java.util.logging.Level
import com.ib.client.Types.SecType
import com.ib.client.{Contract, ContractDetails, TickAttrib}
import io.github.agbrooks.ibck.tws.types.{NascentQuote, Quote, Stock, TickType}
import io.github.agbrooks.ibck.tws.types.TickType.{AskPrice, AskSize, BidExchange, BidPrice, BidSize, TickType, UnknownTick}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private case class QuoteParts
  ( bid: Snapshot[BigDecimal]
  , ask: Snapshot[BigDecimal]
  , bidSize: Snapshot[Int]
  , askSize: Snapshot[Int] ) {

  def setPrice(tick: TickType, price: Double): Unit = {
    val priceDecimal = BigDecimal(price: Double).setScale(3)
    tick match {
      case BidPrice => bid.replace(priceDecimal)
      case AskPrice => ask.replace(priceDecimal)
      case _ => ()
    }
  }

  def setSize(tick: TickType, size: Int): Unit = {
    tick match {
      case BidSize => bidSize.replace(size)
      case AskSize => askSize.replace(size)
      case _ => ()
    }
  }

  def get(): Future[Quote] = for {
    bid <- bid.get
    ask <- ask.get
    bidSize <- bidSize.get
    askSize <- askSize.get
  } yield Quote(bid=bid, ask=ask, bidSize=bidSize, askSize=askSize)
}
private object QuoteParts {
  def empty(): QuoteParts = QuoteParts(
    Snapshot.empty(),
    Snapshot.empty(),
    Snapshot.empty(),
    Snapshot.empty()
  )
}

/**
 * Mixin providing most recent market data for stock / options.
 */
trait QuotesFeature extends BaseTWSAdapter {

  private final val stockQuotes: mutable.HashMap[Int, QuoteParts] = new mutable.HashMap
  private final val stockQuoteIds: mutable.HashMap[Stock, Int] = new mutable.HashMap

  /**
   * Fetch a stock quote.
   * @param stock the
   * @return
   */
  def stockQuote(stock: Stock): Future[Quote] = this.synchronized {
      stockQuoteIds.get(stock) match {
        case Some(existingReqId) =>
          // Already subscribed, sneak a peek at the latest
          stockQuotes(existingReqId)
        case None =>
          // Not subscribed, demand some market data
          val reqId = requestIdGenerator.nextRequestId()
          val parts = QuoteParts.empty()
          stockQuotes.addOne(reqId -> parts)
          stockQuoteIds.addOne(stock -> reqId)
          // TODO: Check that we don't need any of the right column tick types.
          // (eg, 106 is IV, https://interactivebrokers.github.io/tws-api/tick_types.html)
          clientSocket.reqMktData(reqId, stock, "", false, false, new java.util.ArrayList())
          parts
      }
    }.get()

  def stockQuote(symbol: String, primaryExchange: String): Future[Quote] =
    stockQuote(Stock(symbol=symbol, primaryExchange=primaryExchange))

  // TODO: We probably need an OptionChain type...
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
  // TODO: OK now this calls contractDetails at some point...
  // TODO: Now we use reqSecDefOptParams against whatever strike/expiry we care about...
  // TODO: Then that calls... ???

  // TODO: "On-demand" subscription creation / deletion?
  // TODO: Relating request IDs to things we want...?

  /*
   * Overrides for the relevant portion of the IBKR EWrapper interface
   */
  override def tickPrice(tickerId: Int, field: Int, price: Double, tickAttrib: TickAttrib): Unit = {
    stockQuotes.get(tickerId) match {
      case None => logger.warning(f"received price tick for unrecognized msg id $tickerId")
      case Some(parts) => parts.setPrice(TickType.lookup(field), price)
    }
    val goodField: TickType = TickType.lookup(field)
    logger.finest(f"tickPrice(tickerId: $tickerId, field: $goodField, price: $price, tickAttrib: $tickAttrib)")
  }

  override def tickSize(tickerId: Int, field: Int, size: Int): Unit = {
    stockQuotes.get(tickerId) match {
      case None => logger.warning(f"received size tick for urecognized msg id $tickerId")
      case Some(parts) => parts.setSize(TickType.lookup(field), size)
    }
    val goodField: TickType = TickType.lookup(field)
    logger.finest(f"tickSize(tickerId: $tickerId, field: $goodField, size: $size)")
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
    val goodField: TickType = TickType.lookup(field)
    logger.warning(f"tickerId: $tickerId / field: $goodField / value: $value")
  }

  override def tickReqParams(reqId: Int, minTick: Double, bboExchange: String, snapshotPerms: Int): Unit = {
    logger.warning(f"reqId: $reqId, minTick: $minTick, bboExchange: $bboExchange, snapshotPerms: $snapshotPerms")
  }

  override def tickString(tickerId: Int, field: Int, value: String): Unit = {
    val goodField: TickType = TickType.lookup(field)
    logger.warning(f"tickerId: $tickerId / field: $goodField / value: $value")
  }

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {
    logger.finest(f"reqId: $reqId, type: $marketDataType")
    // YET MORE magic numbers we have to deal with!
    // 1 RT, 2 frozen, 3 delayed, 4 delayed-frozen
  }

  override def contractDetails(reqId: Int, contractDetails: ContractDetails): Unit = ???

  override def contractDetailsEnd(reqId: Int): Unit = ???
}