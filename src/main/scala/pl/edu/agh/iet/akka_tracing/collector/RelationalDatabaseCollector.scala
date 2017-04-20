package pl.edu.agh.iet.akka_tracing.collector

import java.util.concurrent.{ Executors, TimeUnit }

import com.typesafe.config.Config
import pl.edu.agh.iet.akka_tracing.model.{ Message, MessagesRelation, ReceiverMessage, SenderMessage }
import pl.edu.agh.iet.akka_tracing.utils.DatabaseUtils

import scala.collection.mutable
import scala.concurrent.ExecutionContext

final class RelationalDatabaseCollector(config: Config)
                                       (implicit val ec: ExecutionContext)
  extends Collector {

  import Collector._

  private[akka_tracing] val databaseUtils = new DatabaseUtils(config)

  import databaseUtils._
  import dc.profile.api._

  private val queue = mutable.MutableList[DBIO[Any]]()

  private val threadPool = Executors.newScheduledThreadPool(1)
  threadPool.scheduleAtFixedRate(new Runnable {
    override def run(): Unit = {
      queue.synchronized {
        val actions = queue.toList
        queue.clear()
        db.run(DBIO.seq(actions: _*))
      }
    }
  }, 500, 500, TimeUnit.MILLISECONDS)

  override def handleSenderMessage(msg: CollectorSenderMessage): Unit = {
    queue.synchronized {
      queue += (senderMessages += SenderMessage(msg.id, msg.sender, msg.message))
    }
  }

  override def handleReceiverMessage(msg: CollectorReceiverMessage): Unit = {
    queue.synchronized {
      queue += (receiverMessages += ReceiverMessage(msg.id, msg.receiver))
    }
  }

  override def handleRelationMessage(msg: RelationMessage): Unit = {
    queue.synchronized {
      queue += (relations += MessagesRelation(msg.id1, msg.id2))
    }
  }
}

final class RelationalDatabaseCollectorConstructor extends CollectorConstructor {
  override def fromConfig(config: Config)(implicit ec: ExecutionContext): Collector =
    new RelationalDatabaseCollector(config)
}
