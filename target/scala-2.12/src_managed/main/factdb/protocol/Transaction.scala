// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package factdb.protocol

@SerialVersionUID(0L)
final case class Transaction(
    id: _root_.scala.Predef.String = "",
    keys: _root_.scala.Seq[_root_.scala.Predef.String] = _root_.scala.Seq.empty,
    rs: _root_.scala.Seq[factdb.protocol.MVCCVersion] = _root_.scala.Seq.empty,
    ws: _root_.scala.Seq[factdb.protocol.MVCCVersion] = _root_.scala.Seq.empty,
    partitions: _root_.scala.Seq[_root_.scala.Predef.String] = _root_.scala.Seq.empty,
    worker: _root_.scala.Predef.String = "",
    coordinator: _root_.scala.Predef.String = ""
    ) extends scalapb.GeneratedMessage with scalapb.Message[Transaction] with scalapb.lenses.Updatable[Transaction] with factdb.Command {
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
      keys.foreach { __item =>
        val __value = __item
        __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
      }
      rs.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
      ws.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
      partitions.foreach { __item =>
        val __value = __item
        __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(5, __value)
      }
      
      {
        val __value = worker
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(6, __value)
        }
      };
      
      {
        val __value = coordinator
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(7, __value)
        }
      };
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
      keys.foreach { __v =>
        val __m = __v
        _output__.writeString(2, __m)
      };
      rs.foreach { __v =>
        val __m = __v
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      ws.foreach { __v =>
        val __m = __v
        _output__.writeTag(4, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      partitions.foreach { __v =>
        val __m = __v
        _output__.writeString(5, __m)
      };
      {
        val __v = worker
        if (__v != "") {
          _output__.writeString(6, __v)
        }
      };
      {
        val __v = coordinator
        if (__v != "") {
          _output__.writeString(7, __v)
        }
      };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): factdb.protocol.Transaction = {
      var __id = this.id
      val __keys = (_root_.scala.collection.immutable.Vector.newBuilder[_root_.scala.Predef.String] ++= this.keys)
      val __rs = (_root_.scala.collection.immutable.Vector.newBuilder[factdb.protocol.MVCCVersion] ++= this.rs)
      val __ws = (_root_.scala.collection.immutable.Vector.newBuilder[factdb.protocol.MVCCVersion] ++= this.ws)
      val __partitions = (_root_.scala.collection.immutable.Vector.newBuilder[_root_.scala.Predef.String] ++= this.partitions)
      var __worker = this.worker
      var __coordinator = this.coordinator
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __id = _input__.readString()
          case 18 =>
            __keys += _input__.readString()
          case 26 =>
            __rs += _root_.scalapb.LiteParser.readMessage(_input__, factdb.protocol.MVCCVersion.defaultInstance)
          case 34 =>
            __ws += _root_.scalapb.LiteParser.readMessage(_input__, factdb.protocol.MVCCVersion.defaultInstance)
          case 42 =>
            __partitions += _input__.readString()
          case 50 =>
            __worker = _input__.readString()
          case 58 =>
            __coordinator = _input__.readString()
          case tag => _input__.skipField(tag)
        }
      }
      factdb.protocol.Transaction(
          id = __id,
          keys = __keys.result(),
          rs = __rs.result(),
          ws = __ws.result(),
          partitions = __partitions.result(),
          worker = __worker,
          coordinator = __coordinator
      )
    }
    def withId(__v: _root_.scala.Predef.String): Transaction = copy(id = __v)
    def clearKeys = copy(keys = _root_.scala.Seq.empty)
    def addKeys(__vs: _root_.scala.Predef.String*): Transaction = addAllKeys(__vs)
    def addAllKeys(__vs: Iterable[_root_.scala.Predef.String]): Transaction = copy(keys = keys ++ __vs)
    def withKeys(__v: _root_.scala.Seq[_root_.scala.Predef.String]): Transaction = copy(keys = __v)
    def clearRs = copy(rs = _root_.scala.Seq.empty)
    def addRs(__vs: factdb.protocol.MVCCVersion*): Transaction = addAllRs(__vs)
    def addAllRs(__vs: Iterable[factdb.protocol.MVCCVersion]): Transaction = copy(rs = rs ++ __vs)
    def withRs(__v: _root_.scala.Seq[factdb.protocol.MVCCVersion]): Transaction = copy(rs = __v)
    def clearWs = copy(ws = _root_.scala.Seq.empty)
    def addWs(__vs: factdb.protocol.MVCCVersion*): Transaction = addAllWs(__vs)
    def addAllWs(__vs: Iterable[factdb.protocol.MVCCVersion]): Transaction = copy(ws = ws ++ __vs)
    def withWs(__v: _root_.scala.Seq[factdb.protocol.MVCCVersion]): Transaction = copy(ws = __v)
    def clearPartitions = copy(partitions = _root_.scala.Seq.empty)
    def addPartitions(__vs: _root_.scala.Predef.String*): Transaction = addAllPartitions(__vs)
    def addAllPartitions(__vs: Iterable[_root_.scala.Predef.String]): Transaction = copy(partitions = partitions ++ __vs)
    def withPartitions(__v: _root_.scala.Seq[_root_.scala.Predef.String]): Transaction = copy(partitions = __v)
    def withWorker(__v: _root_.scala.Predef.String): Transaction = copy(worker = __v)
    def withCoordinator(__v: _root_.scala.Predef.String): Transaction = copy(coordinator = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = id
          if (__t != "") __t else null
        }
        case 2 => keys
        case 3 => rs
        case 4 => ws
        case 5 => partitions
        case 6 => {
          val __t = worker
          if (__t != "") __t else null
        }
        case 7 => {
          val __t = coordinator
          if (__t != "") __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(id)
        case 2 => _root_.scalapb.descriptors.PRepeated(keys.iterator.map(_root_.scalapb.descriptors.PString).toVector)
        case 3 => _root_.scalapb.descriptors.PRepeated(rs.iterator.map(_.toPMessage).toVector)
        case 4 => _root_.scalapb.descriptors.PRepeated(ws.iterator.map(_.toPMessage).toVector)
        case 5 => _root_.scalapb.descriptors.PRepeated(partitions.iterator.map(_root_.scalapb.descriptors.PString).toVector)
        case 6 => _root_.scalapb.descriptors.PString(worker)
        case 7 => _root_.scalapb.descriptors.PString(coordinator)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = factdb.protocol.Transaction
}

