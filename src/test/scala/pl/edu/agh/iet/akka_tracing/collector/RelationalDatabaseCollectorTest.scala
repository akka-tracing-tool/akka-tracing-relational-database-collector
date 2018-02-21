package pl.edu.agh.iet.akka_tracing.collector

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.Extraction._
import org.scalatest.FlatSpec
import pl.edu.agh.iet.akka_tracing.model.{ MessagesRelation, ReceiverMessage, SenderMessage }
import pl.edu.agh.iet.akka_tracing.utils.DatabaseUtils

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RelationalDatabaseCollectorTest extends FlatSpec {

  import RelationalDatabaseCollectorTest._

  import scala.concurrent.ExecutionContext.Implicits.global

  "A database's tables" should "contain 1 row each" in {
    val uuid = UUID.randomUUID()
    val config = ConfigFactory.load("database.conf")
    val collectorConstructor = new RelationalDatabaseCollectorConstructor()
    val actorSystem = ActorSystem("collector_test")
    val collector = actorSystem.actorOf(collectorConstructor.propsFromConfig(config))
    val databaseUtils = new DatabaseUtils(config)

    import databaseUtils._
    import dc.profile.api._

    implicit val formats: Formats = DefaultFormats

    Await.result(init, Duration.Inf)

    collector ! SenderMessage(uuid, "sender", Some(decompose(Message(1))))
    collector ! ReceiverMessage(uuid, "receiver")
    collector ! MessagesRelation(UUID.randomUUID(), UUID.randomUUID())

    Thread.sleep(2000)

    val senderMessagesRowsCount = Await.result(db.run(senderMessages.length.result), Duration.Inf)
    val receiverMessagesRowsCount = Await.result(db.run(receiverMessages.length.result), Duration.Inf)
    val relationRowsCount = Await.result(db.run(relations.length.result), Duration.Inf)

    assert(senderMessagesRowsCount === 1)
    assert(receiverMessagesRowsCount === 1)
    assert(relationRowsCount === 1)

    Await.result(actorSystem.terminate(), Duration.Inf)
  }
}

object RelationalDatabaseCollectorTest {

  case class Message(x: Int)

}
