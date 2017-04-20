package pl.edu.agh.iet.akka_tracing.utils

import java.util.UUID

import com.typesafe.config.Config
import org.json4s.JValue
import org.json4s.native.JsonMethods._
import org.slf4j.{ Logger, LoggerFactory }
import pl.edu.agh.iet.akka_tracing.model.{ ReceiverMessage, SenderMessage, MessagesRelation }

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

class DatabaseUtils(val config: Config) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  import slick.basic.DatabaseConfig
  import slick.jdbc.JdbcProfile
  import slick.jdbc.meta._

  private[akka_tracing] val dc = DatabaseConfig.forConfig[JdbcProfile]("database", config)

  import dc.profile.api._

  private[akka_tracing] implicit val jValueColumnType = MappedColumnType.base[JValue, String](
    (json: JValue) => compact(render(json)),
    (s: String) => parse(s)
  )

  class SenderMessages(tag: Tag) extends Table[SenderMessage](tag, "senderMessages") {
    def id = column[UUID]("id", O.PrimaryKey)

    def sender = column[String]("sender")

    def contents = column[Option[JValue]]("contents", O.SqlType("TEXT"))

    override def * = (id, sender, contents) <> (SenderMessage.tupled, SenderMessage.unapply)
  }

  class ReceiverMessages(tag: Tag) extends Table[ReceiverMessage](tag, "receiverMessages") {
    def id = column[UUID]("id", O.PrimaryKey)

    def receiver = column[String]("receiver")

    override def * = (id, receiver) <> (ReceiverMessage.tupled, ReceiverMessage.unapply)
  }

  class MessagesRelations(tag: Tag) extends Table[MessagesRelation](tag, "relations") {
    def id1 = column[UUID]("id1")

    def id2 = column[UUID]("id2")

    def pk = primaryKey("pk", (id1, id2))

    override def * = (id1, id2) <> (MessagesRelation.tupled, MessagesRelation.unapply)
  }

  private[akka_tracing] val db = dc.db
  private[akka_tracing] val senderMessages = TableQuery[SenderMessages]
  private[akka_tracing] val receiverMessages = TableQuery[ReceiverMessages]
  private[akka_tracing] val relations = TableQuery[MessagesRelations]

  def init(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Creating tables (if needed)...")
    db.run(MTable.getTables).flatMap(
      (tablesVector: Vector[MTable]) => {
        val tables = tablesVector.toList.map((t: MTable) => t.name.name)
        var queries = mutable.MutableList[DBIO[Any]]()
        if (!tables.contains("senderMessages")) {
          logger.info("Creating table for sender messages...")
          queries += senderMessages.schema.create
        }
        if (!tables.contains("receiverMessages")) {
          logger.info("Creating table for receiver messages...")
          queries += receiverMessages.schema.create
        }
        if (!tables.contains("relations")) {
          logger.info("Creating table for relations...")
          queries += relations.schema.create
        }
        db.run(DBIO.seq(queries: _*))
      }
    ).map[Unit](_ =>
      logger.info("Done")
    )
  }

  def clean(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Cleaning tables...")
    db.run(DBIO.seq(
      senderMessages.delete,
      receiverMessages.delete,
      relations.delete
    )).map[Unit](_ => logger.info("Done"))
  }
}
