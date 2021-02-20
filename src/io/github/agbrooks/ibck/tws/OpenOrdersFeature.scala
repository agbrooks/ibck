package io.github.agbrooks.ibck.tws

import com.ib.client.{Contract, Order, OrderState, OrderStatus}
import io.github.agbrooks.ibck.tws.types.{OpenOrder, OrderBaseInfo, OrderExecution}

import scala.concurrent.Future
import scala.util.Try

/**
 * Internal sum type describing all expected types of order details message
 */
sealed private trait OrderDetailsFragment { val id: Int }
private final case class BaseFragment(base: OrderBaseInfo) extends OrderDetailsFragment { val id: Int = base.id }
private final case class ExecFragment(exec: OrderExecution) extends OrderDetailsFragment { val id: Int = exec.id }

/**
 * Mixin providing the ability to request all open orders.
 */
trait OpenOrdersFeature extends BaseTWSAdapter {
  private final val dummyReqId: Int = 0
  private final val ordersCombiner: MessageCombiner[OrderDetailsFragment, Array[OpenOrder]] =
    new MessageCombiner(new DummyRequestIdGenerator(dummyReqId), parts => Try(buildOpenOrders(parts)))

  /**
   * Request and gather all open orders across all accounts and connected TWS clients.
   * @return
   */
  def getOpenOrders: Future[Array[OpenOrder]] = synchronized {
    ordersCombiner.usingRequestId(_ => clientSocket.reqAllOpenOrders())
  }

  // FIXME: SEE https://interactivebrokers.github.io/tws-api/order_submission.html#order_status
  // FIXME:
  // FIXME: "There are not guaranteed to be orderStatus callbacks for every change in order status.
  //  For example with market orders when the order is accepted and executes immediately, there commonly will not be
  //  any corresponding orderStatus callbacks. For that reason it is recommended to monitor the
  //  IBApi.EWrapper.execDetails function in addition to IBApi.EWrapper.orderStatus."
  //
  // TODO: Maybe we need to do another pass to request Execution objects from any that weren't paired...?
  //
  /**
   * Match and combine all BaseFragments / ExecFragments into OpenOrders.
   */
  private def buildOpenOrders(fragments: Iterable[OrderDetailsFragment]): Array[OpenOrder] =
    fragments
      .groupBy(_.id)
      .view.mapValues(frags => {
        val (baseParts, execParts) = frags.partition(_.isInstanceOf[BaseFragment])
        val base = baseParts.map(_.asInstanceOf[BaseFragment].base).headOption
        val exec = execParts.map(_.asInstanceOf[ExecFragment].exec).headOption
        (base, exec) match {
          case (Some(b), Some(e)) => OpenOrder.apply(b, e)
          // FIXME: This exception is not very appropriate... maybe want a 'InvalidTWSReply' or something?
          case (Some(b), _) => throw new IllegalArgumentException(f"order ${b.id} missing orderStatus message")
          case (_, Some(e)) => throw new IllegalArgumentException(f"order id ${e.id} missing openOrder message")
          case (_, _) => throw new IllegalArgumentException(f"...how did we get here") // uh
        }
      }).values.toArray

  /*
   * Overrides for the relevant portion of the IBKR EWrapper interface
   */

  override def openOrder(orderId: Int, contract: Contract, order: Order, orderState: OrderState): Unit = {
    // Just submit a 'base fragment' from the basic order info offered
    val baseInfo = OrderBaseInfo(
      id = orderId,
      contract = contract,
      order = order,
      state = orderState
    )
    ordersCombiner.submit(dummyReqId, BaseFragment(baseInfo))
  }

  override def orderStatus
  (orderId: Int,
   status: String,
   filled: Double,
   remaining: Double,
   avgFillPrice: Double,
   permId: Int,
   parentId: Int,
   lastFillPrice: Double,
   clientId: Int,
   whyHeld: String,
   mktCapPrice: Double): Unit = {
    val executionInfo = OrderExecution(
      id = orderId,
      status = OrderStatus.valueOf(status),
      filled = filled,
      remaining = remaining,
      avgFillPrice = avgFillPrice,
      permId = permId,
      parentId = parentId,
      lastFillPrice = lastFillPrice,
      clientId = clientId,
      whyHeld = whyHeld,
      mktCapPrice = mktCapPrice
    )
    // Just submit a 'exec fragment' from the execution/status details provided
    ordersCombiner.submit(dummyReqId, ExecFragment(executionInfo))
  }

  override def openOrderEnd(): Unit = {
    ordersCombiner.finish(dummyReqId)
  }
}
