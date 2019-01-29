package home.util

import akka.actor.TimerScheduler

/**
  * My actor classes should extend this trait instead of Timers and use timersx instead of timers.
  * The real timers is called only when there's an environment variable named `akkaTimers`.
  */
trait TimersX extends akka.actor.Timers {
    final def timersx: TimerScheduler = if (sys.env.contains("akkaTimers")) timers else home.Main.timerScheduler
}
