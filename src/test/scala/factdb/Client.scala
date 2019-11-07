package factdb

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{ActorPath, ActorRef, ActorSelection, ActorSystem}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout
import akka.pattern._

import scala.concurrent.Future
import scala.concurrent.duration._
import factdb.protocol._

import scala.collection.concurrent.TrieMap

class Client(val system: ActorSystem, val settings: ClusterClientSettings) {

  val id = UUID.randomUUID.toString
  val client = system.actorOf(ClusterClient.props(settings), s"client-$id")
  implicit val timeout = new Timeout(300 seconds)
  implicit val ec = system.dispatcher

  val tid = UUID.randomUUID.toString()
  val rand = ThreadLocalRandom.current()

  def execute(f: ((String, Map[String, MVCCVersion])) => Map[String, MVCCVersion]): Future[Boolean] = {

    val accs = accounts.keys.toSeq

    val k1 = accs(rand.nextInt(0, accs.length)).toString
    val k2 = accs(rand.nextInt(0, accs.length)).toString

    val keys = Seq(k1, k2)

    val c = scala.util.Random.shuffle(Server.coordinators).head

    (client ? ClusterClient.Send(s"/user/${c}/singleton", ReadRequest(keys), localAffinity = false)).flatMap { r =>
      val reads = r.asInstanceOf[ReadResponse].values
      val writes = f(tid -> reads.map(v => v.k -> v).toMap)

      val dkeys = keys.distinct
      val partitions = dkeys.map(computeHash(_)).distinct
      val tx = Transaction(tid, dkeys, reads, writes.map(_._2).toSeq, partitions,
        scala.util.Random.shuffle(Server.workers).head, c)

      (client ? ClusterClient.Send(s"/user/${c}/singleton", tx, localAffinity = false)).mapTo[Boolean]
    }.recover { case e =>
      //e.printStackTrace()
      false
    }.map {
      _ match {
        case true =>
          println(s"tx ${tid} succeed")
          true
        case false =>
          println(s"tx ${tid} failed")
          false
      }
    }

    /*val t = Transaction(UUID.randomUUID.toString)

    (client ? ClusterClient.Send(s"/user/${c}/singleton", t, localAffinity = false))
      .mapTo[Boolean].map { response =>
      println(s"${Console.GREEN_B}transaction response ${response}${Console.RESET}\n")
      response
    }.recover { case e =>
      e.printStackTrace()
      false
    }*/
  }

  def stop(): Unit = {
    system.stop(client)
  }
}
