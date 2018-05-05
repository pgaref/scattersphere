package com.scattersphere.core.util

import java.util.concurrent.CompletionException

import com.scattersphere.core.util.execution.JobExecutor
import org.scalatest.{FlatSpec, Matchers}

class ExceptionJobTest extends FlatSpec with Matchers {

  class TimerJob(duration: Int) extends RunnableTask {
    override def run(): Unit = {
      Thread.sleep(duration * 1000)
      println(s"Slept $duration second(s)")
      throw new NullPointerException
    }
  }

  "Exception Jobs" should "handle an exception" in {
    val runnableTask1: RunnableTask = new TimerJob(3)
    val task1: Task = new Task("3 second task", runnableTask1)

    task1.getStatus shouldBe TaskQueued
    task1.name shouldBe "3 second task"
    task1.getDependencies.length shouldBe 0

    val job1: Job = new Job("Cancelable Job", Seq(task1))
    val jobExec: JobExecutor = new JobExecutor(job1)

    job1.tasks.length shouldBe 1
    job1.tasks(0) shouldBe task1
    job1.getStatus() shouldBe JobQueued

    val queuedJob: JobExecutor = jobExec.queue()

    queuedJob.runNonblocking()
    job1.getStatus() shouldBe JobRunning

    println("Waiting 5 seconds before submitting a cancel.")
    Thread.sleep(5000)
    job1.getStatus() match {
      case JobFailed(_) => println("Job failed expected.")
      case x => fail(s"Unexpected job state caught: $x")
    }

    assertThrows[CompletionException] {
      queuedJob.runBlocking()
    }

    task1.getStatus match {
      case TaskFailed(reason) => reason match {
        case _: NullPointerException => println(s"Expected NullPointerException caught.")
        case x => fail(s"Unexpected exception $x caught.")
      }

      case _ => fail("Expected NullPointerException.")
    }
  }

}
