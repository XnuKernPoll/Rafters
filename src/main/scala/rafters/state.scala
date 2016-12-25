package rafters
import akka.actor._
import scala.math.min
import java.util.{Timer, UUID}
trait Role
case class Log(var entries: Seq[Entry], var commitIndex: Int = 0, var lastApplied: Int = 0)
case class Entry(val command: Command, val term: Int, val Client: Option[UUID] = None)
case object Follower extends Role
case object Leader extends Role
case object Candidate extends Role

case class StateDat(
  var term: Int = 0,
  var log: Log = Log(entries = Seq[Entry]()),
  var MemberList: Seq[String],
  var leader: Option[ActorRef] = None,
  var votedFor: Option[ActorRef] =  None,
  var Votes: Int = 0,
  var timer: Timer
)

object StateFuncs {

  def checkAppend(data: StateDat, rpc:AppendEntriesRPC): Boolean = {
    if (rpc.term < data.term) {
      return false
    } else {
      return true
    }
  }
  def appendEntries(lg: Log, rpc: AppendEntriesRPC): Log = {
    lg.entries = lg.entries ++ rpc.entries
    lg.commitIndex = rpc.leaderCommit
    if (rpc.leaderCommit > lg.commitIndex) {
      lg.commitIndex = min(rpc.leaderCommit, lg.entries.length)
    }
    return lg
  }
}
