package io.github.agbrooks.ibck.analysis
import io.github.agbrooks.ibck.tws.types.{AccountData, Position}

import scala.util.control.Breaks.break

class MaxLossCheck(allowableLossPct: Double) extends PortfolioCheck {
  def title(): String = s"max loss does not exceed $allowableLossPct%"

  override def run(positions: Iterable[Position], account: AccountData): Iterable[PortfolioCheckResult] = {
    val positionsByUnderlying = positions.groupBy(_.contract.symbol)
    val maxLossByUnderlying = positionsByUnderlying.view.mapValues(maxLossOneUnderlying)
    println(maxLossByUnderlying.toSeq) // FIXME AB DEBUG

    val undefinedMaxLossUnderlyings = maxLossByUnderlying.filter(_._2.isEmpty).keySet
    if (undefinedMaxLossUnderlyings.nonEmpty) {
      return undefinedMaxLossUnderlyings.map(sym => PortfolioWarning(s"UNLIMITED LOSS POTENTIAL for ${sym}"))
    }
    // FIXME AB DEBUG
    println(s"Your account value is ${accountValue(positions, account)}")
    val totalMaxLossPct = 100.0 * maxLossByUnderlying.values.flatMap(_.toSeq).sum / accountValue(positions, account)
    if (totalMaxLossPct > allowableLossPct) {
      Seq(PortfolioWarning(s"unacceptable max loss: $totalMaxLossPct%"))
    } else {
      Seq(PortfolioComment(s"max loss: $totalMaxLossPct%"))
    }
  }

  private def accountValue(positions: Iterable[Position], account: AccountData): BigDecimal = {
    // TODO: who knows if this works for things that aren't stock/options; should probably throw
    // FIXME: This is seriously a kludge; it's just straight-up wrong
    account.cash + positions.map(p => p.averageCost * p.quantity * p.multiplier.getOrElse(1.0)).sum
  }

  private def maxLossOneUnderlying(positions: Iterable[Position]): Option[BigDecimal] = {
    require(positions.map(_.contract.symbol).toSet.size < 2, "only one underlying at a time can be considered")
    val (puts, calls) = positions.filter(_.isOption).partition(_.isPut)
    val (shortStock, longStock) = positions.filter(_.isStock).partition(_.isShort)
    val (shortPuts, longPuts) = puts.partition(_.isShort)
    val (shortCalls, longCalls) = calls.partition(_.isShort)

    // TODO: Write some tests for this, would be too easy to make an arithmetic error
    // FIXME: Once quotes properly implemented, need to actually determine value now, not value when purchased...
    // Pair off short puts / long stock against long puts with highest strikes
    val downsideSharesRisked =
      longStock.map(_.quantity).sum +
        shortPuts.map(p => -p.quantity * p.multiplier.getOrElse(100.0)).sum
    val downsideValueRisked =
      longStock.map(s => s.quantity * s.averageCost).sum +
        shortPuts.map(p => -p.quantity * p.multiplier.getOrElse(100.0) * p.strike()).sum

    val bestLongPuts = longPuts.toSeq.sortWith(_.strike() > _.strike())
    var downsideMaxLoss = downsideValueRisked
    var downsideSharesUncovered = downsideSharesRisked
    for (put <- bestLongPuts) {
      val nToCover = math.min(put.quantity * put.multiplier.getOrElse(100.0), downsideSharesUncovered)
      downsideSharesUncovered -= nToCover
      if (downsideSharesUncovered < 0) {
        downsideMaxLoss -= nToCover * put.strike()
      }
    }

    // Pair off short calls / short stock against long calls with lowest strikes
    val upsideSharesRisked = 0
    // TODO finish this

    Some(BigDecimal.decimal(downsideMaxLoss))
  }

  //private def maxLossOneSided(optionFilter)

}
