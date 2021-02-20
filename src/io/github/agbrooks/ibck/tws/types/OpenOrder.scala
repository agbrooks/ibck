package io.github.agbrooks.ibck.tws.types

import com.ib.client.{Contract, Order, OrderState, OrderStatus}


/**
 * An open order, along with some details about any partial/incomplete execution.
 * It is recommended to construct the OpenOrder with the OpenOrder companion object.
 *
 * @param orderId Order ID for the open order
 * @param position Position that would be added to the portfolio were the open order filled.
 *                 Note that the average fill price is a "best guess" based on the fill price for partial orders
 *                 and the limit price, and should not be taken too seriously.
 * @param order the raw TWS Order object
 * @param state the raw TWS OrderState object
 * @param execution details about the execution of the order (mirrors EWrapper orderStatus message)
 */
case class OpenOrder
(orderId: Int,
 position: Position,
 order: Order,
 state: OrderState,
 execution: OrderExecution ) {
  val contract: Contract = position.contract
  val status: OrderStatus = execution.status
  val filled: Double = execution.filled
  val remaining: Double = execution.remaining
  val avgFillPrice: Double = execution.avgFillPrice
  val permId: Int = execution.permId // ???
  val parentId: Int = execution.parentId
  val lastFillPrice: Double = execution.lastFillPrice
  val clientId: Int = execution.clientId
  val whyHeld: String = execution.whyHeld // ???
  /**
   * Price cap (if price-capped)
   */
  val mktCapPrice: Double = execution.mktCapPrice
}
object OpenOrder {
  /**
   * Construct an OpenOrder by merging base/execution message info.
   * The average fill price in the constructed position is a "best guess" based on the fill price for partial
   * orders and any limit price set for the open order. Don't take it too seriously.
   *
   * Both the base and execution order IDs must match.
   * @param base OrderBaseInfo (constructed directly from TWS message)
   * @param execution OrderExecution (constructed directly from TWS message)
   * @return
   */
  def apply(base: OrderBaseInfo, execution: OrderExecution): OpenOrder = {
    if (base.id != execution.id) {
      // abort: not pairing execution info w/ correct order...
      throw new IllegalArgumentException(
        f"OrderBaseInfo / OrderExecution ids do not match (${base.id} / ${execution.id}"
      )
    }
    OpenOrder(
      orderId = base.id,
      position = hypotheticalPosition(base, execution),
      order = base.order,
      state = base.state,
      execution = execution
    )
  }

  private def hypotheticalPosition(base: OrderBaseInfo, execution: OrderExecution): Position = Position(
    contract = base.contract,
    quantity = execution.filled + execution.remaining,
    averageCost = bestGuessAverageCost(base, execution)
  )

  private def bestGuessAverageCost(base: OrderBaseInfo, execution: OrderExecution): Double = {
    val avgFill = Option(execution.avgFillPrice)
    val lmtPrice = Option(base.order.lmtPrice())
    val lastFill = Option(execution.lastFillPrice)

    (avgFill, lmtPrice, lastFill) match {
      case (Some(avg), _, _)  if avg != 0 => avg
      case (_, Some(lmt), _)  if lmt != 0 => lmt
      case (_, _, Some(last)) if last != 0 => last
        // TODO: can we ever receive messages from TWS that allow all these cases to fall through?
    }
  }
}

/**
 * All information provided by the IBKR TWS API's openOrder EWrapper method.
 * Should really only be used as an intermediate value when constructing an OpenOrder.
 * @param id
 * @param contract
 * @param order
 * @param state
 */
case class OrderBaseInfo
( id: Int,
  contract: Contract,
  order: Order,
  state: OrderState )

/**
 * All information provided by the IBKR TWS API's orderStatus EWrapper method.
 * Should really only be used as an intermediate value when constructing an OpenOrder.
 * @param id
 * @param status
 * @param filled
 * @param remaining
 * @param avgFillPrice
 * @param permId
 * @param parentId
 * @param lastFillPrice
 * @param clientId
 * @param whyHeld
 * @param mktCapPrice
 */
// Corresponds to orderStatus EWrapper method
case class OrderExecution
( id: Int,
  status: OrderStatus, // enum for the status string...
  filled: Double,
  remaining: Double,
  avgFillPrice: Double,
  permId: Int, // ??? what even is this ???
  parentId: Int,
  lastFillPrice: Double,
  clientId: Int,
  whyHeld: String,
  mktCapPrice: Double)
