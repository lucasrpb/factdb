package factdb

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.ddata.{DistributedData, ORMap, ORMapKey, Replicator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.protobuf.any.Any
import factdb.protocol._
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord, KafkaConsumerRecords}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.pattern._
import akka.util.Timeout
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import io.vertx.scala.kafka.client.common.TopicPartition

import scala.concurrent.duration._

class Worker(val id: String, val partition: Int) extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

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

  val READ_BATCH = session.prepare("select completed from batches where id=?;")
  val UPDATE_BATCH = session.prepare("update batches set completed = true where id=?;")

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()

  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"worker")
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

  val batches = TrieMap.empty[String, Batch]

  val topicPartition = TopicPartition.apply(new io.vertx.kafka.client.common.TopicPartition().setPartition(partition)
    .setTopic("batches"))

  //val batch = new AtomicReference[Option[Batch]](None)

  def handle(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    /*if(!evt.partition().equals(partition)){
      consumer.commit()
      return
    }*/

    consumer.pause()

    val b = Any.parseFrom(evt.value()).unpack(Batch)

    if(!send(b)){

      consumer.seek(topicPartition, evt.offset())
      consumer.commit()
      consumer.resume()
      return
    }
  }

  def send(b: Batch): Boolean = synchronized {
    if(batches.isDefinedAt(id)){
      false
    } else {

      batches.put(id, b)

      Server.workers.filterNot(_.equals(id)).foreach { w =>
        wMap(w) ! EpochBatch(id, b)
      }

      true
    }
  }

  consumer.handler(handle)
  //consumer.batchHandler(handle)

  val sent = new AtomicBoolean(false)

  override def preStart(): Unit = {
    println(s"STARTING WORKER $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING WORKER $id...\n")
  }

  val empty = Batch(UUID.randomUUID.toString, Seq.empty[Transaction], id,
    Seq.empty[String], scala.util.Random.shuffle(Server.workers).head)

  override def receive: Receive = {
    case b: EpochBatch =>

      batches.putIfAbsent(b.w, b.b)

      val keys = batches.keys.toSeq

      send(empty)

      if(Server.workers.forall(keys.contains(_))){

        val b = batches(id)

        if(!b.txs.isEmpty){
          println(s"${Console.RED_B}[$id] txs ${b.txs.map(_.id)} batches: ${} ${Console.RESET}\n")
          cMap(b.coordinator) ! BatchDone(id, Seq.empty[String], b.txs.map(_.id))
        }

        batches.clear()
        consumer.commit()
        consumer.resume()
      }

    case _ =>
  }
}
