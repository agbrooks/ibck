package io.github.agbrooks.ibck.tws.types

/**
 * The TWS API is tragically reliant on magic numbers to indicate certain types of market data ticks.
 * This enumeration is a nice workaround that lets you pattern-match on human-readable values and
 * UnknownTick(someInt) for all others.
 *
 * TickTypes can be freely & automatically inter-converted with Ints.
 * See: https://interactivebrokers.github.io/tws-api/tick_types.html
 */
object TickType extends Enumeration {
  import scala.language.implicitConversions;
  type TickType = Value
  protected sealed trait TickLike { val code: Int }

  // A recognized TWS TickType
  protected sealed case class Tick(code: Int) extends super.Val with TickLike
  // An unrecognized TWS TickType. Public so we can match on it.
  sealed case class UnknownTick(code: Int) extends super.Val with TickLike
  // All recognized tick types.
  // A complete list is given here: https://interactivebrokers.github.io/tws-api/tick_types.html
  // We don't actually care about most of these -- for now, just pick and choose the ones we care about
  // and let the rest be UnknownTick(whatever).
  final val BidSize = Tick(0)
  final val BidPrice = Tick(1)
  final val AskPrice = Tick(2)
  final val AskSize = Tick(3)
  final val LastPrice = Tick(4)
  final val ClosePrice = Tick(9)
  final val ImpliedVol = Tick(24)
  final val BidExchange = Tick(32)
  final val AskExchange = Tick(33)

  // auto-conversion to/from ints
  @transient lazy private val toIntsMap: Map[TickType, Int] = fromIntsMap.map{case (t, int) => (int, t)}
  @transient lazy private val fromIntsMap: Map[Int, TickType] = this.values.flatMap {
    case tick: Tick => Some(tick.code, tick)
    case _ => None
  }.toMap
  //implicit def intToTickType(int: Int): TickType = fromIntsMap.getOrElse(int,UnknownTick(int))
  implicit def tickTypeToInt(t: TickType): Int = toIntsMap(t)

  def lookup(code: Int): TickType = fromIntsMap.getOrElse(code, UnknownTick(code))
}
