// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package factdb.protocol

object CommandsProto extends _root_.scalapb.GeneratedFileObject {
  lazy val dependencies: Seq[_root_.scalapb.GeneratedFileObject] = Seq(
    scalapb.options.ScalapbProto
  )
  lazy val messagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] =
    Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]](
      factdb.protocol.Ack,
      factdb.protocol.Nack,
      factdb.protocol.HelloWorld,
      factdb.protocol.MVCCVersion,
      factdb.protocol.Transaction
    )
  private lazy val ProtoBytes: Array[Byte] =
      scalapb.Encoding.fromBase64(scala.collection.immutable.Seq(
  """Cg5jb21tYW5kcy5wcm90bxoVc2NhbGFwYi9zY2FsYXBiLnByb3RvIioKA0Fjazoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjd
  GRiLkNvbW1hbmQiKwoETmFjazoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmQiTQoKSGVsbG9Xb3JsZBIaCgNtc
  2cYASABKAlCCOI/BRIDbXNnUgNtc2c6I+I/IAoOZmFjdGRiLkNvbW1hbmQSDmZhY3RkYi5Db21tYW5kIoYBCgtNVkNDVmVyc2lvb
  hIUCgFrGAEgASgJQgbiPwMSAWtSAWsSFAoBdhgCIAEoBEIG4j8DEgF2UgF2EiYKB3ZlcnNpb24YAyABKAlCDOI/CRIHdmVyc2lvb
  lIHdmVyc2lvbjoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmQiwgIKC1RyYW5zYWN0aW9uEhcKAmlkGAEgASgJQ
  gfiPwQSAmlkUgJpZBIdCgRrZXlzGAIgAygJQgniPwYSBGtleXNSBGtleXMSJQoCcnMYAyADKAsyDC5NVkNDVmVyc2lvbkIH4j8EE
  gJyc1ICcnMSJQoCd3MYBCADKAsyDC5NVkNDVmVyc2lvbkIH4j8EEgJ3c1ICd3MSLwoKcGFydGl0aW9ucxgFIAMoCUIP4j8MEgpwY
  XJ0aXRpb25zUgpwYXJ0aXRpb25zEiMKBndvcmtlchgGIAEoCUIL4j8IEgZ3b3JrZXJSBndvcmtlchIyCgtjb29yZGluYXRvchgHI
  AEoCUIQ4j8NEgtjb29yZGluYXRvclILY29vcmRpbmF0b3I6I+I/IAoOZmFjdGRiLkNvbW1hbmQSDmZhY3RkYi5Db21tYW5kQhjiP
  xUKD2ZhY3RkYi5wcm90b2NvbBABWABiBnByb3RvMw=="""
      ).mkString)
  lazy val scalaDescriptor: _root_.scalapb.descriptors.FileDescriptor = {
    val scalaProto = com.google.protobuf.descriptor.FileDescriptorProto.parseFrom(ProtoBytes)
    _root_.scalapb.descriptors.FileDescriptor.buildFrom(scalaProto, dependencies.map(_.scalaDescriptor))
  }
  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val javaProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto.parseFrom(ProtoBytes)
    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(javaProto, Array(
      scalapb.options.ScalapbProto.javaDescriptor
    ))
  }
  @deprecated("Use javaDescriptor instead. In a future version this will refer to scalaDescriptor.", "ScalaPB 0.5.47")
  def descriptor: com.google.protobuf.Descriptors.FileDescriptor = javaDescriptor
}