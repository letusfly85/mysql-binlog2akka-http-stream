package io.wonder.soft.mb2ahs.actor

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener
import com.github.shyiko.mysql.binlog.event.{FormatDescriptionEventData, RotateEventData, Event => BinLogEvent}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}

case class MyEvent(value: BinLogEvent)

class BinLogHandleActor extends Actor {
  implicit val system: ActorSystem  = ActorSystem("BinLogHandle")
  implicit val executor: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  val config: Config = ConfigFactory.load()
  val dbDefault = config.getConfig("db.default")

  val dbHost = dbDefault.getString("host")
  val dbUser = dbDefault.getString("user")
  val dbPassword = dbDefault.getString("password")
  val dbPort = dbDefault.getInt("port")

  val client = new BinaryLogClient(dbHost, dbPort, dbUser, dbPassword)

  val wSClientActor = system.actorOf(Props[WSClientActor])

  def receive = {
    case 'init =>
      client.registerEventListener(
        new EventListener {
          def onEvent(event: BinLogEvent): Unit = {
            if (!event.getData.isInstanceOf[RotateEventData] && !event.getData.isInstanceOf[FormatDescriptionEventData]) {
              if (event.getData != null) {
                wSClientActor ! MyEvent(event)
              }
            }
          }
        }
      )
      if (!client.isConnected) {
        client.connect()
      }
  }
}


class WSClientActor extends Actor {
  implicit val system: ActorSystem  = ActorSystem("WebSocketClient")
  implicit val executor: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  def receive = {
    case myEvent: MyEvent =>
      val printSink: Sink[Message, Future[Done]] =
        Sink.foreach {
          case message: TextMessage.Strict =>
            println(message.text)
        }

      val helloSource: Source[Message, NotUsed] =
        Source.single(TextMessage(myEvent.value.getData.toString))

      // the Future[Done] is the materialized value of Sink.foreach
      // and it is completed when the stream completes
      val flow: Flow[Message, Message, Future[Done]] =
      Flow.fromSinkAndSourceMat(printSink, helloSource)(Keep.left)

      // upgradeResponse is a Future[WebSocketUpgradeResponse] that
      // completes or fails when the connection succeeds or fails
      // and closed is a Future[Done] representing the stream completion from above
      val (upgradeResponse, closed) =
      Http().singleWebSocketRequest(WebSocketRequest("ws://localhost:8082/socket.io"), flow)

      val connected = upgradeResponse.map { upgrade =>
        // just like a regular http request we can access response status which is available via upgrade.response.status
        // status code 101 (Switching Protocols) indicates that server support WebSockets
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
          Done
        } else {
          throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
        }
      }

      // in a real application you would not side effect here
      // and handle errors more carefully
      connected.onComplete(println)
      closed.foreach(_ => println("closed"))
  }
}
