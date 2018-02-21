package pl.edu.agh.iet.akka_tracing.collector

import akka.actor.Props
import com.typesafe.config.Config
import pl.edu.agh.iet.akka_tracing.model.{ MessagesRelation, ReceiverMessage, SenderMessage }
import pl.edu.agh.iet.akka_tracing.utils.DatabaseUtils

import scala.collection.mutable

final class RelationalDatabaseCollector(config: Config) extends Collector {

  import RelationalDatabaseCollector.SendToDatabase
  import pl.edu.agh.iet.akka_tracing.config.ConfigUtils._

  import scala.concurrent.duration._

  private[akka_tracing] val databaseUtils = new DatabaseUtils(config)

  import databaseUtils._
  import dc.profile.api._

  private val queue = mutable.MutableList[DBIO[Any]]()
  private val sendToDatabaseInterval = config.getOrElse[Int]("sendToDatabaseInterval.millis", 1000).millis

  context.system.scheduler.schedule(sendToDatabaseInterval, sendToDatabaseInterval, self, SendToDatabase)

  override def handleSenderMessage(msg: SenderMessage): Unit = {
    queue += (senderMessages += msg)
  }

  override def handleReceiverMessage(msg: ReceiverMessage): Unit = {
    queue += (receiverMessages += msg)
  }

  override def handleRelationMessage(msg: MessagesRelation): Unit = {
    queue += (relations += msg)
  }

  override def handleOtherMessages: PartialFunction[Any, Unit] = {
    case SendToDatabase =>
      val actions = queue.toList
      queue.clear()
      db.run(DBIO.seq(actions: _*))
  }
}

object RelationalDatabaseCollector {

  case object SendToDatabase

}

final class RelationalDatabaseCollectorConstructor extends CollectorConstructor {
  override def propsFromConfig(config: Config): Props =
    Props(classOf[RelationalDatabaseCollector], config)
}
