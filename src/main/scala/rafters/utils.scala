package rafters
import akka.actor._
import com.typesafe.config._
import java.util.Random
object utils {
  val addressFormat = (addrs: String) => addrs.split(":")
  def startSystem(myaddress: String): ActorSystem = {
    val addr = addressFormat(myaddress)
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + addr(0))
      .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + addr(1)))
      .withFallback(ConfigFactory.load())
    val system = ActorSystem("rafters", conf)
    return system
  }
  val memberGen = {(member: String) =>
    val addr = member.split(":")
    val address = Address("akka.tcp", "rafters", addr(0), addr(1).toInt)
    address.toString + "/user/raft"
  }
  def chooseRandom(mlist: Seq[String]): String = {
    val r = new Random()
    val n = r.nextInt(mlist.length)
    return memberGen(mlist(n))
  }
  val checkElement = { (s: String) =>
    if (s != null && !s.isEmpty) {
      true
    } else {
      false
    }
  }
  val checkNull = {(s: Any) =>
    if (s != null) {
      true
    } else {
      false
    }
  }
}
