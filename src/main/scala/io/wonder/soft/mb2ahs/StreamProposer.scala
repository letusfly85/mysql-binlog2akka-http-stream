package io.wonder.soft.mb2ahs

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream._
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.wonder.soft.mb2ahs.actor._

import scala.concurrent.ExecutionContextExecutor

case class MyData(data:String)
trait StreamProposer {

  implicit val system: ActorSystem
  implicit val executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)

  def logger: LoggingAdapter

  val sourceGraph: Graph[SourceShape[MyEvent], String] = new BinLogSourceStage
  val flowGraph: Graph[FlowShape[MyEvent, MyEvent], String] = new BinLogFlowStage
  val sinkGraph: Graph[SinkShape[MyEvent], String] = new BinLogSinkStage
  val sinkEvent = Sink.foreach[MyEvent](me => me.value.toString)

  Source.fromGraph[MyEvent, String](sourceGraph)
  val greeterWebSocketService =
    Flow[Message]
      .mapConcat {
        // we match but don't actually consume the text message here,
        // rather we simply stream it back as the tail of the response
        // this means we might start sending the response even before the
        // end of the incoming message has been received
        case tm: TextMessage =>
          //val result = Source.fromGraph(sourceGraph).via(flowGraph).to(sinkGraph).run()
          val result = Source.fromGraph(sourceGraph).to(sinkEvent).run()
          TextMessage(Source.single(result.toString) ++ tm.textStream) :: Nil

        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }

  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
  val settings = CorsSettings.defaultSettings.copy(allowedOrigins = HttpOriginRange.*).withAllowedOrigins(HttpOriginRange.*)
  val routes = cors(settings) {
    path("v1" / "status") {
      get {
        logger.info("/v1/status")

        complete("ok")
      }

    } ~ path("v1" / "streams") {
      put {
        complete("ok")
      }

    } ~ path("socket.io") {
      (get | options) {
        handleWebSocketMessages(greeterWebSocketService)
      }
    }
  }

}

object StreamProposer extends App with StreamProposer {
  override implicit val system: ActorSystem  = ActorSystem("BinLogListener")
  override implicit val executor: ExecutionContextExecutor = system.dispatcher
  override implicit val materializer: Materializer = ActorMaterializer()

  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, "0.0.0.0", 8082)
}
