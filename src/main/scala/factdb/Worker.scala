package factdb

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
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.pattern._
import akka.util.Timeout
import com.datastax.driver.core.{HostDistance, PoolingOptions}

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
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"worker-$id")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  // use consumer for interacting with Apache Kafka
  val consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

  consumer.subscribeFuture("log").onComplete {
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

  val batch = new AtomicReference[Batch](null)
  //val finished = TrieMap[String, String]()

  def handle(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    val b = Any.parseFrom(evt.value()).unpack(Batch)

    if(!b.workers.contains(id)){
      consumer.commit()
      consumer.resume()
      return
    }

    batch.set(b)

    /*if(b.worker.equals(id)){
      process()
      return
    }

    log.info(s"${Console.GREEN_B}worker ${id} waiting for batch ${b.id} to finish${Console.RESET}\n")

    checkFinished()*/

    if(b.worker.equals(id)){
      process()
      return
    }

    checkFinished()
  }

  consumer.handler(handle)

  def checkFinished(): Unit = synchronized {
    val b = batch.get()

    if(b == null) return

    session.executeAsync(READ_BATCH.bind.setString(0, b.id)).map { r =>
      val one = r.one()

      println(s"result for batch ${b.id} completed ${one.getBool("completed")}...\n")

      if(one.getBool("completed")) {
        batch.set(null)
        consumer.commit()
        consumer.resume()
      } else {
        scheduler.scheduleOnce(10 millis)(checkFinished)
      }
    }.recover { case ex =>
      ex.printStackTrace()
    }
  }

  def process(): Unit = {
    val b = batch.get()

    log.info(s"${Console.RED_B}worker ${id} processing batch ${b.id}${Console.RESET}\n")

    session.executeAsync(UPDATE_BATCH.bind.setString(0, b.id)).map { r =>

      cMap(b.coordinator) ! BatchDone(id, Seq.empty[String], b.txs.map(_.id))

      b.workers.filterNot(_.equals(id)).foreach { w =>
        wMap(w) ! BatchFinished(b.id)
      }

      consumer.commit()
      consumer.resume()

    }.recover { case ex =>
      ex.printStackTrace()
    }
  }

  override def preStart(): Unit = {
    println(s"STARTING WORKER $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING WORKER $id...\n")
  }

  def process(cmd: BatchFinished): Unit = {
    val b = batch.get()

    if(b != null && cmd.id.equals(b.id)){
      consumer.commit()
      consumer.resume()
    }
  }

  override def receive: Receive = {
    case cmd: BatchFinished => process(cmd)
    case _ =>
  }
}
