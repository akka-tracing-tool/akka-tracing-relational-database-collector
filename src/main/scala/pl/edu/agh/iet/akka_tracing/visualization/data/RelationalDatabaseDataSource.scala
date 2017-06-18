package pl.edu.agh.iet.akka_tracing.visualization.data

import com.typesafe.config.Config
import pl.edu.agh.iet.akka_tracing.model.{ Message, MessagesRelation }
import pl.edu.agh.iet.akka_tracing.utils.DatabaseUtils

import scala.concurrent.{ ExecutionContext, Future }

class RelationalDatabaseDataSource(config: Config)
                                  (implicit val ec: ExecutionContext)
  extends DataSource {

  private[akka_tracing] val databaseUtils = new DatabaseUtils(config)

  import databaseUtils._
  import dc.profile.api._

  override def onStart: Future[Unit] = databaseUtils.init

  override def getMessages: Future[List[Message]] = {
    db.run(
      (for {
        (senderMessage, receiverMessage) <- senderMessages joinLeft receiverMessages on (_.id === _.id)
      } yield (
        senderMessage.id,
        senderMessage.sender,
        receiverMessage.map(_.receiver),
        senderMessage.contents
      )).to[List].result
    ).map(
      _.map {
        case (id, sender, receiver, contents) => Message(id, sender, receiver, contents)
      }
    )
  }

  override def getRelations: Future[List[MessagesRelation]] = db.run(relations.to[List].result)
}

class RelationalDatabaseDataSourceConstructor extends DataSourceConstructor {
  override def fromConfig(config: Config)(implicit ec: ExecutionContext): DataSource = {
    new RelationalDatabaseDataSource(config)
  }
}
