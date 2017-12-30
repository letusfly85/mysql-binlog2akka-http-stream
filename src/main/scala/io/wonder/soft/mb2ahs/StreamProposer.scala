package io.wonder.soft.mb2ahs

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream._
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.wonder.soft.mb2ahs.actor._

import scala.concurrent.ExecutionContextExecutor

trait StreamProposer {

  implicit val system: ActorSystem
  implicit val executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)

  def logger: LoggingAdapter

  val binLogRoom: ActorRef

  def newUser(): Flow[Message, Message, NotUsed] = {
    // new connection - new user actor
    val userActor = system.actorOf(Props(new User(binLogRoom)))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        // transform websocket message to domain message
        case TextMessage.Strict(text) => User.IncomingMessage(text)
      }.to(Sink.actorRef[User.IncomingMessage](userActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[User.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          // give the user actor a way to send messages out
          userActor ! User.Connected(outActor)
          NotUsed
        }.map(
        // transform domain message to web socket message
        (outMsg: User.OutgoingMessage) => TextMessage(outMsg.text))

    // then combine both to a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
  val settings = CorsSettings.defaultSettings.copy(allowedOrigins = HttpOriginRange.*).withAllowedOrigins(HttpOriginRange.*)
  val routes = cors(settings) {
    path("v1" / "status") {
      get {
        logger.info("/v1/status")

        complete("ok")
      }

    } ~ path("socket.io") {
      (get | options) {
        handleWebSocketMessages(newUser())
      }
    }
  }

}

object StreamProposer extends App with StreamProposer {
  override implicit val system: ActorSystem  = ActorSystem("BinLogListener")
  override implicit val executor: ExecutionContextExecutor = system.dispatcher
  override implicit val materializer: Materializer = ActorMaterializer()

  override val logger = Logging(system, getClass)

  val binLogHandleActor = system.actorOf(Props[BinLogHandleActor])
  binLogHandleActor ! 'init

  override val binLogRoom = system.actorOf(Props(new BinLogRoom), "BinLogRoom")

  Http().bindAndHandle(routes, "0.0.0.0", 8082)
}
