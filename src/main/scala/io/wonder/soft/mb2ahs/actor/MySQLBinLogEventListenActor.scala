package io.wonder.soft.mb2ahs.actor

import akka.actor.Actor
import io.wonder.soft.mb2ahs.lib.mysql.EventListenerTrait

class MySQLBinLogEventListenActor extends Actor with EventListenerTrait {

  def receive = {

    case msg: String =>
      println(msg)
      initialize()

  }

}
