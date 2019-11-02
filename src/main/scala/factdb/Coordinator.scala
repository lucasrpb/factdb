package factdb

import java.util.concurrent.ConcurrentLinkedDeque

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist

import scala.concurrent.duration._
import factdb.protocol._
import akka.pattern._

import scala.concurrent.Promise

class Coordinator(val id: String) extends Actor with ActorLogging {

  case class Request(t: Transaction, p: Promise[Boolean] = Promise[Boolean](), tmp: Long = System.currentTimeMillis())

  implicit val ec = context.dispatcher
  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val batch = new ConcurrentLinkedDeque[Request]()

  def job(): Unit = {

  }

  override def preStart(): Unit = {
    println(s"STARTING COORDINATOR $id...\n")
    //scheduler.scheduleOnce(10 milliseconds)(job)
  }

  override def postStop(): Unit = {
    println(s"STOPPING COORDINATOR $id...\n")
  }

  def process(t: Transaction): Unit = {
    val r = Request(t)
    batch.offer(r)
    //r.p.future.pipeTo(sender)

    log.info(s"${Console.GREEN_B}received transaction at coordinator ${id} ${t.id}...${Console.RESET}\n")

    sender ! true
  }

  override def receive: Receive = {
    case cmd: String =>
      log.info(s"${Console.RED_B}coordinator received message ${cmd}${Console.RESET}\n")
      sender ! true
    case cmd: Transaction => process(cmd)
    case _ =>
  }
}
