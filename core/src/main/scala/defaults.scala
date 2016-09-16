package dispatch

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.{Timer, HashedWheelTimer}
import org.asynchttpclient.{DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}
import java.util.{concurrent => juc}

object Defaults {
  implicit def executor = scala.concurrent.ExecutionContext.Implicits.global
  implicit lazy val timer: Timer = InternalDefaults.timer
}

private [dispatch] object InternalDefaults {
  /** true if we think we're runing un-forked in an sbt-interactive session */
  val inSbt = (
    for (group <- Option(Thread.currentThread.getThreadGroup))
    yield (
      group.getName == "trap.exit" // sbt version <= 0.13.0
      || group.getName.startsWith("run-main-group") // sbt 0.13.1+
    )
  ).getOrElse(false)

  private lazy val underlying = 
    if (inSbt) SbtProcessDefaults
    else BasicDefaults

  def client = new DefaultAsyncHttpClient(underlying.builder.build())
  lazy val timer = underlying.timer

  private trait Defaults {
    def builder: DefaultAsyncHttpClientConfig.Builder
    def timer: Timer
  }

  /** Sets a user agent, no timeout for requests  */
  private object BasicDefaults extends Defaults {
    lazy val timer = new HashedWheelTimer()
    def builder = new DefaultAsyncHttpClientConfig.Builder()
      .setUserAgent("Dispatch/%s" format BuildInfo.version)
      .setRequestTimeout(-1) // don't timeout streaming connections
  }

  /** Uses daemon threads and tries to exit cleanly when running in sbt  */
  private object SbtProcessDefaults extends Defaults {
    def builder = {
      val shuttingDown = new juc.atomic.AtomicBoolean(false)

      val workerCount = 2 * Runtime.getRuntime().availableProcessors()
      lazy val eventLoopGroup = new NioEventLoopGroup(
        workerCount
//        juc.Executors.newCachedThreadPool(interruptThreadFactory)
      )

      def shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
          eventLoopGroup.shutdownGracefully()
          timer.stop()
        }
      }

      /** daemon threads that also shut down everything when interrupted! */
      lazy val interruptThreadFactory = new juc.ThreadFactory {
        def newThread(runnable: Runnable) = {
          new Thread(runnable) {
            setDaemon(true)
            /** only reliably called on any thread if all spawned threads are daemon */
            override def interrupt() {
              shutdown()
              super.interrupt()
            }
          }
        }
      }

      val nioClientSocketChannelFactory = {
        val b = new ServerBootstrap()
        b.group(eventLoopGroup)
      }

      BasicDefaults.builder
        .setNettyTimer(timer)
        .setEventLoopGroup(eventLoopGroup)
    }
    lazy val timer = new HashedWheelTimer(DaemonThreads.factory)


  }
}

object DaemonThreads {
  /** produces daemon threads that won't block JVM shutdown */
  val factory = new juc.ThreadFactory {
    def newThread(runnable: Runnable): Thread ={
      val thread = new Thread(runnable)
      thread.setDaemon(true)
      thread
    }
  }
  def apply(threadPoolSize: Int) =
    juc.Executors.newFixedThreadPool(threadPoolSize, factory)
}
