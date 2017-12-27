package io.wonder.soft.mb2ahs.lib.mysql

import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener
import com.github.shyiko.mysql.binlog.event.Event

trait EventListenerTrait {

  def initialize(): Unit = {
    val client = new BinaryLogClient("0.0.0.0", 3306, "root", "password")
    client.registerEventListener(
      new EventListener {
        def onEvent(event: Event): Unit = {
          println(event.toString)
        }
      }
    )

    client.connect()
  }

}
