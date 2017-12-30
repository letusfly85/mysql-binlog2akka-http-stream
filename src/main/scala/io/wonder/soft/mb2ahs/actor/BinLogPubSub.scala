package io.wonder.soft.mb2ahs.actor

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener
import com.github.shyiko.mysql.binlog.event.{Event => BinLogEvent}
import com.typesafe.config.{Config, ConfigFactory}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContextExecutor

class BinLogPubSub {
  implicit val system: ActorSystem  = ActorSystem("BinLogPubSub")
  implicit val executor: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  val config: Config = ConfigFactory.load()
  val dbDefault = config.getConfig("db.default")

  val dbHost = dbDefault.getString("host")
  val dbUser = dbDefault.getString("user")
  val dbPassword = dbDefault.getString("password")
  val dbPort = dbDefault.getInt("port")

  var client = new BinaryLogClient(dbHost, dbPort, dbUser, dbPassword)

  def events: Publisher[BinLogEvent] = {
    new Publisher[BinLogEvent] {
      override def subscribe(s: Subscriber[_ >: BinLogEvent]): Unit = {
        client.registerEventListener(
          new EventListener {
            def onEvent(event: BinLogEvent): Unit = {
              s.onNext(event)
            }
          }
        )
        if (!client.isConnected) {
          client.connect()
        }
      }
    }
  }

  def storage: Subscriber[BinLogEvent] = {
    new Subscriber[BinLogEvent] {
      override def onError(t: Throwable): Unit = ???

      override def onComplete(): Unit = ???

      override def onNext(t: BinLogEvent): Unit = ???

      override def onSubscribe(s: Subscription): Unit = ???
    }
  }

  val sourcePub = Flow[BinLogEvent].map(be => be)

  val runnableGraph = Source.fromPublisher(events).via(sourcePub).to(Sink.fromSubscriber(storage)).run()
}

