package factdb

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import com.datastax.driver.core.{Cluster, ResultSet}
import org.scalatest.FlatSpec

import scala.concurrent.Future

class InsertSpec extends FlatSpec {

  "insertion" should "execute succesfully" in {

    val cluster = Cluster.builder()
      .addContactPoint("127.0.0.1")
      .build();

    val session = cluster.connect("s2")

    val INSERT_DATA = session.prepare("insert into data(key, value, version) values(?,?,?);")

    val n = 1000

    val rand = ThreadLocalRandom.current()
    val MAX_VALUE = 1000L

    val tid = UUID.randomUUID.toString

    for(i<-0 until n){
      val key = UUID.randomUUID.toString
      session.execute(INSERT_DATA.bind.setString(0, key).setLong(1, rand.nextLong(0, MAX_VALUE)).setString(2, tid))
    }
  }

}
