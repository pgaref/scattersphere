/**
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package com.scattersphere.core.util.execution

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}
import java.util.concurrent.locks.{Condition, ReentrantLock}

import com.typesafe.scalalogging.LazyLogging

/**
  * A light wrapper around the [[ThreadPoolExecutor]]. It allows for you to pause execution and
  * resume execution when ready. It is very handy for games that need to pause.
  *
  * (Please note, no license was specified when copied from GitHubGist, so this applies to the LICENSE-2.0
  * as outlined in the start of this code.)
  *
  * @author Matthew A. Johnston (warmwaffles)
  * @author Kenji Suenobu (KenSuenobu)
  * @param corePoolSize    The size of the pool
  * @param maximumPoolSize The maximum size of the pool
  * @param keepAliveTime   The amount of time you wish to keep a single task alive
  * @param unit            The unit of time that the keep alive time represents
  * @param workQueue       The queue that holds your tasks
  * @param startPaused     true to start in paused state, false otherwise
  * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
  * @since 0.0.1
  */
class PausableThreadPoolExecutor(val corePoolSize: Int = Runtime.getRuntime.availableProcessors(),
                                 val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 10,
                                 val keepAliveTime: Long = Long.MaxValue,
                                 val unit: TimeUnit = TimeUnit.SECONDS,
                                 val workQueue: BlockingQueue[Runnable] = new LinkedBlockingQueue[Runnable](),
                                 val startPaused: Boolean = false)
  extends ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue) with LazyLogging {

  private val lock: ReentrantLock = new ReentrantLock()
  private val condition: Condition = lock.newCondition()
  private var isPaused = startPaused

  /** Overrides the beforeExecute function, allowing for the lock (pause) functionality.
    *
    * @param thread   The thread being executed
    * @param runnable The runnable task
    * @see { @link ThreadPoolExecutor#beforeExecute(Thread, Runnable)}
    */
  override protected def beforeExecute(thread: Thread, runnable: Runnable): Unit = {
    super.beforeExecute(thread, runnable)

    lock.lock()

    try {
      while (isPaused) {
        logger.trace("Awaiting lock release.")
        condition.await
        logger.trace("Lock released.")
      }
    } catch {
      case _: InterruptedException => thread.interrupt()
    } finally {
      lock.unlock()
    }
  }

  /** Indicates whether or not this thread pool executor is paused.
    *
    * @return true if paused, false otherwise.
    */
  def paused: Boolean = isPaused

  /** Pauses execution. */
  def pause(): Unit = {
    logger.trace("Pausing thread pool.")

    lock.lock()

    try {
      isPaused = true
    } finally {
      lock.unlock()
    }
  }

  /** Resumes execution. */
  def resume(): Unit = {
    logger.trace("Resuming thread pool.")

    lock.lock()

    try {
      isPaused = false
      condition.signalAll
    } finally {
      lock.unlock()
    }
  }
}

/** Convenience PausableThreadPoolExecutor factory object. */
object PausableThreadPoolExecutor {

  /** Emits a PausableThreadPoolExecutor in the paused state. */
  def apply(): PausableThreadPoolExecutor = new PausableThreadPoolExecutor(startPaused = true)

}