object Transaction extends scalapb.GeneratedMessageCompanion[factdb.protocol.Transaction] with factdb.Command {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[factdb.protocol.Transaction] with factdb.Command = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, _root_.scala.Any]): factdb.protocol.Transaction = {
    _root_.scala.Predef.require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    factdb.protocol.Transaction(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), Nil).asInstanceOf[_root_.scala.Seq[_root_.scala.Predef.String]],
      __fieldsMap.getOrElse(__fields.get(2), Nil).asInstanceOf[_root_.scala.Seq[factdb.protocol.MVCCVersion]],
      __fieldsMap.getOrElse(__fields.get(3), Nil).asInstanceOf[_root_.scala.Seq[factdb.protocol.MVCCVersion]],
      __fieldsMap.getOrElse(__fields.get(4), Nil).asInstanceOf[_root_.scala.Seq[_root_.scala.Predef.String]],
      __fieldsMap.getOrElse(__fields.get(5), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(6), "").asInstanceOf[_root_.scala.Predef.String]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[factdb.protocol.Transaction] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      factdb.protocol.Transaction(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Seq[_root_.scala.Predef.String]]).getOrElse(_root_.scala.Seq.empty),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Seq[factdb.protocol.MVCCVersion]]).getOrElse(_root_.scala.Seq.empty),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Seq[factdb.protocol.MVCCVersion]]).getOrElse(_root_.scala.Seq.empty),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Seq[_root_.scala.Predef.String]]).getOrElse(_root_.scala.Seq.empty),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = CommandsProto.javaDescriptor.getMessageTypes.get(4)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = CommandsProto.scalaDescriptor.messages(4)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 3 => __out = factdb.protocol.MVCCVersion
      case 4 => __out = factdb.protocol.MVCCVersion
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = factdb.protocol.Transaction(
  )
  implicit class TransactionLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, factdb.protocol.Transaction]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, factdb.protocol.Transaction](_l) {
    def id: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.id)((c_, f_) => c_.copy(id = f_))
    def keys: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[_root_.scala.Predef.String]] = field(_.keys)((c_, f_) => c_.copy(keys = f_))
    def rs: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[factdb.protocol.MVCCVersion]] = field(_.rs)((c_, f_) => c_.copy(rs = f_))
    def ws: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[factdb.protocol.MVCCVersion]] = field(_.ws)((c_, f_) => c_.copy(ws = f_))
    def partitions: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[_root_.scala.Predef.String]] = field(_.partitions)((c_, f_) => c_.copy(partitions = f_))
    def worker: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.worker)((c_, f_) => c_.copy(worker = f_))
    def coordinator: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.coordinator)((c_, f_) => c_.copy(coordinator = f_))
  }
  final val ID_FIELD_NUMBER = 1
  final val KEYS_FIELD_NUMBER = 2
  final val RS_FIELD_NUMBER = 3
  final val WS_FIELD_NUMBER = 4
  final val PARTITIONS_FIELD_NUMBER = 5
  final val WORKER_FIELD_NUMBER = 6
  final val COORDINATOR_FIELD_NUMBER = 7
  def of(
    id: _root_.scala.Predef.String,
    keys: _root_.scala.Seq[_root_.scala.Predef.String],
    rs: _root_.scala.Seq[factdb.protocol.MVCCVersion],
    ws: _root_.scala.Seq[factdb.protocol.MVCCVersion],
    partitions: _root_.scala.Seq[_root_.scala.Predef.String],
    worker: _root_.scala.Predef.String,
    coordinator: _root_.scala.Predef.String
  ): _root_.factdb.protocol.Transaction = _root_.factdb.protocol.Transaction(
    id,
    keys,
    rs,
    ws,
    partitions,
    worker,
    coordinator
  )
}
