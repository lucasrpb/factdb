package factdb

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.protobuf.any.Any
import factdb.protocol._
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecords}
import io.vertx.scala.kafka.client.producer.{KafkaProducer, KafkaProducerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class Aggregator() extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val vertx = Vertx.vertx()
  val cconfig = scala.collection.mutable.Map[String, String]()

  cconfig += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  cconfig += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  cconfig += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  cconfig += (ConsumerConfig.GROUP_ID_CONFIG -> s"aggregator")
  cconfig += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  cconfig += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  // use consumer for interacting with Apache Kafka
  val consumer = KafkaConsumer.create[String, Array[Byte]](vertx, cconfig)

  consumer.subscribeFuture("batches").onComplete {
    case Success(result) => {
      println(s"aggregator subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }

  val pconfig = scala.collection.mutable.Map[String, String]()
  pconfig += (ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  //config += (ProducerConfig.LINGER_MS_CONFIG -> "10")
  //config += (ProducerConfig.BATCH_SIZE_CONFIG -> (1024 * 1024 * 10).toString)
  pconfig += (ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringSerializer")
  pconfig += (ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArraySerializer")
  pconfig += (ProducerConfig.ACKS_CONFIG -> "1")

  // use producer for interacting with Apache Kafka
  val producer = KafkaProducer.create[String, Array[Byte]](vertx, pconfig)

  val cMap = TrieMap[String, ActorRef]()

  Server.coordinators.foreach { c =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$c",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${c}")

    cMap.put(c, proxy)
  }

  def calculateBatch(remaining: Seq[Transaction]): Seq[Transaction] = {
    var keys = Seq.empty[String]

    remaining.filter { t =>
      if(!t.keys.exists(keys.contains(_))){
        keys = keys ++ t.keys
        true
      } else {
        false
      }
    }
  }

  def handle(evts: KafkaConsumerRecords[String, Array[Byte]]): Unit = {
    consumer.pause()

    val batches = (0 until evts.size).map { i =>
      val rec = evts.recordAt(i)
      Any.parseFrom(rec.value()).unpack(Batch)
    }

    val e = Epoch(UUID.randomUUID.toString, batches.map(_.txs).flatten)

    val buf = Any.pack(e).toByteArray
    val record = KafkaProducerRecord.create[String, Array[Byte]]("log", e.epoch, buf)

    producer.writeFuture(record).onComplete {
      _ match {
        case scala.util.Failure(ex) =>
          ex.printStackTrace()
          System.exit(1)

        case scala.util.Success(_) =>

          consumer.commit()
          consumer.resume()

      }
    }

  }

  consumer.handler(_ => {})
  consumer.batchHandler(handle)

  override def preStart(): Unit = {
    println(s"AGGREGATOR STARTED...\n")
  }

  override def postStop(): Unit = {
    println(s"AGGREGATOR STOPPED...\n")
  }

  override def receive: Receive = {
    case _ =>
  }
}
