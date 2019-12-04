package factdb

import java.util.concurrent.atomic.AtomicInteger

import factdb.protocol._
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.datastax.driver.core.utils.Bytes
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import com.google.protobuf.any.Any
import io.vertx.core.{AsyncResult, Handler}
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.common.TopicPartition
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord, KafkaConsumerRecords}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success}

class Scheduler() extends Actor with ActorLogging {

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

  val session = ycluster.connect("s2")
  val READ_EPOCH = session.prepare("select * from bucetas where id=?;")

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()

  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"scheduler")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  //config += (ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> "104857600")
  //config += (ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> "5")

  // use consumer for interacting with Apache Kafka
  var consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

  consumer.subscribeFuture("log").onComplete {
    case Success(result) => {
      println(s"scheduler subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }

  val wMap = TrieMap[String, ActorRef]()

  Server.workers.foreach { w =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$w",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${w}")

    wMap.put(w, proxy)
  }

  val cMap = TrieMap.empty[String, ActorRef]

  Server.coordinators.foreach { c =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$c",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${c}")

    cMap.put(c, proxy)
  }

  val offset = new AtomicInteger(0)
  val positions = TrieMap.empty[Int, Long]

  (0 until Server.coordinators.length).map { i =>
    positions.put(i, 0L)
  }

  def seek(): Unit = {
    val p = offset.getAndIncrement() % Server.coordinators.length
    val pos = positions(p)

    positions.update(p, pos + 1L)

    val tp = new io.vertx.kafka.client.common.TopicPartition().setTopic("log").setPartition(p)
    val partition = TopicPartition(tp)

    consumer.seek(partition, pos)
    consumer.commit()
  }

  def handler(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    val e = Any.parseFrom(evt.value()).unpack(Epoch)

    e.batches.foreach { b =>
      cMap(b.coordinator) ! BatchDone(b.id, Seq.empty[String], b.txs.map(_.id))
    }

    seek()
    consumer.resume()
  }

  consumer.handler(handler)
  seek()

  override def receive: Receive = {
    case _ =>
  }
}
