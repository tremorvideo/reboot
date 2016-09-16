package dispatch

import io.netty.util.{Timeout, TimerTask, Timer}

import scala.concurrent.{ExecutionContext}
import scala.concurrent.duration.Duration

import scala.util.Try

object SleepFuture {
  def apply[T](d: Duration)(todo: => T)
              (implicit timer: Timer,
               executor: ExecutionContext) = {
    val promise = scala.concurrent.Promise[T]()

    val sleepTimeout = timer.newTimeout(new TimerTask {
      def run(timeout: Timeout) {
        promise.complete(Try(todo))
      }
    }, d.length, d.unit)

    promise.future
  }
}
