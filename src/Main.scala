import com.ib.client.EReader
import io.github.agbrooks.ibck.tws.TWSAdapter
import io.github.agbrooks.ibck.tws.types.AccountSummary

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

// tacky placeholder for quick-and-dirty manual tests
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

    // HACKY PROOF-OF-CONCEPT:
    // THESE SHOULD REALLY BE ABSTRACTED AWAY (AND CLEANED UP)
    val accountSummary = Await.result(adapter.getAccountSummary, 10 seconds)
    for ((acct, accountData) <- accountSummary.all) {
      println(f"Checking account ${acct}...")
      val positions = Await.result(adapter.getPositions(acct), 10 seconds)

      // Ensure puts are cash-secured
      val cashToSecureShortPuts = positions
        .filter(x => x.isPut && x.isShort)
        .map(pos => pos.contract.strike * pos.contract.multiplier().toDouble * -pos.quantity)
        .sum
      val excessCash = accountData.cash - cashToSecureShortPuts
      println(f"  Cash balance exceeding what's needed to secure short puts: ${excessCash}")

      // Ensure all calls are covered
      val needSharesToCover: Map[String, Double] = positions
        .filter(p => (p.isCall && p.isShort) || p.isStock)
        .map(p => {
          if (p.isCall) {
            (p.contract.symbol(), p.contract.multiplier.toDouble * p.quantity)
          } else {
            (p.contract.symbol(), p.quantity)
          }
        })
        .groupBy(_._1).view // Yes, this is too much stuff chained together, but this is a quick hack, we'll fix later
        .mapValues(_.map(_._2).sum[Double] * -1)
        .filter(_._2 > 0)
        .toMap
      for ((symbol, required) <- needSharesToCover) {
        println(f"  You need ${required} more shares of ${symbol} to cover your calls")
      }
      if (needSharesToCover.isEmpty) {
        println("  All short calls are covered (...whew)")
      }
    }
  }
}
