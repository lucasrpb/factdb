// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package factdb.protocol

@SerialVersionUID(0L)
final case class ReadResponse(
    values: _root_.scala.Seq[factdb.protocol.MVCCVersion] = _root_.scala.Seq.empty
    ) extends scalapb.GeneratedMessage with scalapb.Message[ReadResponse] with scalapb.lenses.Updatable[ReadResponse] with factdb.Command {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      values.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
      __size
    }
    final override def serializedSize: _root_.scala.Int = {
      var read = __serializedSizeCachedValue
      if (read == 0) {
        read = __computeSerializedValue()
        __serializedSizeCachedValue = read
      }
      read
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      values.foreach { __v =>
        val __m = __v
        _output__.writeTag(1, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): factdb.protocol.ReadResponse = {
      val __values = (_root_.scala.collection.immutable.Vector.newBuilder[factdb.protocol.MVCCVersion] ++= this.values)
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __values += _root_.scalapb.LiteParser.readMessage(_input__, factdb.protocol.MVCCVersion.defaultInstance)
          case tag => _input__.skipField(tag)
        }
      }
      factdb.protocol.ReadResponse(
          values = __values.result()
      )
    }
    def clearValues = copy(values = _root_.scala.Seq.empty)
    def addValues(__vs: factdb.protocol.MVCCVersion*): ReadResponse = addAllValues(__vs)
    def addAllValues(__vs: Iterable[factdb.protocol.MVCCVersion]): ReadResponse = copy(values = values ++ __vs)
    def withValues(__v: _root_.scala.Seq[factdb.protocol.MVCCVersion]): ReadResponse = copy(values = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => values
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PRepeated(values.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = factdb.protocol.ReadResponse
}

object ReadResponse extends scalapb.GeneratedMessageCompanion[factdb.protocol.ReadResponse] with factdb.Command {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[factdb.protocol.ReadResponse] with factdb.Command = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, _root_.scala.Any]): factdb.protocol.ReadResponse = {
    _root_.scala.Predef.require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    factdb.protocol.ReadResponse(
      __fieldsMap.getOrElse(__fields.get(0), Nil).asInstanceOf[_root_.scala.Seq[factdb.protocol.MVCCVersion]]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[factdb.protocol.ReadResponse] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      factdb.protocol.ReadResponse(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Seq[factdb.protocol.MVCCVersion]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = CommandsProto.javaDescriptor.getMessageTypes.get(11)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = CommandsProto.scalaDescriptor.messages(11)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = factdb.protocol.MVCCVersion
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = factdb.protocol.ReadResponse(
  )
  implicit class ReadResponseLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, factdb.protocol.ReadResponse]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, factdb.protocol.ReadResponse](_l) {
    def values: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[factdb.protocol.MVCCVersion]] = field(_.values)((c_, f_) => c_.copy(values = f_))
  }
  final val VALUES_FIELD_NUMBER = 1
  def of(
    values: _root_.scala.Seq[factdb.protocol.MVCCVersion]
  ): _root_.factdb.protocol.ReadResponse = _root_.factdb.protocol.ReadResponse(
    values
  )
}
