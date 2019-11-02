package factdb

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.ddata.DistributedData
import akka.cluster.pubsub.DistributedPubSub
import factdb.protocol._
import akka.cluster.ddata._
import scala.concurrent.duration._

import scala.concurrent.duration.Duration

class Service(val id: String, val port: String) extends Actor with ActorLogging {

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val replicator: ActorRef = DistributedData(context.system).replicator
  implicit val node = DistributedData(context.system).selfUniqueAddress

  val Key = ORMapKey.create[String, SameMessage]("txs")

  replicator ! Replicator.Subscribe(Key, self)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {

    println(s"STARTING NODE ${port}\n")

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case cmd: HelloWorld =>

      println(s"${Console.BLUE_B}server ${node} received ${cmd.msg}${Console.RESET}\n")

      replicator ! Replicator.Update(Key, ORMap.empty[String, SameMessage], Replicator.WriteLocal){
        _ :+ (port -> SameMessage(cmd.msg))
      }

      sender ! HelloWorld("ok from server!")

    case change @ Replicator.Changed(Key) =>
      val data = change.get(Key)

      println(s"${Console.RED_B}all txs for ${port}: ${data.entries}${Console.RESET}\n")

    //sender ! HelloWorld(s"Hi, ${cmd.msg}!")

    /*case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case _: MemberEvent => // ignore*/
  }
}
