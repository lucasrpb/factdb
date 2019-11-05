package factdb

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque

import akka.actor.{Actor, ActorLogging, Cancellable}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist

import scala.concurrent.duration._
import factdb.protocol._
import akka.pattern._
import com.google.protobuf.any.Any
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.producer.{KafkaProducer, KafkaProducerRecord}
import org.apache.kafka.clients.producer.ProducerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}

class Coordinator(val id: String) extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  val config = scala.collection.mutable.Map[String, String]()

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val ycluster = com.datastax.driver.core.Cluster.builder()
    .addContactPoint("127.0.0.1")
    .build()

  val session = ycluster.connect("s2")

  val READ = session.prepare("select * from data where key=?;")

  config += (ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  //config += (ProducerConfig.LINGER_MS_CONFIG -> "10")
  //config += (ProducerConfig.BATCH_SIZE_CONFIG -> (1024 * 1024 * 10).toString)
  config += (ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringSerializer")
  config += (ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArraySerializer")
  config += (ProducerConfig.ACKS_CONFIG -> "1")

  val vertx = Vertx.vertx()

  // use producer for interacting with Apache Kafka
  val producer = KafkaProducer.create[String, Array[Byte]](vertx, config)

  def logb(b: Batch): Future[Boolean] = {
    val buf = Any.pack(b).toByteArray
    val now = System.currentTimeMillis()
    val record = KafkaProducerRecord.create[String, Array[Byte]]("batches", b.id, buf)

    producer.writeFuture(record).map { _ =>
      true
    }
  }

  case class Request(t: Transaction, p: Promise[Boolean] = Promise[Boolean](), tmp: Long = System.currentTimeMillis())

  val batches = new ConcurrentLinkedDeque[Request]()
  val executing = TrieMap[String, Request]()

  var task: Cancellable = null

  def job(): Unit = {

    if(batches.isEmpty){
      task = scheduler.scheduleOnce(10 milliseconds)(job)
      return
    }

    val now = System.currentTimeMillis()
    var tasks = Seq.empty[Request]
    var keys = Seq.empty[String]

    while(!batches.isEmpty){
      val r = batches.poll()
      val elapsed = now - r.tmp

      if(elapsed >= TIMEOUT){
        r.p.success(false)
      } else if(!r.t.keys.exists{keys.contains(_)}) {
        keys = keys ++ r.t.keys
        tasks = tasks :+ r
      } else {
        r.p.success(false)
      }
    }

    if(tasks.isEmpty){
      task = scheduler.scheduleOnce(10 milliseconds)(job)
      return
    }

    val b = Batch(UUID.randomUUID.toString, tasks.map(_.t), id)

    logb(b).map { ok =>
      if(ok){
        tasks.foreach { r =>
          executing.put(r.t.id, r)
        }
      } else {
        tasks.foreach { r =>
          r.p.success(false)
        }
      }
    }.recover { case ex =>
      tasks.foreach { r =>
        r.p.success(false)
      }

      ex.printStackTrace()
    }.onComplete { _ =>
      task = scheduler.scheduleOnce(10 milliseconds)(job)
    }

  }

  override def preStart(): Unit = {
    println(s"STARTING COORDINATOR $id...\n")
    task = scheduler.scheduleOnce(10 milliseconds)(job)
  }

  override def postStop(): Unit = {
    if(task != null) task.cancel()
    println(s"STOPPING COORDINATOR $id...\n")
  }

  def process(t: Transaction): Unit = {
    val r = Request(t)
    batches.offer(r)

    //sender ! true
    r.p.future.pipeTo(sender)
  }

  def process(done: BatchDone): Unit = {
    println(s"aborted ${done.aborted}")
    println(s"committed ${done.committed}\n")

    done.aborted.foreach { t =>
      executing.get(t) match {
        case None =>
        case Some(r) =>
          executing.remove(t)
          r.p.success(false)
      }
    }

    done.committed.foreach { t =>
      executing.get(t) match {
        case None =>
        case Some(r) =>
          executing.remove(t)
          r.p.success(true)
      }
    }

    sender ! true
  }

  def read(key: String): Future[MVCCVersion] = {
    session.executeAsync(READ.bind.setString(0, key)).map { rs =>
      val one = rs.one()
      MVCCVersion(one.getString("key"), one.getLong("value"), one.getString("version"))
    }
  }

  def process(cmd: ReadRequest): Unit = {
    Future.sequence(cmd.keys.map{read(_)}).map(r => ReadResponse(r))
      .pipeTo(sender)
  }

  override def receive: Receive = {
    case cmd: Transaction => process(cmd)
    case cmd: ReadRequest => process(cmd)
    case cmd: BatchDone => process(cmd)
    case _ =>
  }
}
