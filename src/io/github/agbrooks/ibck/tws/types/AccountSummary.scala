package io.github.agbrooks.ibck.tws.types

import scala.collection.MapView
import scala.util.Try

/**
 * All account summary information pertaining to all accounts.
 */
case class AccountSummary(all: Map[String, AccountData]) {
  /**
   * @return Names of all accounts.
   */
  def accounts: Iterable[String] = all.keys

  /**
   * Fetch the AccountData associated with an account number.
   */
  def get(account: String): Option[AccountData] = all.get(account)
}

object AccountSummary {
  def apply(parts: Iterable[AccountSummaryPart]): AccountSummary = {
    val accountsMap = parts
      .groupBy(_.account).view
      .mapValues(buildAccountData).toMap
    new AccountSummary(accountsMap)
  }
  private def buildAccountData(parts: Iterable[AccountSummaryPart]): AccountData = {
    // TODO: So, uh, what if currency isn't USD for everything?
    val tagsMap: Map[String, String] =
      parts
        .groupBy(_.tag).view
        .mapValues(_.head.value).toMap
    Seq("CashBalance", "Currency", "AccountOrGroup").map(tagsMap.get) match {
      case Seq(Some(cash), Some(currency), Some(name)) => AccountData(name, BigDecimal(cash), currency, tagsMap)
      case _ => throw new RuntimeException("...") // TODO: Use a sensible exception, not this crap
    }
  }
}

/**
 * Account summary data for one account number.
 *
 * @param friendlyName account nickname (from AccountOrGroup tag),
 * @param cash current cash balance (from CashBalance tag)
 * @param currency current currency (from Currency tag)
 * @param raw all tag / value pairs reported by TWS. TWS gives us a lot of "magic strings", and it's hard to know
 *            what tags _could_ be sent. For debugging purposes, it's helpful to know exactly what it sent back.
 */
case class AccountData
( friendlyName: String,
  cash: BigDecimal,
  currency: String, // would have been nice if TWS provided an enum here...
  raw: Map[String, String]
)

/**
 * A fragment of an account summary. Correspends directly to the data received from the accountSummary callback on
 * EWrapper.
 * @param account account number
 * @param tag type of quantity being provided in this part (eg, 'CashBalance')
 * @param value quantity provided (some tags are not numeric)
 * @param currency currency associated with the quantity -- yes, even non-numeric things like the account nickname
 *                 have an associated currency (not to be confused with the Currency tag); I haven't the foggiest why
 *                 the TWS API was designed this way
 */
case class AccountSummaryPart(account: String, tag: String, value: String, currency: String)

