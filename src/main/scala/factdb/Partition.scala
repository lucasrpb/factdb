package factdb

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef}
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

  consumer.subscribeFuture("batches").onComplete {
    case Success(result) => {
      println(s"partition ${id} subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }

  val replicator: ActorRef = DistributedData(context.system).replicator
  implicit val node = DistributedData(context.system).selfUniqueAddress

  val Key = ORMapKey.create[String, Epoch]("epoch")

  val sent = new AtomicBoolean(false)
  var epoch: Long = 0L
  var batch: Option[Batch] = None

  replicator ! Replicator.Subscribe(Key, self)

  def handle(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    if(evt.partition() != partition){
      consumer.commit()
      return
    }

    consumer.pause()

    val b = Any.parseFrom(evt.value()).unpack(Batch)
    batch = Some(b)

    if(sent.compareAndSet(false, true)){
      replicator ! Replicator.Update(Key, ORMap.empty[String, Epoch], Replicator.WriteLocal){
        _.put(node, "epoch", Epoch(epoch, Map(id -> batch)))
      }
    }

    println(s"partition ${id} processing batch ${b.id}\n")
  }

  consumer.handler(handle)

  val rand = ThreadLocalRandom.current()
  //val tasks = TrieMap[String, (Transaction, String)]()
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

  def process(batches: Seq[Batch]): Unit = {

    val list = batches.map(_.txs.filter(_.partitions.contains(id))).flatten.sortBy(_.id)

    if(list.isEmpty) {

      epoch += 1L
      batch = None
      sent.set(false)

      consumer.commit()
      consumer.resume()

      return
    }

    var keys = executing.map(_._2.keys).flatten.toSeq
    var abort = Seq.empty[Transaction]
    var commit = Seq.empty[Transaction]

    list.foreach { t =>
      val tkeys = t.keys.filter(computeHash(_).equals(id))

      if(!tkeys.exists(keys.contains(_))){
        keys = keys ++ t.keys
        commit = commit :+ t
        executing.put(t.id, t)
      } else {
        abort = abort :+ t
      }
    }

    var requests = Map.empty[String, PartitionExecute]

    println(s"sending ${commit.map(_.id)}\n")

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

    if(executing.isEmpty){
      epoch += 1L
      batch = None
      sent.set(false)

      consumer.commit()
      consumer.resume()
    }

    sender ! true
  }

  override def preStart(): Unit = {
    println(s"STARTING PARTITION $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING PARTITION $id...\n")
  }

  override def receive: Receive = {

    case cmd: PartitionRelease => process(cmd)

    case change @ Replicator.Changed(Key) =>
      val data = change.get(Key)

      val e = data.get("epoch")

      if(e.isDefined && e.get.epoch == epoch && Server.partitions.forall(e.get.batches.contains(_))) {
        val batches = e.get.batches.values.flatten.toSeq

        println(s"${Console.RED_B}all batches seen by partition ${id} epoch = ${e.get.epoch}: " +
          s"${batches.map(_.id)}${Console.RESET}\n")

        /*scheduler.scheduleOnce(rand.nextInt(0, 10) milliseconds){
          epoch += 1L
          batch = None
          sent.set(false)

          consumer.commit()
          consumer.resume()
        }*/

        process(batches)

      } else {

        if(sent.compareAndSet(false, true)){
          replicator ! Replicator.Update(Key, ORMap.empty[String, Epoch], Replicator.WriteLocal){
            _.put(node, "epoch", Epoch(epoch, Map(id -> batch)))
          }
        }

      }

    case _ =>
  }
}
