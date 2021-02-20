package io.github.agbrooks.ibck.tws.types

import java.text.SimpleDateFormat
import java.util.Date

import com.ib.client.Contract
import com.ib.client.Types.{Right, SecType}

import scala.collection.immutable.HashSet
// import scala.language.implicitConversions
import scala.util.Try

private object Constants {
  // lastTradeDateOrContractMonth patterns
  // full date for options; futures will drop the day
  final val EXPIRATION_DATE_FORMAT: SimpleDateFormat = new SimpleDateFormat("yyyymmdd")

  // SecTypes that are options
  final val ALL_OPTION_TYPES: Set[SecType] = HashSet(
    SecType.OPT, // equity option
    SecType.IOPT // index option
  )

  // Any security type that has an expiration date
  final val EXPIRING_SECURITY_TYPES: Set[SecType] =
    ALL_OPTION_TYPES ++
      HashSet(
        SecType.BILL,
        SecType.BOND,
        SecType.CFD, // contract-for-difference
        SecType.FOP, // futures option
        SecType.FWD, // forward
        SecType.FUT, // future (non-continuous!)
        SecType.FIXED, // fixed-income annuity (I think? TWS docs are lacking)
        SecType.WAR, // warrant
      )
}

/**
 * A Position is just a 'Contract' associated with ownership information.
 * Note that 'Contract' is a bit of a misnomer (thanks, TWS API): stock, options, and futures are all represented by
 * 'Contract's.
 * @param contract the "contract" in question
 * @param quantity the quantity we own
 * @param averageCost average cost of the "contract"
 */
case class Position(contract: Contract, quantity: Double, averageCost: Double) {

  def isShort: Boolean = quantity < 0
  def isLong: Boolean = quantity > 0

  def isOneOf(types: SecType*): Boolean = isOneOf(types)
  def isOneOf(types: Iterable[SecType]): Boolean = types.exists(_ == contract.secType())

  def isStock: Boolean = isOneOf(SecType.STK)
  def isEquityOption: Boolean = isOneOf(SecType.OPT)
  def isOption: Boolean = isOneOf(Constants.ALL_OPTION_TYPES)
  def isPut: Boolean = contract.right() == Right.Put
  def isCall: Boolean = contract.right() == Right.Call

  // BEWARE: Assuming that TWS sends a non-zero/non-null strike if and only if the position has a strike.
  // This is true for anything I trade (stocks, equity options) but it's possible that some other security type I don't
  // know as much about behaves differently...
  def strike: Option[Double] = Option(contract.strike()).filter(_ != 0)

  /**
   * Get the position's expiration, if it has one.
   *
   * NB: If the contract is a future, the Date for the expiration will be set to the start of each
   * month (note that futures actually expire on the third Friday of that month).
   */
  def expiration: Option[Date] = {
    val expString = contract.lastTradeDateOrContractMonth()
    val maybeExpiration =
      Option(expString)
        .filter(_ != "")
        .flatMap(x =>
          Try(Constants.EXPIRATION_DATE_FORMAT.parse(x)).toOption
            .orElse(Try(Constants.EXPIRATION_DATE_FORMAT.parse(s"${x}01")).toOption))

    // Sanity check: certain securities always ought to have an expiration
    if (shouldHaveExpiration && maybeExpiration.isEmpty) {
      throw new RuntimeException(s"expected expiration for ${contract.secType()}, yet could not parse '$expString'")
    }
    maybeExpiration
  }

  private def shouldHaveExpiration: Boolean = Constants.EXPIRING_SECURITY_TYPES contains contract.secType()
}

object Position {
  import scala.language.implicitConversions
  implicit def toContract(pos: Position): Contract = pos.contract
}