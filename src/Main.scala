import com.ib.client.EReader
import io.github.agbrooks.ibck.analysis.{CashSecuredCheck, NakedCallCheck}
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
      new CashSecuredCheck(90),
      new NakedCallCheck
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
    System.exit(failedChecks)
  }
}
