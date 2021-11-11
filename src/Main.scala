import com.ib.client.EReader
import io.github.agbrooks.ibck.analysis.{CashSecuredCheck, MaxLossCheck, NakedCallCheck}
import io.github.agbrooks.ibck.tws.TWSAdapter

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

// This needs some work - should probably add some nice command line argument parsing, etc.
object Main {
  def main(args: Array[String]): Unit = {
    val adapter: TWSAdapter = new TWSAdapter()
    adapter.reconnect()
    val reader: EReader = new EReader(adapter.clientSocket, adapter.readerSignal)
    reader.start()
    val thr = new Thread(() => {
      while (adapter.clientSocket.isConnected) {
        adapter.readerSignal.waitForSignal()
        try {
          reader.processMsgs()
        }
      }
    })
    thr.start()

    // Might want to configure these based on CLI args or something
    // may also want to have a check that confirms that your portfolio is one that we're able to sanity-check
    // effectively
    val portfolioChecks = Seq(
      new CashSecuredCheck(80),
      new NakedCallCheck,
      new MaxLossCheck(40)
    )

    // Should probably move pretty formatting stuff out of main
    var failedChecks = 0
    val accountSummary = Await.result(adapter.getAccountSummary, 10 seconds)
    for ((acct, accountData) <- accountSummary.all) {
      val positions = Await.result(adapter.getPositions(acct), 10 seconds)
      println(f"ACCOUNT ${acct}:")
      for (check <- portfolioChecks) {
        println(s"... ${check.title().toUpperCase}")
        for (remark <- check.run(positions, accountData)) {
          val bullet = if (remark.isProblem) "[FIXME] " else "[OK]    "
          if (remark.isProblem) {
            failedChecks += 1
          }
          println(s"      $bullet ${remark.description}")
        }
      }
    }
    // ...Just messing around...
    val quote = Await.result(adapter.stockQuote("IBKR", "NASDAQ"), 15 seconds)
    println(f"IBKR: bid/ask: ${quote.bid} x ${quote.ask}")
    println(f"IBKR: size: ${quote.bidSize} x ${quote.askSize}")
    /*
    Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickReqParams
WARNING: reqId: 0, minTick: 0.01, bboExchange: 9c0001, snapshotPerms: 3
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #8>, price: 63.26, tickAttrib:
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: BidPrice, price: -1.0, tickAttrib: canAutoExecute
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickSize
WARNING: tickerId: 0 / field: BidSize / size: 0
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: AskPrice, price: -1.0, tickAttrib: canAutoExecute
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickSize
WARNING: tickerId: 0 / field: AskSize / size: 0
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickGeneric
WARNING: tickerId: 0 / field: ImpliedVol / value: 0.26821711792492064
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickSize
WARNING: tickerId: 0 / field: <Invalid enum: no field for #9> / size: 9860
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #10>, price: 79.40000153, tickAttrib:
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #11>, price: 63.08000183, tickAttrib:
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #12>, price: 80.47000122, tickAttrib:
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #13>, price: 57.02999878, tickAttrib:
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #14>, price: 80.47000122, tickAttrib:
Jun 20, 2021 11:25:42 PM io.github.agbrooks.ibck.tws.QuotesFeature tickPrice
WARNING: tickerId: 0 / field: <Invalid enum: no field for #15>, price: 39.49000168, tickAttrib:

     */
    Thread.sleep(15000)
    //System.exit(failedChecks)
  }
}
