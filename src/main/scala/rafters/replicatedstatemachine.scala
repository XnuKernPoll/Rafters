package rafters
import java.util.{UUID, Random}
import akka.actor._
trait Command

trait ReplicatedStateMachine {
  def executeCommand(cmd: Command): Any = ???
}
case class sendRequest(command: Command, addr: String)

class RaftersClient extends Actor {

  def sendRequest(command: Command, addr: String): Any = {
    val uuid = UUID.randomUUID()
    context.actorSelection(addr) !  ClientRequest(uuid, command)
  }
  def handleResp(response: Any): Any = ???
  def receive = {
    case sendRequest(command, address) =>
      sendRequest(command, address)
    case ClientResponse(resp) =>
      handleResp(resp)
  }
}
