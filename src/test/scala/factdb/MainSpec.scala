package factdb

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{ActorPath, ActorSystem}
import akka.cluster.client.ClusterClientSettings
import com.datastax.driver.core.Cluster
import factdb.protocol._
import org.scalatest.FlatSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MainSpec extends FlatSpec {

  "amount of money" should " be equal after transactions" in {

    val rand = ThreadLocalRandom.current()

    val cluster = Cluster.builder()
      .addContactPoint("127.0.0.1")
      .build()

    val session = cluster.connect("s2")
    session.execute("truncate table batches;")
    session.execute("truncate table epochs;")

    val results = session.execute(s"select * from data;")

    results.forEach(r => {
      val key = r.getString("key")
      val value = r.getLong("value")
      accounts.put(key, value)
    })

    val tb = session.execute("select sum(value) as total from data;").one.getLong("total")

    val system = ActorSystem("factdb")

    val initialContacts = Set(
      ActorPath.fromString("akka.tcp://factdb@127.0.0.1:2551/system/receptionist"),
      ActorPath.fromString("akka.tcp://factdb@127.0.0.1:2552/system/receptionist"))
    val settings = ClusterClientSettings(system).withInitialContacts(initialContacts)

    implicit val ec = system.dispatcher

    val n = 1000
    var tasks = Seq.empty[Future[Boolean]]
    var clients = Seq.empty[Client]

    for(i<-0 until n){
      val client = new Client(system, settings)
      clients = clients :+ client
      tasks = tasks :+ client.execute{ case (tid, reads) =>

        val keys = reads.keys

        val k1 = keys.head
        val k2 = keys.last

        var b1 = reads(k1).v
        var b2 = reads(k2).v

        if(b1 > 0 && !k1.equals(k2)){
          val ammount = if(b1 == 1) 1 else rand.nextLong(1, b1)

          b1 = b1 - ammount
          b2 = b2 + ammount
        }

        Map(k1 -> MVCCVersion(k1, b1, tid), k2 -> MVCCVersion(k2, b2, tid))
      }
    }

    val t0 = System.currentTimeMillis()
    val result = Await.result(Future.sequence(tasks), Duration.Inf)
    val elapsed = System.currentTimeMillis() - t0

    println(result)

    val len = result.length
    val reqs = (1000 * len)/elapsed

    println(s"elapsed: ${elapsed}ms, req/s: ${reqs} avg. latency: ${elapsed.toDouble/len} ms\n")
    println(s"${result.count(_ == true)}/${len}\n")

    val ta = session.execute("select sum(value) as total from data;").one.getLong("total")

    println(s"${Console.YELLOW}total before ${tb} total after ${ta}${Console.RESET}\n\n")

    session.close()
    cluster.close()

    assert(ta == tb)

    clients.foreach(_.stop())

    Await.ready(system.terminate(), Duration.Inf)
  }

}
