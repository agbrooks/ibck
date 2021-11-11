package io.github.agbrooks.ibck.tws

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Awaitable, Future, blocking}

/**
 * A "Snapshot" holds the most recently-observed value of some quantity.
 * @param initialValue starting value for the snapshot (if available)
 * @param placeRequest How to place the request to get the quantity in question. Calling this function should result in
 *                     another thread calling 'replace' on this Snapshot.
 *                     The snapshot will call this function at most once. If an initialValue is defined, then this
 *                     function is never called.
 *                     This function must not block the caller indefinitely.
 * @tparam T Data contained by the snapshot.
 *         T is assumed to be immutable.
 */
class Snapshot[T](val initialValue: Option[T] = None, val placeRequest: () => Unit) {
  private val gotAnyValue: CountDownLatch = initialValue match {
    case None => new CountDownLatch(1)
    case _ => new CountDownLatch(0)
  }
  private val requested: AtomicReference[Boolean] = new AtomicReference(initialValue.isDefined)

  private val lock: ReentrantReadWriteLock = new ReentrantReadWriteLock()
  private val rlock = lock.readLock()
  private val wlock = lock.writeLock()
  private var snapshotted: Option[T] = initialValue

  /**
   * Swap out the snapshotted value with a newer one.
   * @param newVal the value to use as the latest value of the snapshot.
   */
  def replace(newVal: T): Unit = {
    try {
      wlock.lockInterruptibly()
      snapshotted = Some(newVal)
      gotAnyValue.countDown()
    } finally wlock.unlock()
  }

  /**
   * Update a snapshot with a function. If no initial value was found, one will be requested.
   * May block until the initial value is known.
   * @param modify how to update the snapshotted value
   * @return the updated value
   */
  def map(modify: T => T): T = {
    ensureRequestedAndWait()
    try {
      wlock.lockInterruptibly()
      snapshotted = snapshotted.map(modify)
      snapshotted.get
    } finally wlock.unlock()
  }

  /**
   * Get the snapshotted value.
   * If none has been observed, one will be requested and the returned future will wait until it is ready.
   * @return
   */
  def get: Future[T] = Future {
    blocking {
      ensureRequestedAndWait()
      gotAnyValue.await()
      try {
        rlock.lockInterruptibly()
        snapshotted.get
      } finally rlock.unlock()
    }
  }

  /**
   * Place a request that should result in an initial value and wait for a snapshotted value to become available.
   */
  private def ensureRequestedAndWait(): Unit = {
    requested.getAndUpdate {
      case true => true
      case false =>
        placeRequest()
        true
    }
    gotAnyValue.await()
  }
}

/**
 * Companion object allowing more convenient construction of Snapshots.
 */
object Snapshot {
  def apply[T](placeRequest: () => Unit): Snapshot[T] = new Snapshot(None, placeRequest)
  def apply[T](initialValue: T, placeRequest: () => Unit): Snapshot[T] = new Snapshot(Some(initialValue), placeRequest)
  def empty[T](): Snapshot[T] = new Snapshot(None, () => ())
}
