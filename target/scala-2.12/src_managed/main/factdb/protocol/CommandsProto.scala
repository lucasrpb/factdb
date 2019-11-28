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
      factdb.protocol.Transaction,
      factdb.protocol.Batch,
      factdb.protocol.Epoch,
      factdb.protocol.BatchDone,
      factdb.protocol.BatchFinished,
      factdb.protocol.BatchResponse,
      factdb.protocol.PartitionExecute,
      factdb.protocol.PartitionRelease,
      factdb.protocol.ReadRequest,
      factdb.protocol.ReadResponse
    )
  private lazy val ProtoBytes: Array[Byte] =
      scalapb.Encoding.fromBase64(scala.collection.immutable.Seq(
  """Cg5jb21tYW5kcy5wcm90bxoVc2NhbGFwYi9zY2FsYXBiLnByb3RvIioKA0Fjazoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjd
  GRiLkNvbW1hbmQiKwoETmFjazoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmQiTQoKSGVsbG9Xb3JsZBIaCgNtc
  2cYASABKAlCCOI/BRIDbXNnUgNtc2c6I+I/IAoOZmFjdGRiLkNvbW1hbmQSDmZhY3RkYi5Db21tYW5kIoYBCgtNVkNDVmVyc2lvb
  hIUCgFrGAEgASgJQgbiPwMSAWtSAWsSFAoBdhgCIAEoBEIG4j8DEgF2UgF2EiYKB3ZlcnNpb24YAyABKAlCDOI/CRIHdmVyc2lvb
  lIHdmVyc2lvbjoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmQinQIKC1RyYW5zYWN0aW9uEhcKAmlkGAEgASgJQ
  gfiPwQSAmlkUgJpZBIdCgRrZXlzGAIgAygJQgniPwYSBGtleXNSBGtleXMSJQoCcnMYAyADKAsyDC5NVkNDVmVyc2lvbkIH4j8EE
  gJyc1ICcnMSJQoCd3MYBCADKAsyDC5NVkNDVmVyc2lvbkIH4j8EEgJ3c1ICd3MSLwoKcGFydGl0aW9ucxgFIAMoCUIP4j8MEgpwY
  XJ0aXRpb25zUgpwYXJ0aXRpb25zEjIKC2Nvb3JkaW5hdG9yGAYgASgJQhDiPw0SC2Nvb3JkaW5hdG9yUgtjb29yZGluYXRvcjoj4
  j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmQi1AEKBUJhdGNoEhcKAmlkGAEgASgJQgfiPwQSAmlkUgJpZBIoCgN0e
  HMYAiADKAsyDC5UcmFuc2FjdGlvbkII4j8FEgN0eHNSA3R4cxIyCgtjb29yZGluYXRvchgDIAEoCUIQ4j8NEgtjb29yZGluYXRvc
  lILY29vcmRpbmF0b3ISLwoKcGFydGl0aW9ucxgEIAMoCUIP4j8MEgpwYXJ0aXRpb25zUgpwYXJ0aXRpb25zOiPiPyAKDmZhY3RkY
  i5Db21tYW5kEg5mYWN0ZGIuQ29tbWFuZCJ4CgVFcG9jaBIgCgVlcG9jaBgBIAEoCUIK4j8HEgVlcG9jaFIFZXBvY2gSKAoDdHhzG
  AIgAygLMgwuVHJhbnNhY3Rpb25CCOI/BRIDdHhzUgN0eHM6I+I/IAoOZmFjdGRiLkNvbW1hbmQSDmZhY3RkYi5Db21tYW5kIp8BC
  glCYXRjaERvbmUSFwoCaWQYASABKAlCB+I/BBICaWRSAmlkEiYKB2Fib3J0ZWQYAiADKAlCDOI/CRIHYWJvcnRlZFIHYWJvcnRlZ
  BIsCgljb21taXR0ZWQYAyADKAlCDuI/CxIJY29tbWl0dGVkUgljb21taXR0ZWQ6I+I/IAoOZmFjdGRiLkNvbW1hbmQSDmZhY3RkY
  i5Db21tYW5kImkKDUJhdGNoRmluaXNoZWQSFwoCaWQYASABKAlCB+I/BBICaWRSAmlkEhoKA3R4cxgCIAMoCUII4j8FEgN0eHNSA
  3R4czoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmQiZgoNQmF0Y2hSZXNwb25zZRIXCgJpZBgBIAEoCUIH4j8EE
  gJpZFICaWQSFwoCb2sYAiABKAhCB+I/BBICb2tSAm9rOiPiPyAKDmZhY3RkYi5Db21tYW5kEg5mYWN0ZGIuQ29tbWFuZCJQChBQY
  XJ0aXRpb25FeGVjdXRlEhcKAmlkGAEgASgJQgfiPwQSAmlkUgJpZDoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hb
  mQibAoQUGFydGl0aW9uUmVsZWFzZRIXCgJpZBgBIAEoCUIH4j8EEgJpZFICaWQSGgoDdHhzGAIgAygJQgjiPwUSA3R4c1IDdHhzO
  iPiPyAKDmZhY3RkYi5Db21tYW5kEg5mYWN0ZGIuQ29tbWFuZCJRCgtSZWFkUmVxdWVzdBIdCgRrZXlzGAEgAygJQgniPwYSBGtle
  XNSBGtleXM6I+I/IAoOZmFjdGRiLkNvbW1hbmQSDmZhY3RkYi5Db21tYW5kImYKDFJlYWRSZXNwb25zZRIxCgZ2YWx1ZXMYASADK
  AsyDC5NVkNDVmVyc2lvbkIL4j8IEgZ2YWx1ZXNSBnZhbHVlczoj4j8gCg5mYWN0ZGIuQ29tbWFuZBIOZmFjdGRiLkNvbW1hbmRCG
  OI/FQoPZmFjdGRiLnByb3RvY29sEAFYAGIGcHJvdG8z"""
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