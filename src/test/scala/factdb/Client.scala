package factdb

import java.util.UUID

import akka.actor.{ActorPath, ActorSystem}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.util.Timeout
import akka.pattern._

import scala.concurrent.Future
import scala.concurrent.duration._
import factdb.protocol._

class Client(val system: ActorSystem, val settings: ClusterClientSettings) {

  val id = UUID.randomUUID.toString
  val client = system.actorOf(ClusterClient.props(settings), s"client-$id")
  implicit val timeout = new Timeout(5 seconds)
  implicit val ec = system.dispatcher

  def execute(): Future[Boolean] = {
    val t = Transaction(UUID.randomUUID.toString)

    (client ? ClusterClient.Send("/user/coordinator", "hello", localAffinity = false))
      .mapTo[Boolean].map { response =>
      println(s"${Console.GREEN_B}transaction response ${response}${Console.RESET}\n")
      response
    }.recover { case e =>
      e.printStackTrace()
      false
    }
  }

  def stop(): Unit = {
    system.stop(client)
  }
}
