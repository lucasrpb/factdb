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

class Scheduler(id: String) extends Actor with ActorLogging {

  val PARTITION = "log"

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

  val READ_EPOCH = session.prepare("select * from epochs where id=?;")

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()

  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"scheduler-$id")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  //config += (ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> "104857600")
  config += (ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> "1")

  // use consumer for interacting with Apache Kafka
  var consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

  /*consumer.subscribeFuture(/*scala.collection.mutable.Set(Server.LOG_TOPICS: _*)*/"l0").onComplete {
    case Success(result) => {
      println(s"scheduler-$id subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }*/

  //consumer.subscribe(scala.collection.mutable.Set(Server.LOG_TOPICS: _*))

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

  for(i<-0 until EPOCH_TOPIC_PARTITIONS){
    positions.put(i, 0L)
  }

  def seek(): Unit = {
    val p = offset.getAndIncrement() % EPOCH_TOPIC_PARTITIONS

    val pos = positions(p)

    val tp = new io.vertx.kafka.client.common.TopicPartition().setTopic("log").setPartition(p)
    val partition = TopicPartition(tp)

    positions.put(p, pos + 1L)

    consumer = consumer.assign(partition)
    consumer = consumer.seek(partition, pos)
    //consumer.commit()
  }

  val batches = TrieMap.empty[String, Batch]
  val executing = TrieMap.empty[String, String]

  def sendWork(): Unit = synchronized {

    val available = Server.workers.filterNot(executing.isDefinedAt(_)).sorted

    if(available.isEmpty) {
      println(s"no workers available!")
      return
    }

    var list = batches.values.toSeq.sortBy(_.id)
    var partitions = Seq.empty[String]

    list = list.filter { b =>
      if(!b.partitions.exists(partitions.contains(_))){
        partitions = partitions ++ b.partitions
        true
      } else {
        false
      }
    }

    list = list.slice(0, Math.min(list.length, available.length))

    for(i<-0 until list.length){
      val b = list(i)
      val w = available(i)

      batches.remove(b.id)
      executing.put(w, b.id)

      wMap(w) ! b
    }
  }

  val epochs = TrieMap.empty[String, String]

  def handler(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    val e = Any.parseFrom(evt.value()).unpack(Epoch)

    if(epochs.isDefinedAt(e.id)){
      println(s"\n${Console.RED_B}:(${Console.RESET}\n")
      System.exit(1)
    }

    epochs.put(e.id, e.id)

    /*e.batches.foreach { b =>
      println(s"${Console.RED_B}batch ${b.id} coordinator: ${b.coordinator}${Console.RESET}\n")
      cMap(b.coordinator) ! BatchDone(b.id, Seq.empty[String], b.txs.map(_.id))
    }

    seek()
    consumer.commit()
    consumer = consumer.resume()*/

    e.batches.foreach { b =>
      batches.put(b.id, b)
    }

    sendWork()
  }

  seek()
  consumer.handler(handler)

  /*def handler(): Unit = {
    session.executeAsync(READ_EPOCH.bind.setString(0, offset.get().toString)).onComplete {
      case Success(rs) =>

        val one = rs.one()

        if(one != null){

          val e = Any.parseFrom(one.getBytes("bin").array()).unpack(Epoch)

          e.batches.foreach { b =>
            batches.put(b.id, b)
          }

          sendWork()

        } else {
          scheduler.scheduleOnce(10 millis)(handler)
        }

      case Failure(ex) => ex.printStackTrace()
    }
  }

  scheduler.scheduleOnce(10 millis)(handler)*/

  def process(cmd: BatchDone): Unit = synchronized {

    println(s"${Console.MAGENTA_B}WORKER ${cmd.id} DONE!${Console.RESET}\n")

    executing.remove(cmd.id)

    if(executing.isEmpty && batches.isEmpty){

      println(s"${Console.RED_B}NEXT EPOCH${Console.RESET}\n\n")

      seek()
      consumer.commit()
      consumer = consumer.resume()

      /*offset.incrementAndGet()
      handler()*/

      return
    }

    sendWork()
  }

  override def receive: Receive = {
    case cmd: BatchDone => process(cmd)
    case _ =>
  }
}
