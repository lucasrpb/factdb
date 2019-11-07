package factdb

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.ddata.{DistributedData, ORMap, ORMapKey, Replicator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.protobuf.any.Any
import factdb.protocol._
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.duration._

class Partition(val id: String, val partition: Int) extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()

  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"partition_${id}")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  // use consumer for interacting with Apache Kafka
  val consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

  consumer.subscribeFuture("log").onComplete {
    case Success(result) => {
      println(s"partition ${id} subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }

  def handle(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    /*if(evt.partition() != partition){
      consumer.commit()
      return
    }*/

    consumer.pause()
    val e = Any.parseFrom(evt.value()).unpack(Epoch)

    e.txs.foreach { t =>
      transactions.put(t.id, t)
    }

    process()
  }

  consumer.handler(handle)

  val rand = ThreadLocalRandom.current()
  val executing = TrieMap[String, Transaction]()

  val wMap = TrieMap[String, ActorRef]()

  Server.workers.foreach { w =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$w",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${w}")

    wMap.put(w, proxy)
  }

  val pMap = TrieMap[String, ActorRef]()

  Server.partitions.foreach { p =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$p",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${p}")

    pMap.put(p, proxy)
  }

  val transactions = TrieMap[String, Transaction]()

  def process(): Unit = {

    val list = transactions.toSeq.map(_._2).sortBy(_.id).filter(_.partitions.contains(id))

    if(list.isEmpty) {

      transactions.clear()
      consumer.commit()
      consumer.resume()

      return
    }

    var keys = executing.map(_._2.keys).flatten.toSeq
    var abort = Seq.empty[Transaction]
    var commit = Seq.empty[Transaction]

    list.foreach { t =>
      val tkeys = t.keys//.filter(computeHash(_).equals(id))

      if (!tkeys.exists(keys.contains(_))) {
        keys = keys ++ t.keys
        commit = commit :+ t
        executing.put(t.id, t)
        transactions.remove(t.id)
      } /*else {
        abort = abort :+ t
      }*/
    }

    println(s"${Console.RED_B}partition ${id} processing txs: ${commit.map(_.id)}...${Console.RESET}\n")

    var requests = Map.empty[String, PartitionExecute]

    abort.foreach { t =>
      requests.get(t.worker) match {
        case None => requests = requests + (t.worker -> PartitionExecute(id, Seq.empty[Transaction], Seq(t)))
        case Some(r) => requests = requests + (t.worker -> PartitionExecute(id, r.acks, r.nacks :+ t))
      }
    }

    commit.foreach { t =>
      requests.get(t.worker) match {
        case None => requests = requests + (t.worker -> PartitionExecute(id, Seq(t)))
        case Some(r) => requests = requests + (t.worker -> PartitionExecute(id, r.acks :+ t, r.nacks))
      }
    }

    requests.foreach { case (w, r) =>
      wMap(w) ! r
    }
  }

  def process(cmd: PartitionRelease): Unit = {
    cmd.txs.foreach { t =>
      executing.remove(t)
    }

    if(transactions.isEmpty){
      consumer.commit()
      consumer.resume()
      return
    }

    process()
  }

  override def preStart(): Unit = {
    println(s"STARTING PARTITION $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING PARTITION $id...\n")
  }

  override def receive: Receive = {
    case cmd: PartitionRelease => process(cmd)
    case _ =>
  }
}
