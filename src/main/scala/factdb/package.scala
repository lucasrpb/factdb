import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}

import akka.cluster.ddata.ReplicatedData
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import factdb.protocol._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}

package object factdb {

  val EVENTS = new ConcurrentLinkedDeque[String]()

  val TIMEOUT = 1000L

  val PARTITIONS = 1000

  val ITERATIONS = 1000

  val EPOCH_TOPIC_PARTITIONS = 3

  val accounts = TrieMap.empty[String, Long]

  def computePartition(k: String): String = {
    (scala.util.hashing.byteswap32(k.##).abs % PARTITIONS).toString
  }

  def computeWorker(p: String): String = {
    Server.workers((scala.util.hashing.byteswap32(p.##).abs % Server.workers.length))
  }

  implicit def rsfToScalaFuture[T](rsf: ListenableFuture[T])(implicit ec: ExecutionContext): Future[T] = {
    val p = Promise[T]()

    Futures.addCallback(rsf, new FutureCallback[T] {
      override def onSuccess(result: T): Unit = {
        p.success(result)
      }

      override def onFailure(t: Throwable): Unit = {
        p.failure(t)
      }
    }, ec.asInstanceOf[java.util.concurrent.Executor])

    p.future
  }

  case class Collect(id: String, epoch: String) extends Command
  case class CollectResponse(id: String, epoch: String, b: Option[Batch]) extends Command

  /*case class Executed(epoch: Long, partitions: Seq[String]) extends ReplicatedData {
    override type T = Executed

    override def merge(that: Executed): Executed = {
      if(epoch < that.epoch) return that
      if(epoch > that.epoch) return this

      Executed(epoch, partitions ++ that.partitions)
    }
  }

  case class Epoch(epoch: Long, batches: Map[String, Option[Batch]]) extends ReplicatedData {
    override type T = Epoch

    override def merge(that: Epoch): Epoch = {
      if(epoch < that.epoch) return that
      if(epoch > that.epoch) return this

      Epoch(epoch, batches ++ that.batches)
    }
  }
*/
}
