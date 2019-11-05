// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package factdb.protocol

@SerialVersionUID(0L)
final case class BatchDone(
    id: _root_.scala.Predef.String = "",
    aborted: _root_.scala.Seq[_root_.scala.Predef.String] = _root_.scala.Seq.empty,
    committed: _root_.scala.Seq[_root_.scala.Predef.String] = _root_.scala.Seq.empty
    ) extends scalapb.GeneratedMessage with scalapb.Message[BatchDone] with scalapb.lenses.Updatable[BatchDone] with factdb.Command {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = id
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
      aborted.foreach { __item =>
        val __value = __item
        __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
      }
      committed.foreach { __item =>
        val __value = __item
        __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, __value)
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
      {
        val __v = id
        if (__v != "") {
          _output__.writeString(1, __v)
        }
      };
      aborted.foreach { __v =>
        val __m = __v
        _output__.writeString(2, __m)
      };
      committed.foreach { __v =>
        val __m = __v
        _output__.writeString(3, __m)
      };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): factdb.protocol.BatchDone = {
      var __id = this.id
      val __aborted = (_root_.scala.collection.immutable.Vector.newBuilder[_root_.scala.Predef.String] ++= this.aborted)
      val __committed = (_root_.scala.collection.immutable.Vector.newBuilder[_root_.scala.Predef.String] ++= this.committed)
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __id = _input__.readString()
          case 18 =>
            __aborted += _input__.readString()
          case 26 =>
            __committed += _input__.readString()
          case tag => _input__.skipField(tag)
        }
      }
      factdb.protocol.BatchDone(
          id = __id,
          aborted = __aborted.result(),
          committed = __committed.result()
      )
    }
    def withId(__v: _root_.scala.Predef.String): BatchDone = copy(id = __v)
    def clearAborted = copy(aborted = _root_.scala.Seq.empty)
    def addAborted(__vs: _root_.scala.Predef.String*): BatchDone = addAllAborted(__vs)
    def addAllAborted(__vs: Iterable[_root_.scala.Predef.String]): BatchDone = copy(aborted = aborted ++ __vs)
    def withAborted(__v: _root_.scala.Seq[_root_.scala.Predef.String]): BatchDone = copy(aborted = __v)
    def clearCommitted = copy(committed = _root_.scala.Seq.empty)
    def addCommitted(__vs: _root_.scala.Predef.String*): BatchDone = addAllCommitted(__vs)
    def addAllCommitted(__vs: Iterable[_root_.scala.Predef.String]): BatchDone = copy(committed = committed ++ __vs)
    def withCommitted(__v: _root_.scala.Seq[_root_.scala.Predef.String]): BatchDone = copy(committed = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = id
          if (__t != "") __t else null
        }
        case 2 => aborted
        case 3 => committed
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(id)
        case 2 => _root_.scalapb.descriptors.PRepeated(aborted.iterator.map(_root_.scalapb.descriptors.PString).toVector)
        case 3 => _root_.scalapb.descriptors.PRepeated(committed.iterator.map(_root_.scalapb.descriptors.PString).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = factdb.protocol.BatchDone
}

object BatchDone extends scalapb.GeneratedMessageCompanion[factdb.protocol.BatchDone] with factdb.Command {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[factdb.protocol.BatchDone] with factdb.Command = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, _root_.scala.Any]): factdb.protocol.BatchDone = {
    _root_.scala.Predef.require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    factdb.protocol.BatchDone(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), Nil).asInstanceOf[_root_.scala.Seq[_root_.scala.Predef.String]],
      __fieldsMap.getOrElse(__fields.get(2), Nil).asInstanceOf[_root_.scala.Seq[_root_.scala.Predef.String]]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[factdb.protocol.BatchDone] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      factdb.protocol.BatchDone(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Seq[_root_.scala.Predef.String]]).getOrElse(_root_.scala.Seq.empty),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Seq[_root_.scala.Predef.String]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = CommandsProto.javaDescriptor.getMessageTypes.get(6)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = CommandsProto.scalaDescriptor.messages(6)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = factdb.protocol.BatchDone(
  )
  implicit class BatchDoneLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, factdb.protocol.BatchDone]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, factdb.protocol.BatchDone](_l) {
    def id: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.id)((c_, f_) => c_.copy(id = f_))
    def aborted: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[_root_.scala.Predef.String]] = field(_.aborted)((c_, f_) => c_.copy(aborted = f_))
    def committed: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[_root_.scala.Predef.String]] = field(_.committed)((c_, f_) => c_.copy(committed = f_))
  }
  final val ID_FIELD_NUMBER = 1
  final val ABORTED_FIELD_NUMBER = 2
  final val COMMITTED_FIELD_NUMBER = 3
  def of(
    id: _root_.scala.Predef.String,
    aborted: _root_.scala.Seq[_root_.scala.Predef.String],
    committed: _root_.scala.Seq[_root_.scala.Predef.String]
  ): _root_.factdb.protocol.BatchDone = _root_.factdb.protocol.BatchDone(
    id,
    aborted,
    committed
  )
}
