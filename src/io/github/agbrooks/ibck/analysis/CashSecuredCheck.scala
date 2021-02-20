package io.github.agbrooks.ibck.analysis
import io.github.agbrooks.ibck.tws.types.{AccountData, Position}

/**
 * PortfolioCheck ensuring that the account has enough cash to secure the exercise of all short puts should all
 * underlyings immediately become worthless.
 *
 * @param percentSecured This check will fail when the cash in the account is smaller than percentSecured of
 *                       the sum of the quantity * multiplier * strike of all short puts.
 */
class CashSecuredCheck(percentSecured: Double = 100) extends PortfolioCheck {
  def title(): String = "puts are cash-secured"
  override def run(positions: Iterable[Position], account: AccountData): Iterable[PortfolioCheckResult] = {
    val cashToSecure = cashToSecureAll(positions)
    val cashAboveThreshold = account.cash - (cashToSecure * percentSecured / 100.0)
    if (cashAboveThreshold < 0) {
      Seq(PortfolioWarning(
        s"Cash shortfall of ${-cashAboveThreshold} ${account.currency} to secure ${percentSecured}% of ${cashToSecure}"
      ))
    } else {
      Seq(PortfolioComment(
        s"Have ${cashAboveThreshold} ${account.currency} exceeding req'd to secure ${percentSecured}% of ${cashToSecure}"
      ))
    }
  }

  private def cashToSecureAll(positions: Iterable[Position]): BigDecimal =
    positions
      .view
      .filter(pos => pos.isShort && pos.isPut)
      .map(worstCaseCashForExercise)
      .sum

  private def worstCaseCashForExercise(position: Position): BigDecimal =
    (position.multiplier, position.strike) match {
      case (Some(multiplier), Some(strike)) if position.isShort && position.isPut =>
        -position.quantity * multiplier * strike
      case _ =>
        0.0
    }
}
