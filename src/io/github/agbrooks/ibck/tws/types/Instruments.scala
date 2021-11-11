package io.github.agbrooks.ibck.tws.types

import com.ib.client.Contract
import com.ib.client.Types.{Right, SecType}

import java.text.{DateFormat, SimpleDateFormat}
import java.util.Date

/**
 * A simple identifier for a stock (just a symbol / primary exchange)
 */
case class Stock( symbol: String, primaryExchange: String )
object Stock {
  import scala.language.implicitConversions
  /**
   * Implicit conversion to TWS API's 'Contract' object, naively assuming USD and quotes from SMART (which is good
   * enough for the purposes of this program)
   */
  implicit def asContract(stock: Stock): Contract = {
    val contract = new Contract()
    contract.secType(SecType.STK)
    contract.symbol(stock.symbol)
    contract.currency("USD")
    contract.exchange("SMART")
    contract.primaryExch(stock.primaryExchange)
    contract
  }
}

/**
 * A simple identifier for an Option contract.
 */
case class OptionContract
  ( symbol: String,
    primaryExchange: String,
    expiry: Date,
    strike: Double, // NB: not BigDecimal, TWS actually gives us a Double and we probably want to preserve it
    right: Right )
object OptionContract {
  import scala.language.implicitConversions

  // TWS API's Contract uses strings like this to represent dates, which seems goofy
  private final val TWS_LAST_TRADE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd")
  /**
   * Implicit conversion to TWS API's 'Contract' object, assuming USD and quotes from SMART (which is good enough
   * for the purposes of this program)
   */
  implicit def asContract(option: OptionContract): Contract = {
    val contract = new Contract()
    contract.secType(SecType.OPT)
    contract.symbol(option.symbol)
    contract.currency("USD")
    contract.exchange("SMART")
    contract.primaryExch(option.primaryExchange)
    contract.right(option.right)
    contract.strike(option.strike)
    contract.lastTradeDateOrContractMonth(TWS_LAST_TRADE_DATE_FORMAT.format(option.expiry))
    contract
  }

  /**
   * Attempt to convert a TWS API Contract to an OptionContract.
   */
  def apply(contract: Contract): Option[OptionContract] = {
    None // TODO implement this, we're probably going to want this for quotes...
  }
}