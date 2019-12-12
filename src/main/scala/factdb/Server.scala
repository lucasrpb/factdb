package factdb

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.datastax.driver.core.{HostDistance, PoolingOptions}
import com.typesafe.config.ConfigFactory
import io.vertx.core.{AsyncResult, Handler}
import io.vertx.scala.core.Vertx
import io.vertx.scala.kafka.admin.AdminUtils

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Server {

  val n = 3

  val coordinators = Seq("c0", "c1", "c2")
  val workers = Seq("w0", "w1", "w2")

  /*val pMap = TrieMap[String, ActorRef]()
  val wMap = TrieMap[String, ActorRef]()
  val cMap = TrieMap[String, ActorRef]()*/

  def main(args: Array[String]): Unit = {

    //val port = args(0)

    val admin = AdminUtils.create(Vertx.vertx(), "127.0.0.1:2181", false)

    val p = Promise[Boolean]()

    admin.deleteTopic("log", r => {
      println(s"topic log deleted: ${r.succeeded()}")

      admin.deleteTopic("batches", r => {
        println(s"topic batches deleted: ${r.succeeded()}")

        admin.createTopic("log", EPOCH_TOPIC_PARTITIONS, 1, (r: AsyncResult[Unit]) => {
          println(s"topic log created: ${r.succeeded()}")

          admin.createTopic("batches", EPOCH_TOPIC_PARTITIONS, 1, (r: AsyncResult[Unit]) => {
            println(s"topic batches created: ${r.succeeded()}")

            admin.close(_ => p.success(true))

          })

        })

      })
    })

    Await.ready(p.future, Duration.Inf)

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

    session.execute("truncate table bucetas;")

    def startup(ports: Seq[String]): Unit = {
      ports foreach { port =>

        val config = ConfigFactory.parseString(s"""
            akka.remote.netty.tcp.port=$port
        """).withFallback(ConfigFactory.load("server.conf"))

        // Create an Akka system
        val system = ActorSystem("factdb", config)
        // Create an actor that handles cluster domain events

        //system.actorOf(Props(classOf[Service], s"hello-${port}", port), name = s"hello-${port}")

        //system.actorOf(Props(classOf[Coordinator], UUID.randomUUID.toString), name = s"coordinator")

        coordinators.foreach { s =>
          system.actorOf(
            ClusterSingletonManager.props(
              singletonProps = Props(classOf[Coordinator], s),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)), name = s)
        }

        for(i<-0 until workers.length){
          val s = workers(i)

          system.actorOf(
            ClusterSingletonManager.props(
              singletonProps = Props(classOf[Worker], s, i),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)), name = s)
        }

        /*system.actorOf(
          ClusterSingletonManager.props(
            singletonProps = Props(classOf[Aggregator]),
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(system)), name = "aggregator")*/

        for(i<-0 until 1){
          system.actorOf(
            ClusterSingletonManager.props(
              singletonProps = Props(classOf[Scheduler], i.toString),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)), name = s"scheduler-$i")
        }
      }
    }

    val BASE_PORT = 2551
    startup((0 until n).map(p => (p + BASE_PORT).toString))

  }

}
