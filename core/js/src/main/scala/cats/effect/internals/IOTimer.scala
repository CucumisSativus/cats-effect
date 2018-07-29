/*
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect
package internals

import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.scalajs.js

/**
 * Internal API — JavaScript specific implementation for a [[Timer]]
 * powered by `IO`.
 *
 * Deferring to JavaScript's own `setTimeout` for
 * `sleep`.
 */
private[internals] class IOTimer extends Timer[IO] {
  import IOTimer.{ScheduledTick, setTimeout, clearTimeout}

  final def clockRealTime(unit: TimeUnit): IO[Long] =
    IOClock.global.clockRealTime(unit)

  final def clockMonotonic(unit: TimeUnit): IO[Long] =
    IOClock.global.clockMonotonic(unit)

  final def sleep(timespan: FiniteDuration): IO[Unit] =
    IO.Async(new IOForkedStart[Unit] {
      def apply(conn: IOConnection, cb: Either[Throwable, Unit] => Unit): Unit = {
        val task = setTimeout(timespan.toMillis, new ScheduledTick(conn, cb))
        // On the JVM this would need a ForwardCancelable,
        // but not on top of JS as we don't have concurrency
        conn.push(() => clearTimeout(task))
      }
    })
}

/**
 * Internal API
 */
private[internals] object IOTimer {
  /**
   * Globally available implementation.
   */
  val global: Timer[IO] = new IOTimer

  private def setTimeout(delayMillis: Long, r: Runnable): js.Dynamic = {
    val lambda: js.Function = () =>
      try { r.run() }
      catch { case e: Throwable => e.printStackTrace() }

    js.Dynamic.global.setTimeout(lambda, delayMillis)
  }

  private def clearTimeout(task: js.Dynamic): Unit = {
    js.Dynamic.global.clearTimeout(task)
  }


  private final class ScheduledTick(
    conn: IOConnection,
    cb: Either[Throwable, Unit] => Unit)
    extends Runnable {

    def run() = {
      conn.pop()
      cb(Callback.rightUnit)
    }
  }
}