package factdb

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object Server {

  def main(args: Array[String]): Unit = {

    val port = args(0)

    val config = ConfigFactory.parseString(s"""
            akka.remote.netty.tcp.port=$port
        """).withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem("factdb", config)
    // Create an actor that handles cluster domain events

    system.actorOf(Props[Node], name = "hello")

  }

}
