package io.github.agbrooks.ibck.tws.types

import com.ib.client.Contract
import com.ib.client.Types.{SecType, Right}

import scala.language.implicitConversions

/**
 * A Position is just a 'Contract' associated with ownership information.
 * Note that 'Contract' is a bit of a misnomer (thanks, TWS API): stock, options, and futures are all represented by
 * 'Contract's.
 * @param contract the "contract" in question
 * @param quantity the quantity we own
 * @param averageCost average cost of the "contract"
 */
case class Position(contract: Contract, quantity: Double, averageCost: Double) {
  implicit def toContract: Contract = contract

  def isShort: Boolean = quantity < 0
  def isLong: Boolean = quantity > 0

  def isStock: Boolean = contract.secType() == SecType.STK
  def isOption: Boolean = contract.secType() == SecType.OPT
  def isPut: Boolean = isOption && contract.right() == Right.Put
  def isCall: Boolean = isOption && contract.right() == Right.Call

  // FIXME: Hey! What about warrants? Those have strikes, too!
  def strike: Option[Double] = if (isOption) Some(contract.strike()) else None
  // FIXME: Make this not a string -- it's in YYYYMMDD form...
  def expiration: Option[String] = if (isOption) Some(contract.lastTradeDateOrContractMonth()) else None
}