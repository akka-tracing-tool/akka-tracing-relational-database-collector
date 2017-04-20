package pl.edu.agh.iet.akka_tracing.visualization.data

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.json4s.DefaultFormats
import org.json4s.Extraction._
import org.scalatest.FlatSpec
import pl.edu.agh.iet.akka_tracing.model.{ Message, MessagesRelation, ReceiverMessage, SenderMessage }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class RelationalDatabaseDataSourceTest extends FlatSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val config = ConfigFactory.load("database2.conf")
  private val dataSource = new RelationalDatabaseDataSource(config)

  import RelationalDatabaseDataSourceTest._
  import dataSource.databaseUtils
  import databaseUtils._
  import dc.profile.api._

  private implicit val formats = DefaultFormats

  private val uuid1 = UUID.randomUUID()
  private val uuid2 = UUID.randomUUID()

  Await.result(init, Duration.Inf)
  Await.result(db.run(DBIO.seq(
    senderMessages += SenderMessage(uuid1, "testSender1", Some(decompose(TestMessage(1)))),
    senderMessages += SenderMessage(uuid2, "testSender2", Some(decompose(TestMessage(2)))),
    receiverMessages += ReceiverMessage(uuid2, "testReceiver2"),
    relations += MessagesRelation(uuid1, uuid2)
  )), Duration.Inf)

  "Database source" should "return proper relations" in {
    val relations = Await.result(dataSource.getRelations, Duration.Inf)
    assert(relations.length === 1)
    assert(relations contains MessagesRelation(uuid1, uuid2))
  }

  it should "return proper messages" in {
    val messages = Await.result(dataSource.getMessages, Duration.Inf)
    assert(messages.length === 2)
    assert(messages contains Message(uuid1, "testSender1", None, Some(decompose(TestMessage(1)))))
    assert(messages contains Message(uuid2, "testSender2", Some("testReceiver2"), Some(decompose(TestMessage(2)))))
  }
}

object RelationalDatabaseDataSourceTest {

  case class TestMessage(i: Int)

}
