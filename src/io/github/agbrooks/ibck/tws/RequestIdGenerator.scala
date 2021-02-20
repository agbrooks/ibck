package io.github.agbrooks.ibck.tws

import java.util.concurrent.atomic.AtomicReference

class RequestIdGenerator {
  private val lastId: AtomicReference[Int] = new AtomicReference(0)
  def nextRequestId(): Int = lastId.getAndUpdate(x => x + 1)
}
