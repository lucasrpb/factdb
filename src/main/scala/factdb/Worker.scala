package factdb

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.datastax.driver.core.{BatchStatement, HostDistance, PoolingOptions}

import scala.collection.concurrent.TrieMap
import factdb.protocol._
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.any.Any
import io.vertx.core.{AsyncResult, Handler}
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.common.TopicPartition
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

class Worker(val id: String) extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val scheduler = context.system.scheduler

  val vertx = Vertx.vertx()
  val config = scala.collection.mutable.Map[String, String]()

  config += (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  config += (ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringDeserializer")
  config += (ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArrayDeserializer")
  //config += (ConsumerConfig.GROUP_ID_CONFIG -> s"worker-$id")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "none")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")
  config += (ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> "1")

  // use consumer for interacting with Apache Kafka
  var consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

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

  val poolingOptions = new PoolingOptions()
    //.setConnectionsPerHost(HostDistance.LOCAL, 1, 200)
    //.setMaxRequestsPerConnection(HostDistance.LOCAL, 2000)
  .setNewConnectionThreshold(HostDistance.LOCAL, 2000)
  //.setCoreConnectionsPerHost(HostDistance.LOCAL, 2000)

  val ycluster = com.datastax.driver.core.Cluster.builder()
    .addContactPoint("127.0.0.1")
    .withPoolingOptions(poolingOptions)
    .build()

  val session = ycluster.connect("s2")

  val READ_BATCH = session.prepare("select completed from batches where id=?;")
  val UPDATE_BATCH = session.prepare("update batches set completed=true where id=?;")

  val UPDATE_DATA = session.prepare("update data set value=?, version=? where key=?;")
  val READ_DATA = session.prepare("select * from data where key=? and version=?;")

  val READ_EPOCH = session.prepare("select * from epochs where id=?;")

  def readKey(k: String, v: MVCCVersion): Future[Boolean] = {
    session.executeAsync(READ_DATA.bind.setString(0, k).setString(1, v.version)).map{_.one() != null}
  }

  def writeKey(k: String, v: MVCCVersion): Future[Boolean] = {
    session.executeAsync(UPDATE_DATA.bind.setLong(0, v.v).setString(1, v.version).setString(2, k)).map{_.wasApplied()}
  }

  def checkTx(t: Transaction): Future[Boolean] = {
    Future.sequence(t.rs.map{r => readKey(r.k, r)}).map(!_.contains(false))
  }

  def write(t: Transaction): Future[Boolean] = {
    val wb = new BatchStatement()

    t.ws.foreach { x =>
      val k = x.k
      val v = x.v
      val version = x.version

      wb.add(UPDATE_DATA.bind.setLong(0, v).setString(1, version).setString(2, k))
    }

    session.executeAsync(wb).map(_.wasApplied()).recover { case e =>
      e.printStackTrace()
      false
    }
  }

  /*def write(txs: Seq[Transaction]): Future[Boolean] = {
    Future.sequence(txs.map(write(_))).map(!_.contains(false))
  }*/

  def write(txs: Seq[Transaction]): Future[Boolean] = {
    val wb = new BatchStatement()

    wb.clear()

    txs.foreach { t =>
      t.ws.foreach { x =>
        val k = x.k
        val v = x.v
        val version = x.version

        wb.add(UPDATE_DATA.bind.setLong(0, v).setString(1, version).setString(2, k))
      }
    }

    session.executeAsync(wb).map(_.wasApplied()).recover { case e =>
      e.printStackTrace()
      false
    }
  }

  def check(txs: Seq[Transaction]): Future[Seq[(Transaction, Boolean)]] = {
    Future.sequence(txs.map{t => checkTx(t).map(t -> _)})
  }

  def execute(b: Batch): Future[Boolean] = {
    println(s"${Console.CYAN_B} executing batch at ${id} : ${b.id}${Console.RESET}\n")

    check(b.txs).flatMap { read =>
      val failures = read.filter(!_._2).map(_._1)
      val successes = read.filter(_._2).map(_._1)

      write(successes).flatMap(_ => save(b.id)).map { ok =>
        cMap(b.coordinator) ! BatchDone(b.id, failures.map(_.id), successes.map(_.id))
        ok
      }
    }.recover { case ex =>
        ex.printStackTrace()
        false
    }
  }

  def readBatch(id: String): Future[Boolean] = {
    session.executeAsync(READ_BATCH.bind.setString(0, id)).map {
      _.one.getBool("completed")
    }
  }

  def save(id: String): Future[Boolean] = {
    session.executeAsync(UPDATE_BATCH.bind.setString(0, id)).map(_.wasApplied())
  }

  def run(): Unit = {
    execute(b).onComplete {
      case Success(ok) => next()
      case Failure(ex) =>
        ex.printStackTrace()
        System.exit(1)
    }
  }

  def checkCompleted(): Unit = {

    readBatch(b.id).onComplete {
      case Success(ok) =>

        if(ok) {
          next()
        } else {
          scheduler.scheduleOnce(10 millis)(checkCompleted)
        }

      case Failure(ex) =>
        ex.printStackTrace()
        System.exit(1)
    }
  }

  //var p = 0
  //var pos = 0L
  val batches = TrieMap.empty[String, Batch]
  var b: Batch = null

  val offset = new AtomicInteger(0)
  val positions = TrieMap.empty[Int, Long]

  (0 until EPOCH_TOPIC_PARTITIONS).foreach { p =>
    positions.put(p, 0L)
  }

  var p = 0
  var pos = 0L

  def seekPartition(partition: TopicPartition, pos: Long): Unit = {
    consumer.seek(partition, pos, (event: AsyncResult[Unit]) => {
      if(event.failed()){
        event.cause().printStackTrace()
        seekPartition(partition, pos)
      }
    })
  }

  def assign(partition: TopicPartition, pos: Long): Unit = {
    consumer.assign(partition, new Handler[AsyncResult[Unit]] {
      override def handle(event: AsyncResult[Unit]): Unit = {
        if(event.failed()){
          event.cause().printStackTrace()
          seek()
        } else {
          seekPartition(partition, pos)
        }
      }
    })
  }

  def seek(): Unit = {
    p = offset.get() % EPOCH_TOPIC_PARTITIONS
    pos = positions(p)

    val tp = new io.vertx.kafka.client.common.TopicPartition().setTopic("log").setPartition(p)
    val partition = TopicPartition(tp)

    consumer.assign(partition)
    consumer.seek(partition, pos)

    assign(partition, pos)
  }

  //consumer.subscribe("log")

  def next(): Unit = {
    val all = batches.values.toSeq.sortBy(_.id)

    all.headOption match {
      case None =>

        consumer.commit()

        offset.incrementAndGet()
        positions.put(p, pos + 1L)

        seek()
        consumer.resume()

      case Some(h) =>

        batches.remove(h.id)
        b = h

        if(h.worker.equals(id)){
          run()
        } else {
          checkCompleted()
        }
    }
  }

  def handler(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    val e = Any.parseFrom(evt.value()).unpack(Epoch)

    /*if(evt.partition() != p || pos != evt.offset()){

      println(s"\n\n${Console.RED_B}SEEK GONE WRONG!!!${Console.RESET}\n\n")

      seek()
      return
    }*/

    println(s"processing epoch ${e.id} at worker ${id} partition ${evt.partition()} offset ${evt.offset()}\n")

    e.batches.filter(_.workers.contains(id)).foreach { b =>
      batches.put(b.id, b)
    }

    if(batches.isEmpty){

      consumer.commit()

      offset.incrementAndGet()
      positions.put(p, pos + 1L)

      seek()
      consumer.resume()

      return
    }

    next()
  }

  seek()
  consumer.handler(handler)

  consumer.exceptionHandler((event: Throwable) => {
    event.printStackTrace()
    System.exit(1)
  })

  override def receive: Receive = {
    case _ =>
  }
}
