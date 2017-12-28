package io.wonder.soft.mb2ahs.actor

import akka.stream._
import akka.stream.stage._
import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener
import com.github.shyiko.mysql.binlog.event.{Event => BinLogEvent}
import com.typesafe.config.{Config, ConfigFactory}

case class MyEvent(value: BinLogEvent)

class BinLogSourceStage extends GraphStageWithMaterializedValue[SourceShape[MyEvent], String] {
  val config: Config = ConfigFactory.load()
  val dbDefault = config.getConfig("db.default")

  val dbHost = dbDefault.getString("host")
  val dbUser = dbDefault.getString("user")
  val dbPassword = dbDefault.getString("password")
  val dbPort = dbDefault.getInt("port")

  var client = new BinaryLogClient(dbHost, dbPort, dbUser, dbPassword)

  val out: Outlet[MyEvent] = Outlet("out")
  val shape: SourceShape[MyEvent] = SourceShape(out)

  var sourceEvent: MyEvent = _
  var preSourceEvent: MyEvent = _

  def createLogicAndMaterializedValue(in: Attributes): (GraphStageLogic, String) = {
    val logic = new GraphStageLogic(shape) {
      override def preStart(): Unit = {
        client.registerEventListener(
          new EventListener {
            def onEvent(event: BinLogEvent): Unit = {
              sourceEvent = MyEvent(event)
            }
          }
        )
        if (!client.isConnected) {
          client.connect()
        }
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (sourceEvent != null && sourceEvent.value != preSourceEvent.value) {
            preSourceEvent = sourceEvent
            push(out, sourceEvent)
          }
        }
      })
    }
    if (sourceEvent == null || sourceEvent.value == null) {
      (logic, "")

    } else {
      (logic, sourceEvent.value.toString)
    }
  }
}

class BinLogFlowStage extends GraphStageWithMaterializedValue[FlowShape[MyEvent, MyEvent], String] {

  val in: Inlet[MyEvent] = Inlet("flow.in")
  val out: Outlet[MyEvent] = Outlet("flow.out")
  val shape: FlowShape[MyEvent, MyEvent] = FlowShape(in, out)

  var event: MyEvent = _

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, String) = {
    val logic = new GraphStageLogic(shape) {
      override def preStart(): Unit = pull(in)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          // println(grab(in))
          pull(in)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          push(out, event)
        }
      })
    }

    (logic, event.value.toString)
  }
}

class BinLogSinkStage extends GraphStageWithMaterializedValue[SinkShape[MyEvent], String] {

  val in: Inlet[MyEvent] = Inlet("in")
  val shape: SinkShape[MyEvent] = SinkShape(in)

  var event: MyEvent = _

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, String) = {
    val logic = new GraphStageLogic(shape) {
      override def preStart(): Unit = pull(in)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          // println(grab(in))
          pull(in)
        }
      })
    }
    (logic, event.value.toString)
  }
}
