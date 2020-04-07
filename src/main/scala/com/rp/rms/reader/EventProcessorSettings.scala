package com.rp.rms.reader

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config

import scala.concurrent.duration._

object EventProcessorSettings {

  def apply(system: ActorSystem[_]): EventProcessorSettings = {
    apply(system.settings.config.getConfig("event-processor"))
  }

  def apply(config: Config): EventProcessorSettings = {
    val keepAliveInterval: FiniteDuration = config.getDuration("keep-alive-interval").toMillis.millis
    val tagName: String = config.getString("tag-name")
    val parallelism: Int = 1
    EventProcessorSettings(keepAliveInterval, tagName, parallelism)
  }
}

final case class EventProcessorSettings(
                                         keepAliveInterval: FiniteDuration,
                                         tagName: String,
                                         parallelism: Int)
