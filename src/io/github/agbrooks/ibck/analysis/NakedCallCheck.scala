package io.github.agbrooks.ibck.analysis
import io.github.agbrooks.ibck.tws.types.{AccountData, Position}

/**
 * PortfolioCheck ensuring that all short calls are covered by long shares.
 * Exclusively considers equity options.
 */
class NakedCallCheck extends PortfolioCheck {
  def title(): String = "no naked calls"
  override def run(positions: Iterable[Position], account: AccountData): Iterable[PortfolioCheckResult] = {
    val sharesNeeded: Map[String, Double] = positions
      .filter(p => (p.isCall && p.isShort && p.isEquityOption))
      .map(p => (p.symbol(), p.multiplier.get * -p.quantity))
      .toMap
    val longShares: Map[String, Double] = positions
      .filter(p => (p.isStock && p.isLong))
      .map(p => (p.symbol(), p.quantity))
      .toMap
    val warnings = sharesNeeded
      .map { case (symbol, neededToCover) => (symbol, neededToCover - longShares.getOrElse(symbol, 0.0)) }
      .filter(_._2 < 0)
      .map {
        case (symbol, numMissing) => PortfolioWarning(s"Need $numMissing more shares of $symbol to cover short calls")
      }
    if (warnings.isEmpty) {
      Seq(PortfolioComment(s"All short equity calls are covered"))
    } else {
      warnings
    }
  }
}
