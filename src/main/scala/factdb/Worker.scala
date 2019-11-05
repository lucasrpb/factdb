package factdb

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.datastax.driver.core.BatchStatement
import factdb.protocol._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class Worker(val id: String) extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  val scheduler = context.system.scheduler

  implicit val cluster = Cluster(context.system)
  ClusterClientReceptionist(context.system).registerService(self)

  val tasks = TrieMap[String, (Transaction, Seq[String], Seq[String])]()

  val ycluster = com.datastax.driver.core.Cluster.builder()
    .addContactPoint("127.0.0.1")
    .build()

  val session = ycluster.connect("s2")

  val UPDATE_DATA = session.prepare("update data set value=?, version=? where key=?;")
  val READ_DATA = session.prepare("select * from data where key=? and version=?;")

  def readKey(k: String, v: MVCCVersion): Future[Boolean] = {
    session.executeAsync(READ_DATA.bind.setString(0, k).setString(1, v.version)).map{_.one() != null}
  }

  def checkTx(t: Transaction): Future[Boolean] = {
    Future.sequence(t.rs.map{r => readKey(r.k, r)}).map(!_.contains(false))
  }

  def write(tasks: Seq[Transaction]): Future[Boolean] = {
    val wb = new BatchStatement()

    tasks.foreach { t =>
      t.ws.foreach { v =>
        wb.add(UPDATE_DATA.bind.setLong(0, v.v).setString(1, v.version).setString(2, v.k))
      }
    }

    session.executeAsync(wb).map(_.wasApplied())
  }

  def check(tasks: Seq[Transaction]): Future[Seq[(Transaction, Boolean)]] = {
    Future.sequence(tasks.map{ t => checkTx(t).map(t -> _)})
  }

  val pMap = TrieMap[String, ActorRef]()

  Server.partitions.foreach { p =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$p",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${p}")

    pMap.put(p, proxy)
  }

  val cMap = TrieMap[String, ActorRef]()

  Server.coordinators.foreach { c =>

    val proxy = context.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/$c",
        settings = ClusterSingletonProxySettings(context.system)),
      name = s"proxy-${c}")

    cMap.put(c, proxy)
  }

  def release(list: Seq[Transaction], tasks: TrieMap[String, (Transaction, Seq[String], Seq[String])]): Unit = {
    var requests = Map.empty[String, Seq[String]]

    list.foreach { t =>
      t.partitions.foreach { p =>
        requests.get(p) match {
          case None => requests = requests + (p -> Seq(t.id))
          case Some(l) => requests = requests + (p -> (l :+ t.id))
        }
      }
    }

    requests.foreach { case (p, list) =>
      pMap(p) ! PartitionRelease(p, list)
    }
  }

  def sendToCoordinator(aborted: Seq[Transaction], committed: Seq[Transaction]): Unit = {
    var requests = Map.empty[String, (Seq[String], Seq[String])]

    aborted.foreach { t =>
      requests.get(t.coordinator) match {
        case None => requests = requests + (t.coordinator -> (Seq(t.id), Seq.empty[String]))
        case Some((a, c)) => requests = requests + (t.coordinator -> (a :+ t.id, c))
      }
    }

    committed.foreach { t =>
      requests.get(t.coordinator) match {
        case None => requests = requests + (t.coordinator -> (Seq.empty[String], Seq(t.id)))
        case Some((a, c)) => requests = requests + (t.coordinator -> (a, c :+ t.id))
      }
    }

    requests.foreach { case (c, r) =>
      cMap(c) ! BatchDone("", r._1, r._2)
    }
  }

  def execute(): Unit = {

    val list = tasks.filter { case (_, (t, acks, nacks)) =>
      val all = acks ++ nacks
      t.partitions.forall(all.contains(_))
    }

    if(list.isEmpty) return

    list.foreach { case (t, _) =>
      tasks.remove(t)
    }

    val abort = list.filter{!_._2._3.isEmpty}.map(_._2._1).toSeq
    val commit = list.filter(_._2._3.isEmpty).map(_._2._1).toSeq

    println(s"executing tasks ${commit.map(_.id)}\n")
    println(s"aborting tasks ${abort.map(_.id)}\n")

    check(commit).flatMap { reads =>
      val failures = abort ++ reads.filter(!_._2).map(_._1)
      val successes = reads.filter(_._2).map(_._1)

      write(successes).map { ok =>
        release(failures ++ successes, list)
        sendToCoordinator(failures, successes)
        true
      }
    }
  }

  def process(cmd: PartitionExecute): Unit = synchronized {

    cmd.acks.foreach { t =>
      tasks.get(t.id) match {
        case None => tasks.put(t.id, (t, Seq(cmd.id), Seq.empty[String]))
        case Some(e) => tasks.update(t.id, (t, (e._2 :+ cmd.id), e._3))
      }
    }

    cmd.nacks.foreach { t =>
      tasks.get(t.id) match {
        case None => tasks.put(t.id, (t, Seq.empty[String], Seq(cmd.id)))
        case Some(e) => tasks.update(t.id, (t, e._2, e._3 :+ cmd.id))
      }
    }

    execute()

    sender ! true
  }

  override def preStart(): Unit = {
    println(s"STARTING WORKER $id...\n")
  }

  override def postStop(): Unit = {
    println(s"STOPPING WORKER $id...\n")
  }

  override def receive: Receive = {
    case cmd: PartitionExecute => process(cmd)
    case _ =>
  }
}
