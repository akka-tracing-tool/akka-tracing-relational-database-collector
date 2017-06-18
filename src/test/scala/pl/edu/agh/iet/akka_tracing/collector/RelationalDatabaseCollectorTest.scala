package pl.edu.agh.iet.akka_tracing.collector

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.json4s.DefaultFormats
import org.json4s.Extraction._
import org.scalatest.FlatSpec
import pl.edu.agh.iet.akka_tracing.model.{ MessagesRelation, ReceiverMessage, SenderMessage }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RelationalDatabaseCollectorTest extends FlatSpec {

  import RelationalDatabaseCollectorTest._

  import scala.concurrent.ExecutionContext.Implicits.global

  "A database's tables" should "contain 1 row each" in {
    val uuid = UUID.randomUUID()
    val config = ConfigFactory.load("database.conf")
    val collector = new RelationalDatabaseCollector(config)

    import collector.databaseUtils._
    import dc.profile.api._

    implicit val formats = DefaultFormats

    Await.result(init, Duration.Inf)

    collector.handleSenderMessage(SenderMessage(uuid, "sender", Some(decompose(Message(1)))))
    collector.handleReceiverMessage(ReceiverMessage(uuid, "receiver"))
    collector.handleRelationMessage(MessagesRelation(UUID.randomUUID(), UUID.randomUUID()))

    Thread.sleep(1000)

    val senderMessagesRowsCount = Await.result(db.run(senderMessages.length.result), Duration.Inf)
    val receiverMessagesRowsCount = Await.result(db.run(receiverMessages.length.result), Duration.Inf)
    val relationRowsCount = Await.result(db.run(relations.length.result), Duration.Inf)

    assert(senderMessagesRowsCount === 1)
    assert(receiverMessagesRowsCount === 1)
    assert(relationRowsCount === 1)
  }
}

object RelationalDatabaseCollectorTest {

  case class Message(x: Int)

}
