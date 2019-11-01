package factdb

import akka.actor.{ActorPath, ActorSystem}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern._
import akka.util.Timeout
import factdb.protocol._

import scala.concurrent.Await

object Client {

  def main(args: Array[String]): Unit = {

    val system = ActorSystem("client")

    val initialContacts = Set(
      ActorPath.fromString("akka.tcp://factdb@127.0.0.1:2552/system/receptionist"),
      ActorPath.fromString("akka.tcp://factdb@127.0.0.1:2552/system/receptionist"))
    val settings = ClusterClientSettings(system).withInitialContacts(initialContacts)

    val client = system.actorOf(ClusterClient.props(settings), "client")

    implicit val timeout = new Timeout(5 seconds)

    val f = (client ? ClusterClient.Send("/user/c1/singleton", HelloWorld("Lucas"), localAffinity = false))
      .mapTo[HelloWorld].map { response =>
      println(s"${Console.GREEN_B}response ${response}${Console.RESET}\n")
    }.recover { case ex =>
      ex.printStackTrace()
    }

    Await.ready(f, timeout.duration)

    Await.ready(system.terminate(), Duration.Inf)
  }

}
