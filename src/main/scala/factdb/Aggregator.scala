package factdb

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import com.google.protobuf.any.Any
import factdb.protocol._
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecords}
import io.vertx.scala.kafka.client.producer.{KafkaProducer, KafkaProducerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Aggregator() extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val poolingOptions = new PoolingOptions()
    //.setConnectionsPerHost(HostDistance.LOCAL, 1, 200)
    .setMaxRequestsPerConnection(HostDistance.LOCAL, 32768)
  //.setNewConnectionThreshold(HostDistance.LOCAL, 2000)
  //.setCoreConnectionsPerHost(HostDistance.LOCAL, 2000)

  val ycluster = com.datastax.driver.core.Cluster.builder()
    .addContactPoint("127.0.0.1")
    .withPoolingOptions(poolingOptions)
    .build()

  val vertx = Vertx.vertx()
  val cconfig = scala.collection.mutable.Map[String, String]()

  cconfig += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  cconfig += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  cconfig += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  cconfig += (ConsumerConfig.GROUP_ID_CONFIG -> s"aggregator")
  cconfig += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  cconfig += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  //config += (ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> "104857600")
  //config += (ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> "5")

  // use consumer for interacting with Apache Kafka
  var consumer = KafkaConsumer.create[String, Array[Byte]](vertx, cconfig)

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

  val wMap = TrieMap[String, ActorRef]()

  Server.workers.foreach { w =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$w",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${w}")

    wMap.put(w, proxy)
  }

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

  var offset = new AtomicInteger(0)

  def loge(e: Epoch): Future[Boolean] = {
    val buf = Any.pack(e).toByteArray
    val now = System.currentTimeMillis()

    //val p = offset.get() % EPOCH_TOPIC_PARTITIONS
    //val record = KafkaProducerRecord.create[String, Array[Byte]]("log", e.id, buf, now, p)

    val record = KafkaProducerRecord.create[String, Array[Byte]]("log", e.id, buf)

    producer.sendFuture(record).map { m =>
      true
    }
  }

  def handler(evts: KafkaConsumerRecords[String, Array[Byte]]): Unit = {
    consumer.pause()

    val batches = (0 until evts.size).map { i =>
      val rec = evts.recordAt(i)
      Any.parseFrom(rec.value()).unpack(Batch)
    }

    val e = Epoch(UUID.randomUUID.toString, batches)

    loge(e).onComplete { case _ =>
      offset.incrementAndGet()
      consumer.commit()
      consumer.resume()
    }
  }

  consumer.handler(_ => {})
  consumer.batchHandler(handler)

  override def receive: Receive = {
    case _ =>
  }
}
