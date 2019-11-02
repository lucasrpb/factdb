package factdb

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{ActorPath, ActorSystem}
import akka.cluster.client.ClusterClientSettings
import com.datastax.driver.core.Cluster
import org.scalatest.FlatSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MainSpec extends FlatSpec {

  "amount of money" should " be equal after transactions" in {

    val rand = ThreadLocalRandom.current()

    val system = ActorSystem("factdb")

    val initialContacts = Set(
      ActorPath.fromString("akka.tcp://factdb@127.0.0.1:2551/system/receptionist"),
      ActorPath.fromString("akka.tcp://factdb@127.0.0.1:2552/system/receptionist"))
    val settings = ClusterClientSettings(system).withInitialContacts(initialContacts)

    implicit val ec = system.dispatcher

    val n = 100
    var tasks = Seq.empty[Future[Boolean]]
    var clients = Seq.empty[Client]

    for(i<-0 until n){
      val client = new Client(system, settings)
      clients = clients :+ client
      tasks = tasks :+ client.execute()
    }

    val t0 = System.currentTimeMillis()
    val result = Await.result(Future.sequence(tasks), 60 seconds)
    val elapsed = System.currentTimeMillis() - t0

    println(result)

    val len = result.length
    val reqs = (1000 * len)/elapsed

    println(s"elapsed: ${elapsed}ms, req/s: ${reqs} avg. latency: ${elapsed.toDouble/len} ms\n")
    println(s"${result.count(_ == true)}/${len}\n")

    clients.foreach(_.stop())

    Await.ready(system.terminate(), Duration.Inf)
  }

}
