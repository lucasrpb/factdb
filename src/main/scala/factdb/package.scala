import akka.actor.ExtendedActorSystem
import akka.cluster.ddata.{ORMapKey, ReplicatedData}
import akka.serialization.Serializer
import com.google.protobuf.any.Any
import factdb.protocol._

package object factdb {

  case class SameMessage(msg: String) extends ReplicatedData {
    override type T = SameMessage

    override def merge(that: SameMessage): SameMessage = that
  }

}
