package factdb

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.ddata.DistributedData
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import com.google.protobuf.any.Any
import factdb.protocol._
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success}

class Worker(val id: String, val partition: Int) extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  implicit val node = DistributedData(context.system).selfUniqueAddress
  val replicator = DistributedData(context.system).replicator



  val poolingOptions = new PoolingOptions()
    //.setConnectionsPerHost(HostDistance.LOCAL, 1, 200)
    .setMaxRequestsPerConnection(HostDistance.LOCAL, 2000)
  //.setNewConnectionThreshold(HostDistance.LOCAL, 2000)
  //.setCoreConnectionsPerHost(HostDistance.LOCAL, 2000)

  val ycluster = com.datastax.driver.core.Cluster.builder()
    .addContactPoint("127.0.0.1")
    .withPoolingOptions(poolingOptions)
    .build()

  val session = ycluster.connect("s2")

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()

  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"workers")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  // use consumer for interacting with Apache Kafka
  val consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

  consumer.subscribeFuture("batches").onComplete {
    case Success(result) => {
      println(s"worker ${id} subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }

  val rand = ThreadLocalRandom.current()
  val wMap = TrieMap[String, ActorRef]()

  Server.workers.foreach { w =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$w",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${w}")

    wMap.put(w, proxy)
  }

  val cMap = TrieMap[String, ActorRef]()

  Server.coordinators.foreach { c =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$c",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${c}")

    cMap.put(c, proxy)
  }

  val epoch = new AtomicInteger(0)
  val batch = new AtomicReference[Option[Batch]](None)

  val data = TrieMap.empty[String, TrieMap[String, Option[Batch]]]

  def handle(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    val b = Any.parseFrom(evt.value()).unpack(Batch)
    batch.set(Some(b))

  }

  consumer.handler(handle)

  override def preStart(): Unit = {
    println(s"STARTING WORKER $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING WORKER $id...\n")
  }

  override def receive: Receive = {
    case _ =>
  }
}
