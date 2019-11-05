import akka.cluster.ddata.ReplicatedData
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import factdb.protocol._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}

package object factdb {

  val TIMEOUT = 500L

  val accounts = TrieMap[String, Long]()

  def computeHash(k: String): String = {
    Server.partitions((scala.util.hashing.byteswap32(k.##).abs % Server.PARTITIONS))
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

  case class SameMessage(msg: String) extends ReplicatedData {
    override type T = SameMessage
    override def merge(that: SameMessage): SameMessage = that
  }

  case class Epoch(epoch: Long, batches: Map[String, Option[Batch]]) extends ReplicatedData {
    override type T = Epoch

    override def merge(that: Epoch): Epoch = {
      if(epoch < that.epoch) return that
      if(epoch > that.epoch) return this

      Epoch(epoch, batches ++ that.batches)
    }
  }

}
