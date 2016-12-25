package rafters
import akka.actor._
import akka.actor.FSM._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import java.util.{Timer, TimerTask, Random}

class RaftActor(mbr: Seq[String]) extends Actor with LoggingFSM[Role, StateDat]  with ReplicatedStateMachine {
  //val memberGen = (member: String) => context.actorSelection("akka.tcp://rafters@" + member + "/user/raft")
  val memberGen = {(member: String) =>
    val addr = member.split(":")
    val address = Address("akka.tcp", "rafters", addr(0), addr(1).toInt)
    address.toString + "/user/raft"
  }
  var memberList = mbr.map(x => memberGen(x))
  startWith(Follower, StateDat(MemberList = memberList, timer = NewTimer))
  when(Follower) {
    case Event(rpc: AppendEntriesRPC, data) =>
      data.term = rpc.term
      if (StateFuncs.checkAppend(data, rpc)) {
        data.log = StateFuncs.appendEntries(data.log, rpc)
        sender ! AppendEntriesResponse(data.term, true)
        val entries = rpc.entries.filter(x => utils.checkNull(x))
        for (x <- entries) {
          try {
            executeCommand(x.command)
          } catch {
              case e: Exception => None
          }
        }
      } else {
        sender ! AppendEntriesResponse(data.term, false)
      }
      stay()

    case Event(rpc: RequestVote, data) =>
      if(Vote(rpc, data)) {
        data.votedFor = Some(sender)
        sender ! VoteResults(data.term, true)
      } else {
        sender ! VoteResults(data.term, false)
      }
      stay()

    case Event(rpc: Heartbeat, data) => {
      if (rpc.term >= data.term) {
        data.term = rpc.term
        data.timer = ResetTimer(data.timer)
        data.leader = Some(sender)
        stay()
      } else {
        stay
      }
    }
    case Event(rpc: ClientRequest, data) =>
      ForwardToLeader(rpc, data)
      stay()

    case Event(Timeout, data) =>
      goto(Candidate) using data

    case Event(rpc: AddServerIn, data) =>
      ForwardToLeader(AddServer(sender), data)
      stay()
    case Event(rpc: AddServer, data) =>
      data.MemberList = data.MemberList :+ rpc.newServer.toString
      stay()
    case Event(joinExisting, data) =>
      val rand = new Random()
      val bootstrap = data.MemberList(rand.nextInt(data.MemberList.length))
      join(bootstrap)
      stay()

  }
  when(Candidate) {
    case Event(BeginCampaign, data) =>
      data.timer = ResetTimer(data.timer)
      Campaign(data)
      stay()

    case Event(rpc: VoteResults, data) =>
      if (rpc.voteGranted == true) {
          data.Votes += 1
      }
      stay()

    case Event(rpc: Heartbeat, data) =>
      data.leader = Some(sender)
      data.timer = ResetTimer(data.timer)
      goto(Follower) using data

    case Event(Timeout, data) =>
      if (checkVotes(data)) {
        goto(Leader) using data
      } else {
        data.votedFor = None
        data.Votes = 0
        data.timer = ResetTimer(data.timer)
        goto(Follower)
      }
  }

  when(Leader) {
    case Event(Start, data) =>
      data.term += 1
      val beat = Heartbeat(data.term)
      sendToAll(beat, data)
      data.timer = LeaderTimer(beat, data)
      stay()
    case Event(rpc: AddServer, data) =>
      data.MemberList = data.MemberList :+ rpc.newServer.toString
      sendToAll(rpc, data)
      stay()
    case Event(rpc: AddServerIn, data) =>
      data.MemberList = data.MemberList :+ sender.toString
      val ms = AddServer(sender)
      sendToAll(ms, data)
      stay()
    case Event(rpc: ClientRequest, data) => {
      data.log.commitIndex += 1
      data.log.lastApplied += 1
      val sel = sender
      val res = executeCommand(rpc.command)

      res match {
        case None => sel ! ClientResponse(None)
        case Some(x) => sel ! ClientResponse(Some(x))
      }

      val message = AppendEntriesRPC(data.term, data.log.lastApplied, Seq[Entry](Entry(rpc.command, data.term, Some(rpc.clientId))), data.log.commitIndex)
      sendToAll(message, data)
      stay()
    }
  }

  onTransition {
    case Candidate -> Leader =>
      println("I am leader")
      self ! Start
    case Follower -> Candidate =>
      println("I am candidate")
      self ! BeginCampaign
    case Candidate -> Follower =>
      println("I am Follower")
  }
  whenUnhandled {
    case Event(_, _) =>
      stay()
  }
  def sendToAll(rpc: Any, data: StateDat): Unit = {
    val config = context.system.settings.config
    val maddr = config.getValue("akka.remote.netty.tcp.hostname").render().replaceAll(""""""", "") + ":"  + config.getValue("akka.remote.netty.tcp.port").render().toString
    val mlist = data.MemberList.filter(!_.contains(maddr))
    for (x <- mlist) {
      context.actorSelection(x) ! rpc
    }
  }
  def join(bootstrap: String): Unit = {
    context.actorSelection(bootstrap) ! AddServerIn()
  }
  def checkVotes(data: StateDat): Boolean = {
    val majority = data.MemberList.length / 2.0
    if (data.Votes > majority) {
      return true
    } else {
      return false
    }
  }
  def Campaign(data: StateDat): Unit = {
    data.votedFor = Some(self)
    data.Votes += 1
    val checkElement = { (s: String) =>
      if (s != null && !s.isEmpty) {
        true
      } else {
        false
      }
    }
    var mlist = data.MemberList.filter(x => checkElement(x))
    for (x <- mlist) {
      context.actorSelection(x) ! RequestVote(data.term, data.log.commitIndex, data.term)
    }
    //data.term += 1

  }
  def ForwardToLeader(rpc: RPC, data: StateDat): Unit = {
    data.leader match {
      case Some(target) => target forward rpc
      case None => None
    }
  }
  def Vote(rpc: RequestVote, data: StateDat): Boolean = {
    if (rpc.term >= data.term && data.votedFor == None && rpc.lastLogIndex >= data.log.commitIndex) {
      return true
    } else {
      return false
    }
  }
  def NewTimer(): Timer = {
    val rand = new Random()
    var timer = new Timer()
    val task = new TimerTask{def run() = self ! Timeout}
    val timeout = 500 + rand.nextInt(1000 - 500)
    timer.schedule(task, timeout, timeout)
    return timer
  }
  def LeaderTimer(rpc: Heartbeat, data: StateDat): Timer = {
    val rand = new Random()
    var timer = new Timer()
    var task = new TimerTask {def run() = {sendToAll(rpc, data)}}
    timer.schedule(task, 50, 50)
    return timer
  }
  def ResetTimer(timer: Timer): Timer = {
    timer.cancel()
    var mytime = NewTimer()
    return mytime
  }
  initialize()
}
