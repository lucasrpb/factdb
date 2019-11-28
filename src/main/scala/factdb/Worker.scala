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
import com.datastax.driver.core.{BatchStatement, HostDistance, PoolingOptions}
import collection.JavaConverters._

import scala.concurrent.duration._

class Worker(val id: String, val partition: Int) extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

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

  val READ_BATCH = session.prepare("select * from batches where id=?;")
  val UPDATE_BATCH = session.prepare("update batches set completed = true where id=?;")
  val ADD_VOTE = session.prepare("update batches set votes = votes + 1 where id=?;")

  val UPDATE_DATA = session.prepare("update data set value=?, version=? where key=?;")
  val READ_DATA = session.prepare("select * from data where key=? and version=?;")

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

  def updateBatch(id: String): Future[Boolean] = {
    session.executeAsync(UPDATE_BATCH.bind.setString(0, id)).map(_.wasApplied())
  }

  /*def execute(b: Batch): Future[Boolean] = {
    check(b.txs).flatMap { reads =>
      val conflicted = reads.filter(_._2 == false).map(_._1.id)
      val applied = reads.filter(_._2 == true).map(_._1)

      write(applied).flatMap { _ =>
        updateBatch(b.id).map { _ =>

          cMap(b.coordinator) ! BatchDone(id, conflicted, applied.map(_.id))

          b.workers.filterNot(_.equals(id)).map { w =>
            wMap(w) ! BatchFinished(b.id)
          }

          consumer.commit()
          consumer.resume()

          true
        }
      }
    }

  }*/

  def execute(b: Batch): Future[Boolean] = {
    check(b.txs).flatMap { reads =>
      val conflicted = reads.filter(_._2 == false).map(_._1.id)
      val applied = reads.filter(_._2 == true).map(_._1)

      write(applied).map { _ =>
        cMap(b.coordinator) ! BatchDone(id, conflicted, applied.map(_.id))
        true
      }
    }
  }

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

  /*consumer.subscribeFuture("log").onComplete {
    case Success(result) => {
      println(s"worker ${id} subscribed!")
    }
    case Failure(cause) => cause.printStackTrace()
  }*/

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

  var b: Batch = null

  def handle(evt: KafkaConsumerRecord[String, Array[Byte]]): Unit = {
    consumer.pause()

    b = Any.parseFrom(evt.value()).unpack(Batch)

    /*if(b.worker.equals(id)){
      execute(b)
    }

    if(!b.workers.contains(id)){
      consumer.commit()
      consumer.resume()
    }*/

    execute(b)
  }

 // consumer.handler(handle)

  /*def check(b: Batch): Unit = {
    scheduler.scheduleOnce(10 millis){
      session.executeAsync(READ_BATCH.bind.setString(0, b.id)).map { r =>
        val one = r.one()

        if(one.getInt("votes") == b.workers.length){
          execute(b)
        } else {
          check(b)
        }
      }
    }
  }

  def vote(b: Batch): Unit = {
    session.executeAsync(ADD_VOTE.bind.setString(0, b.id)).map { _ =>
      if(b.worker.equals(id)){
        check(b)
      }
    }
  }*/

  override def preStart(): Unit = {
    println(s"STARTING WORKER $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING WORKER $id...\n")
  }

  def process(cmd: BatchFinished): Unit = {
    //if(b.id.equals(cmd.id)){
      consumer.commit()
      consumer.resume()
    //}
  }

  val sc = context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = s"/user/scheduler",
      settings = ClusterSingletonProxySettings(context.system)),
    name = s"proxy-scheduler")

  def process(batch: Batch): Unit = {
    execute(batch).map { _ =>
       //sc ! BatchFinished(batch.id)

      BatchFinished(batch.id)
    }.pipeTo(sender)
  }

  override def receive: Receive = {
    //case cmd: BatchFinished => process(cmd)
    case cmd: Batch => process(cmd)
    case _ =>
  }
}
