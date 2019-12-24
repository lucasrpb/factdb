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
import com.google.protobuf.any.Any
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.common.TopicPartition
import io.vertx.scala.kafka.client.consumer.{KafkaConsumer, KafkaConsumerRecord}
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.Future
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
  config += (ConsumerConfig.GROUP_ID_CONFIG -> s"worker-$id")
  config += (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "latest")
  config += (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false")

  // use consumer for interacting with Apache Kafka
  var consumer = KafkaConsumer.create[String, Array[Byte]](vertx, config)

  consumer.subscribe("log")

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
    .setMaxRequestsPerConnection(HostDistance.LOCAL, 2000)
  //.setNewConnectionThreshold(HostDistance.LOCAL, 2000)
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

  def process(cmd: Batch): Unit = {
    execute(cmd).map(ok => BatchDone(id)).pipeTo(sender)
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
      case Success(ok) =>
        consumer.commit()
        consumer = consumer.resume()

      case Failure(ex) =>
        ex.printStackTrace()
    }
  }

  def checkCompleted(): Unit = {
    readBatch(b.id).onComplete {
      case Success(completed) =>

        if(completed){
          consumer.commit()
          consumer = consumer.resume()
        } else {
          scheduler.scheduleOnce(10 millis)(checkCompleted)
        }

      case Failure(ex) => ex.printStackTrace()
    }
  }

  var b: Batch = null

  val batches = TrieMap.empty[String, Batch]

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
  }

  def handler(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    b = Any.parseFrom(evt.value()).unpack(Batch)

    if(b.worker.equals(id)){
      run()
      return
    }

    checkCompleted()
  }

  //seek()
  consumer.handler(handler)

  override def receive: Receive = {
    //case cmd: Batch => process(cmd)
    case _ =>
  }
}
