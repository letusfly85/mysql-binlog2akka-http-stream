package io.wonder.soft.mb2ahs.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.stream._
import akka.stream.actor.{ActorPublisher, ActorSubscriber, OneByOneRequestStrategy, RequestStrategy}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener
import com.github.shyiko.mysql.binlog.event.{Event => BinLogEvent}

case class MyEvent(value: BinLogEvent) extends AnyVal

class BinLogSubscriber extends ActorSubscriber {
  override def preStart = context.system.eventStream.subscribe(self, classOf[MyEvent])

  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy

  def receive = {
    case event: MyEvent => println(event.value.toString)
  }
}

class BinLogPublisher extends ActorPublisher[MyEvent] {
  implicit val materializer = ActorMaterializer()

  override def preStart = {
    context.system.eventStream.subscribe(self, classOf[MyEvent])
  }

  def receive = {
    case event: BinLogEvent =>
      println(event.toString)
      context.system.eventStream.publish(event)
      // onNext(MyEvent(event))
  }
}

class MySQLBinLogEventListenActor extends GraphStage[SourceShape[BinLogEvent]] with Actor {
  val binLogStreamingActor: ActorRef = context.system.actorOf(Props(classOf[BinLogPublisher]), "streaming-actor")

  var client = new BinaryLogClient("0.0.0.0", 3306, "root", "password")

  val in: Inlet[BinLogEvent]   = Inlet("BinLogEvent.In")
  val out: Outlet[BinLogEvent] = Outlet("BinLogEvent.Out")
  override val shape: SourceShape[BinLogEvent] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          push(out, grab(in))
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          println(out.s)
        }
      })
    }

  def receive = {
    case 'close =>
      client.disconnect()


    case 'init =>
      client.registerEventListener(
        new EventListener {
          def onEvent(event: BinLogEvent): Unit = {
            binLogStreamingActor ! event
          }
        }
      )
      client.connect()
  }

}
