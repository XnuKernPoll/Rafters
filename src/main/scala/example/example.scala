import rafters._
import akka.actor._
case class Put(key: String, value: String) extends Command
case class Get(key: String) extends Command
case class Update(key: String, value: String) extends Command
case class Delete(key: String) extends Command
class DDB(mbr: Seq[String]) extends RaftActor(mbr) {
  var map = scala.collection.mutable.Map[String, String]()
  override def executeCommand(cmd: Command): Any = {
    cmd match {
      case Put(key, value) =>
        map put (key, value)
      case Get(key) =>
        map get (key)
      case Update(key, value) =>
        map update (key, value)
      case Delete(key) =>
        map -= key
    }
  }
}

class Cly extends RaftersClient {
  override def handleResp(resp: Any): Unit = {
    resp match {
      case None => None
      case Some(x) => println(x)
    }
  }
}

object MyExample extends App {
  val memberList = Seq("127.0.0.1:900", "127.0.0.1:4999", "127.0.0.1:3921")
  for (x <- memberList) {
    val s = utils.startSystem(x)
    s.actorOf(Props(new DDB(memberList)), name="raft")
  }
  Thread.sleep(9000)
  val system = utils.startSystem("127.0.0.1:9033")
  val client = system.actorOf(Props[Cly], name = "raftClient")
  val addr = utils.chooseRandom(memberList)

  client ! sendRequest(Put("h", "w"), addr)
  client ! sendRequest(Get("h"), addr)
}
