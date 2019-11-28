package factdb

import factdb.protocol._
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.protobuf.any.Any
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord, KafkaConsumerRecords}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success}

class Scheduler() extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()


  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"scheduler")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  // use consumer for interacting with Apache Kafka
  val consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

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

  val batches = TrieMap.empty[String, Batch]
  val executing = TrieMap.empty[String, (String, Batch)]
  val ws = TrieMap.empty[String, String]

  def handler(evts: KafkaConsumerRecords[String, Array[Byte]]): Unit = {
    consumer.pause()

    ws.clear()
    Server.workers.foreach(w => ws.put(w, w))

    val bts = (0 until evts.size).map { i =>
      val rec = evts.recordAt(i)
      Any.parseFrom(rec.value()).unpack(Batch)
    }

    bts.foreach { b =>
      batches.put(b.id, b)
    }

    println(s"batches: ${batches.map(_._1)}\n")

    execute()
  }

  def execute(): Unit = synchronized {

    var partitions = executing.map(_._2._2.partitions).flatten.toSeq
    var list = batches.values.toSeq.sortBy(_.id)

    list = list.filter { b =>
      if(!b.partitions.exists(partitions.contains(_))){
        partitions = partitions ++ b.partitions
        true
      } else {
        false
      }
    }

    println(s"list length ${list.length} ${batches.size} ${ws.keySet}\n")

    val len = Math.min(ws.size, list.length)

    list = list.slice(0, len)

    /*if(list.isEmpty) {
      scheduler.scheduleOnce(10 millis)(execute)
      return
    }*/

    println(s"executing ${list.length}/${ws.size}\n")

    val available = ws.keys.toSeq

    for(i<-0 until list.length){
      val w = available(i)
      val b = list(i)

      executing.put(b.id, w -> b)
      batches.remove(b.id)
      ws.remove(w)

      wMap(w) ! b
    }
  }

  consumer.handler(_ => {})
  consumer.batchHandler(handler)

  def process(cmd: BatchFinished): Unit = synchronized {
    val (w, _) = executing.remove(cmd.id).get
    ws.put(w, w)

    if(batches.isEmpty && executing.isEmpty){

      println(s"NEXT\n")

      consumer.commit()
      consumer.resume()
      return
    }

    execute()
  }

  override def receive: Receive = {
    case cmd: BatchFinished => process(cmd)
    case _ =>
  }
}
