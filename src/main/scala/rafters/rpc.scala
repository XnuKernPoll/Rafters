package rafters
import akka.actor._
import java.util.UUID
trait RPC

case class Timeout() extends RPC
case object Start extends RPC
case object BeginCampaign extends RPC
case class Heartbeat(term: Int) extends RPC
case class RequestVote(term: Int, lastLogIndex: Int, lastLogTerm: Int) extends RPC
case class VoteResults(term: Int, voteGranted: Boolean) extends RPC
case class AddServer(newServer: ActorRef) extends RPC
case class AddServerIn() extends RPC
case class RemoveServer(oldServer: ActorSelection) extends RPC
case class ServerResults(status: String, leaderHint: Option[ActorSelection]) extends RPC
case class ClientRequest(clientId: UUID, command: Command) extends RPC
case class ClientResponse(response: Option[Any]) extends RPC
case object joinExisting extends RPC
//case class ClientResults(status: String, response: Option[Any], leaderHint: Option[ActorSelection]) extends RPC
case class AppendEntriesRPC(
  term: Int,
  prevLogIndex: Int,
  entries: Seq[Entry],
  leaderCommit: Int
) extends RPC
case class AppendEntriesResponse(term: Int, success: Boolean) extends RPC
