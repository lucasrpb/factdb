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
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Server {

  val n = 3

  val coordinators = Seq("c0", "c1", "c2")
  val workers = Seq("w0", "w1", "w2")

  val LOG_TOPICS = Seq("l0", "l1", "l2")

  /*val pMap = TrieMap[String, ActorRef]()
  val wMap = TrieMap[String, ActorRef]()
  val cMap = TrieMap[String, ActorRef]()*/

  def main(args: Array[String]): Unit = {

    //val port = args(0)

    val admin = AdminUtils.create(Vertx.vertx(), "127.0.0.1:2181", false)

    val topics = Seq("log" -> EPOCH_TOPIC_PARTITIONS, "batches" -> coordinators.length).toMap

    def createPartition(t: String, n: Int, rf: Int): Future[Boolean] = {
      val p = Promise[Boolean]()

      admin.topicExists(t, (event: AsyncResult[Boolean]) => {
        println(s"$t exists: ${event.result()}")

        if(event.failed()){
          p.failure(event.cause())
        } else if(event.result()){
          admin.deleteTopic(t, (event: AsyncResult[Unit])=> {
            if(event.failed()){
              p.failure(event.cause())
            } else {

              println(s"$t deleted!")

              admin.createTopic(t, n, rf, (event: AsyncResult[Unit]) => {

                if(event.failed()){
                  p.failure(event.cause())
                } else {
                  println(s"$t created!")
                  p.success(true)
                }
              })

            }
          })
        } else {

          admin.createTopic(t, n, rf, (event: AsyncResult[Unit]) => {

            if(event.failed()){
              p.failure(event.cause())
            } else {
              println(s"$t created!")
              p.success(true)
            }
          })

        }
      })

      p.future
    }

    val f = Future.sequence(topics.map{case (t, n) => createPartition(t, n, 1)})
      .map(_ => true)
        .recover { case ex =>
          ex.printStackTrace()
          System.exit(1)
          false
        }

    Await.ready(f, Duration.Inf)

    /*val f = Future.sequence(topics.map{case (t, _) => admin.topicExistsFuture(t).map(t -> _)})
      .flatMap { checks =>
        Future.sequence(checks.filter(_._2).map(_._1).map{admin.deleteTopicFuture(_)})
    }.flatMap { _ =>
      println(s"topics ${topics.map(_._1)} deleted!\n")
      Future.sequence(topics.map{case (t, n) => admin.createTopicFuture(t, n, 1)})
    }.flatMap { _ =>
      println(s"topics ${topics.map(_._1)} created!\n")
      admin.closeFuture().map(_ => true)
    }.recover { case ex =>
      ex.printStackTrace()
      false
    }

    Await.ready(f, Duration.Inf)*/

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

    session.execute("truncate table epochs;")

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

        system.actorOf(
          ClusterSingletonManager.props(
            singletonProps = Props(classOf[Aggregator]),
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(system)), name = "aggregator")

        for(i<-0 until workers.length){
          val s = workers(i)

          system.actorOf(
            ClusterSingletonManager.props(
              singletonProps = Props(classOf[Worker], s),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)), name = s)
        }

        /*for(i<-0 until 1){
          system.actorOf(
            ClusterSingletonManager.props(
              singletonProps = Props(classOf[Scheduler], i.toString),
              terminationMessage = PoisonPill,
              settings = ClusterSingletonManagerSettings(system)), name = s"scheduler-$i")
        }*/

      }
    }

    val BASE_PORT = 2551
    startup((0 until n).map(p => (p + BASE_PORT).toString))

  }

}
