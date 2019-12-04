package factdb

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist

import scala.concurrent.duration._
import factdb.protocol._
import akka.pattern._
import akka.remote.WireFormats.TimeUnit
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import com.google.protobuf.any.Any
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.client.producer.{KafkaProducer, KafkaProducerRecord}
import org.apache.kafka.clients.producer.ProducerConfig

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}

class Coordinator(val id: String) extends Actor with ActorLogging {

  implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")
  //implicit val ec: ExecutionContext = context.dispatcher
  //val scheduler = context.system.scheduler

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

  val scheduler = context.system.scheduler

  val session = ycluster.connect("s2")

  val READ = session.prepare("select * from data where key=?;")
  val INSERT_BATCH = session.prepare("insert into batches(id, completed, votes) values(?,false,0);")

  val config = scala.collection.mutable.Map[String, String]()
  config += (ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092")
  //config += (ProducerConfig.LINGER_MS_CONFIG -> "10")
  //config += (ProducerConfig.BATCH_SIZE_CONFIG -> (1024 * 1024 * 10).toString)
  config += (ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.StringSerializer")
  config += (ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArraySerializer")
  config += (ProducerConfig.ACKS_CONFIG -> "1")

  val vertx = Vertx.vertx()

  // use producer for interacting with Apache Kafka
  val producer = KafkaProducer.create[String, Array[Byte]](vertx, config)

  def save(b: Batch): Future[Boolean] = {
    session.executeAsync(INSERT_BATCH.bind.setString(0, b.id)).map(_.wasApplied())
  }

  var offset = new AtomicInteger(0)

  def logb(b: Batch): Future[Boolean] = {
    val buf = Any.pack(b).toByteArray
    val now = System.currentTimeMillis()

    val p = offset.get() % Server.coordinators.length
    val record = KafkaProducerRecord.create[String, Array[Byte]]("batches", b.id, buf, now, p)

    producer.sendFuture(record).map { m =>
      offset.incrementAndGet()
      true
    }
  }

  case class Request(t: Transaction, sender: ActorRef, p: Promise[Boolean] = Promise[Boolean](), tmp: Long = System.currentTimeMillis())

  val transactions = new ConcurrentLinkedDeque[Request]()
  val batches = TrieMap.empty[String, Batch]
  val executing = TrieMap.empty[String, Request]

  //val scheduler = new java.util.Timer()

  class Job extends java.util.TimerTask {
    override def run(): Unit = job
  }

  var task: Cancellable = null

  def job(): Unit = synchronized {

    if(transactions.isEmpty){
      task = scheduler.scheduleOnce(10 milliseconds)(job)
      return
    }

    val now = System.currentTimeMillis()
    var tasks = Seq.empty[Request]
    var keys = Seq.empty[String]

    /*val it = batches.iterator()

    while(it.hasNext){
      tasks = tasks :+ it.next()
    }

    tasks = tasks.filter { r =>
      if(!r.t.keys.exists{keys.contains(_)}) {
        keys = keys ++ r.t.keys
        batches.remove(r)
        true
      } else {
        false
      }
    }*/

    while(!transactions.isEmpty){
      val r = transactions.poll()
      tasks = tasks :+ r
    }

    if(tasks.isEmpty){
      task = scheduler.scheduleOnce(10 milliseconds)(job)
      //scheduler.schedule(new Job(), 10L)
      return
    }

    val txs = tasks.map(_.t)
    val partitions = txs.map(_.partitions).flatten.distinct

    val b = Batch(UUID.randomUUID.toString, txs, id, partitions)

    batches.put(b.id, b)
    tasks.foreach { r =>
      executing.put(r.t.id, r)
    }

    logb(b).recover { case ex =>
      ex.printStackTrace()

      batches.remove(b.id)

      tasks.foreach { r =>
        executing.remove(r.t.id)
      }

      tasks.foreach { r =>
        r.p.success(false)
      }
    }.map { _ =>
      task = scheduler.scheduleOnce(10 milliseconds)(job)
    }

    /*save(b).flatMap(ok => logb(b).map(_ && ok))*//*logb(b).map { ok =>
      if(ok){

        batches.put(b.id, b)

        println(s"${Console.GREEN_B}saved batch ${b.id} at coordinator $id${Console.RESET}\n")

        tasks.foreach { r =>
          executing.put(r.t.id, r)
        }
      } else {
        tasks.foreach { r =>
          r.p.success(false)
          //r.sender ! false
        }
      }
    }.recover { case ex =>
      tasks.foreach { r =>
        r.p.success(false)
        //r.sender ! false
      }

      ex.printStackTrace()
    }.onComplete { _ =>
      task = scheduler.scheduleOnce(10 milliseconds)(job)
      //scheduler.schedule(new Job(), 10L)
    }*/

  }

  override def preStart(): Unit = {
    //offset.set(0)
    println(s"STARTING COORDINATOR $id...\n")
    task = scheduler.scheduleOnce(10 milliseconds)(job)
    //scheduler.schedule(new Job(), 10L)
  }

  override def postStop(): Unit = {
    if(task != null) task.cancel()
    //scheduler.cancel()
    println(s"STOPPING COORDINATOR $id...\n")
  }

  def process(t: Transaction): Unit = {
    val r = Request(t, sender)
    transactions.offer(r)

    //sender ! true
    r.p.future.pipeTo(sender).recover { case e =>
      e.printStackTrace()
    }
  }

  def process(done: BatchDone): Unit = {
    /*println(s"aborted ${done.aborted}")
    println(s"committed ${done.committed}\n")*/

    val b = batches.remove(done.id)

    if(b.isEmpty){
      println(s"${Console.BLUE_B}WHOOOPS not found batch ${done.id} coordinator ${id} batches ${batches.map(_._2.id)}${Console.RESET}\n")
    }

    /*if(batches.isEmpty){
      println(s"${Console.YELLOW_B}DONE AT COORDINATOR${Console.RESET}\n")
    }*/

    println(s"${Console.GREEN_B}PROCESSED BATCH ${done.id}${Console.RESET}\n")

    done.aborted.foreach { t =>
      executing.get(t) match {
        case None => println(s"${Console.BLUE_B}ABORTED NOT FOUND TX ${t} at coordinator ${id}!!!${Console.RESET}\n")
        case Some(r) =>
          executing.remove(t)
          r.p.success(false)
         // r.sender ! false
      }
    }

    done.committed.foreach { t =>
      executing.get(t) match {
        case None => println(s"${Console.BLUE_B}NOT FOUND TX ${t} at coordinator ${id}!!!${Console.RESET}\n")
        case Some(r) =>
          executing.remove(t)
          r.p.success(true)
          //r.sender ! true
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
    Future.sequence(cmd.keys.map{read(_)}).map(ReadResponse(_))
      .pipeTo(sender)
  }

  override def receive: Receive = {
    case cmd: Transaction => process(cmd)
    case cmd: ReadRequest => process(cmd)
    case cmd: BatchDone => process(cmd)
    case _ =>
  }
}
