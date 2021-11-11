package io.github.agbrooks.ibck.tws.types

import io.github.agbrooks.ibck.tws.Snapshot
import io.github.agbrooks.ibck.tws.types.TickType.{AskSize, BidSize, TickType}

/**
 * A quote for some contract.
 */
case class Quote
( bid: BigDecimal,
  ask: BigDecimal,
  bidSize: Int,
  askSize: Int ) {
  lazy val midpoint: BigDecimal = (bid + ask) / 2
}