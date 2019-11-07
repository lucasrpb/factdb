// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package factdb.protocol

/** @param txs
  *  repeated Batch batches = 2;
  */
@SerialVersionUID(0L)
final case class Epoch(
    epoch: _root_.scala.Predef.String = "",
    txs: _root_.scala.Seq[factdb.protocol.Transaction] = _root_.scala.Seq.empty
    ) extends scalapb.GeneratedMessage with scalapb.Message[Epoch] with scalapb.lenses.Updatable[Epoch] with factdb.Command {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = epoch
        if (__value != "") {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
      txs.foreach { __item =>
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
      {
        val __v = epoch
        if (__v != "") {
          _output__.writeString(1, __v)
        }
      };
      txs.foreach { __v =>
        val __m = __v
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): factdb.protocol.Epoch = {
      var __epoch = this.epoch
      val __txs = (_root_.scala.collection.immutable.Vector.newBuilder[factdb.protocol.Transaction] ++= this.txs)
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __epoch = _input__.readString()
          case 18 =>
            __txs += _root_.scalapb.LiteParser.readMessage(_input__, factdb.protocol.Transaction.defaultInstance)
          case tag => _input__.skipField(tag)
        }
      }
      factdb.protocol.Epoch(
          epoch = __epoch,
          txs = __txs.result()
      )
    }
    def withEpoch(__v: _root_.scala.Predef.String): Epoch = copy(epoch = __v)
    def clearTxs = copy(txs = _root_.scala.Seq.empty)
    def addTxs(__vs: factdb.protocol.Transaction*): Epoch = addAllTxs(__vs)
    def addAllTxs(__vs: Iterable[factdb.protocol.Transaction]): Epoch = copy(txs = txs ++ __vs)
    def withTxs(__v: _root_.scala.Seq[factdb.protocol.Transaction]): Epoch = copy(txs = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = epoch
          if (__t != "") __t else null
        }
        case 2 => txs
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(epoch)
        case 2 => _root_.scalapb.descriptors.PRepeated(txs.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = factdb.protocol.Epoch
}

object Epoch extends scalapb.GeneratedMessageCompanion[factdb.protocol.Epoch] with factdb.Command {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[factdb.protocol.Epoch] with factdb.Command = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, _root_.scala.Any]): factdb.protocol.Epoch = {
    _root_.scala.Predef.require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    factdb.protocol.Epoch(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), Nil).asInstanceOf[_root_.scala.Seq[factdb.protocol.Transaction]]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[factdb.protocol.Epoch] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      factdb.protocol.Epoch(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Seq[factdb.protocol.Transaction]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = CommandsProto.javaDescriptor.getMessageTypes.get(6)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = CommandsProto.scalaDescriptor.messages(6)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 2 => __out = factdb.protocol.Transaction
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = factdb.protocol.Epoch(
  )
  implicit class EpochLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, factdb.protocol.Epoch]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, factdb.protocol.Epoch](_l) {
    def epoch: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.epoch)((c_, f_) => c_.copy(epoch = f_))
    def txs: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[factdb.protocol.Transaction]] = field(_.txs)((c_, f_) => c_.copy(txs = f_))
  }
  final val EPOCH_FIELD_NUMBER = 1
  final val TXS_FIELD_NUMBER = 2
  def of(
    epoch: _root_.scala.Predef.String,
    txs: _root_.scala.Seq[factdb.protocol.Transaction]
  ): _root_.factdb.protocol.Epoch = _root_.factdb.protocol.Epoch(
    epoch,
    txs
  )
}
