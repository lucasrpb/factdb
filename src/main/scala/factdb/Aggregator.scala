package factdb

import java.nio.ByteBuffer
import java.time.Duration
import java.util.{Arrays, Collections, Properties, UUID}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import com.google.protobuf.any.Any
import factdb.protocol._
import io.vertx.core.Handler
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord, KafkaConsumerRecords}
import io.vertx.scala.kafka.client.producer.{KafkaProducer, KafkaProducerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig

import org.apache.kafka.clients.producer._
import org.apache.kafka.clients.consumer._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class Aggregator() extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val poolingOptions = new PoolingOptions()
    //.setConnectionsPerHost(HostDistance.LOCAL, 1, 200)
    //.setMaxRequestsPerConnection(HostDistance.LOCAL, 32768)
  .setNewConnectionThreshold(HostDistance.LOCAL, 2000)
  //.setCoreConnectionsPerHost(HostDistance.LOCAL, 2000)

  val ycluster = com.datastax.driver.core.Cluster.builder()
    .addContactPoint("127.0.0.1")
    .withPoolingOptions(poolingOptions)
    .build()

  val session = ycluster.connect("s2")

  val INSERT_EPOCH = session.prepare("INSERT INTO epochs(id, bin) VALUES(?,?);")

  val vertx = Vertx.vertx()

  val cconfig = scala.collection.mutable.Map[String, String]()

  cconfig += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  cconfig += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  cconfig += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  cconfig += (ConsumerConfig.GROUP_ID_CONFIG -> s"aggregator")
  cconfig += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  cconfig += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  val consumer = KafkaConsumer.create[String, Array[Byte]](vertx, cconfig)

  consumer.subscribe("batches")

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

  def save(e: Epoch): Future[Boolean] = {
    val buf = Any.pack(e).toByteArray
    val bytes = ByteBuffer.wrap(buf)

    session.executeAsync(INSERT_EPOCH.bind
      .setString(0, offset.getAndIncrement().toString)
      .setBytes(1, bytes)).map(_.wasApplied())
  }

  def loge(e: Epoch): Future[Boolean] = {
    val buf = Any.pack(e).toByteArray
    val now = System.currentTimeMillis()

    val p = offset.get() % EPOCH_TOPIC_PARTITIONS
    val record = KafkaProducerRecord.create[String, Array[Byte]]("log", e.id, buf, now, p)

    producer.sendFuture(record).map(_ => true)
  }

  def handler(evts: KafkaConsumerRecords[String, Array[Byte]]): Unit = {
    consumer.pause()

    val batches = (0 until evts.size).map { i =>
      val rec = evts.recordAt(i)
      Any.parseFrom(rec.value()).unpack(Batch)
    }

    val e = Epoch(UUID.randomUUID.toString, batches)

    loge(e).onComplete {

      case Success(_) =>
        offset.incrementAndGet()
        consumer.commit()
        consumer.resume()

      case Failure(ex) =>
        ex.printStackTrace()
        System.exit(1)
    }
  }

  consumer.handler(_ => {})
  consumer.batchHandler(handler)

  override def receive: Receive = {
    case _ =>
  }
}
