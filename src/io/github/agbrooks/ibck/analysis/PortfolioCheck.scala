package io.github.agbrooks.ibck.analysis

import io.github.agbrooks.ibck.tws.types.{AccountData, Position}

/**
 * A concrete recommendation / comment from a PortfolioCheck.
 */
sealed trait PortfolioCheckResult {
  val description: String
  val isProblem: Boolean
}

/**
 * A benign observation made during a PortfolioCheck.
 * Does not indicate a problem, but should probably be reported to the user.
 * @param description A human-readable description of the observation.
 */
final case class PortfolioComment(description: String) extends PortfolioCheckResult { val isProblem = false }

/**
 * A problem identified by a PortfolioCheck that the user should resolve.
 * @param description A human-readable description of the problem.
 */
final case class PortfolioWarning(description: String) extends PortfolioCheckResult { val isProblem = true }

/**
 * Some sanity-check on a portfolio.
 */
trait PortfolioCheck {
  def title(): String
  def run(positions: Iterable[Position], account: AccountData): Iterable[PortfolioCheckResult]
}


