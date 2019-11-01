package factdb

import akka.serialization.Serializer
import com.google.protobuf.any.Any
import factdb.protocol._

class FactDBSerializer extends Serializer {

  // If you need logging here, introduce a constructor that takes an ExtendedActorSystem.
  // class MyOwnSerializer(actorSystem: ExtendedActorSystem) extends Serializer
  // Get a logger using:
  // private val logger = Logging(actorSystem, this)

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = true

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 40 is reserved by Akka itself
  def identifier = 1234567

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(msg: AnyRef): Array[Byte] = {
    msg match {
      case msg: Ack => Any.pack(msg).toByteArray
      case msg: Nack => Any.pack(msg).toByteArray
      case msg: HelloWorld => Any.pack(msg).toByteArray
    }
  }

  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    val p = Any.parseFrom(bytes)

    p match {
      case _ if p.is(Ack) => p.unpack(Ack)
      case _ if p.is(Nack) => p.unpack(Nack)
      case _ if p.is(HelloWorld) => p.unpack(HelloWorld)
    }
  }
}

