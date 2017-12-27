package io.wonder.soft.mb2ahs

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import io.wonder.soft.mb2ahs.actor.MySQLBinLogEventListenActor

import scala.concurrent.ExecutionContextExecutor

trait StreamProposer {

  implicit val system: ActorSystem
  implicit val executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  val binLogActor: ActorRef

  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)

  def logger: LoggingAdapter

  val routes =
    path("v1" / "status") {
      get {
        logger.info("/v1/status")

        complete("ok")
      }

    } ~ path ("v1" / "streams") {
      get {
        binLogActor ! "ok"
        complete("ok")
      }
    }

}

object StreamProposer extends App with StreamProposer {
  override implicit val system: ActorSystem  = ActorSystem("BinLogListener")
  override implicit val executor: ExecutionContextExecutor = system.dispatcher
  override implicit val materializer: Materializer = ActorMaterializer()

  override val logger = Logging(system, getClass)
  override val binLogActor: ActorRef = system.actorOf(Props(classOf[MySQLBinLogEventListenActor]), "binlog-actor")

  Http().bindAndHandle(routes, "0.0.0.0", 8082)
}