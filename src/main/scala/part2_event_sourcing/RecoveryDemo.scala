package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)
  class RecoveryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "recovery-actor"
//    var latestPersistedEventId = 0


    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command (contents) =>
        persist (Event (latestPersistedEventId, contents) ) { event =>
          log.info (s"Successfully persisted $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case Event(id, contents) =>
        log.info(s"Recovered: $contents, recovery is ${if (this.recoveryFinished) "" else "NOT" } finished")
        // this will not change the event handler during recovery
        // AFTER recovery the "normal" handler will be the result of ALL stacking of context.become

        context.become(online(id + 1)) //
      case RecoveryCompleted =>
        log.info("I have finished recovering")
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    //    override def recovery: Recovery = Recovery(toSequenceNr = 100)
    //    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none

  }


  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /*
    Stashing commands
   */
  for (i <- 1 to 1000) {
    recoveryActor ! Command(s"command $i")
  }

  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
    2 - failure during recovery
      - onRecoveryFailure + the actor STOPPED because it is in an inconsistent state
   */

  /*
    3 - Customizing recovery
       - Do not persist more events after a customized _incomplete_ recovery
   */

  /*
    4. Recovery status or KNOWING when you're done recovering
      - getting a signal when you are done recovering
   */

  /*

   */


}
