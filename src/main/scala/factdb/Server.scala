package factdb

import java.util.UUID

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory

object Server {

  def main(args: Array[String]): Unit = {

    //val port = args(0)

    def startup(ports: Seq[String]): Unit = {
      ports foreach { port =>

        val config = ConfigFactory.parseString(s"""
            akka.remote.netty.tcp.port=$port
        """).withFallback(ConfigFactory.load())

        // Create an Akka system
        val system = ActorSystem("factdb", config)
        // Create an actor that handles cluster domain events

        //system.actorOf(Props(classOf[Service], s"hello-${port}", port), name = s"hello-${port}")

        system.actorOf(Props(classOf[Coordinator], UUID.randomUUID.toString), name = s"coordinator")

        /*val services = Seq("c1")

        services.foreach { s =>
          system.actorOf(
            ClusterSingletonManager.props(
              singletonProps = Props(classOf[Service], s, port),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)), name = s)
        }*/

      }
    }

    val n = 3
    val BASE_PORT = 2551
    startup((0 until n).map(p => (p + BASE_PORT).toString))

  }

}
