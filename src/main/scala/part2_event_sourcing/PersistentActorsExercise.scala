package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import scala.collection.mutable

object PersistentActorsExercise extends App {
  /*
    Persistent actor for a voting station
    Keep:
      - the citizens who voted
      - the poll: mapping between a candidate and the number of received votes so far

    The actor must be able to recover its state if it's shutdown or restarted
   */

  case class Vote(citizenPID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {
    override def persistenceId: String = "simple-voting-station"
    val citizens: mutable.Set[String] = new mutable.HashSet[String]
    val poll: mutable.Map[String, Int] = new mutable.HashMap[String, Int]

    def handleInternalStateChange(citizenPID: String, candidate: String): Unit = {
      citizens.add(citizenPID)
      val votes = poll.getOrElse(candidate, 0)
      poll.put(candidate, votes + 1)
    }

    override def receiveCommand: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        if (!citizens.contains(vote.citizenPID)) {
          /*
          1) create the event
          2) persist the event
          3) handle a state change after persisting is successful
         */
          persist(vote) { _ => //COMMAND Sourcing
            log.info(s"Persisted: $vote")
            handleInternalStateChange(citizenPID, candidate)
          }
        }
      case "print" =>
        log.info(s"Current state: \nCitizens: $citizens, \nPoll: $poll")
    }

    override def receiveRecover: Receive = {
      case vote @ Vote(citizenPID, candidate) =>
        log.info(s"Recovered: $vote")
        handleInternalStateChange(citizenPID, candidate)
    }
  }

  val system = ActorSystem("PersistentActorsExercise")
  val votingStation = system.actorOf(Props[VotingStation], "simpleVotingStation")

  val votesMap = Map[String, String](
    "Alice" -> "Martin",
    "Bob" -> "Roland",
    "Charlie" -> "Martin",
    "David" -> "Jonas",
    "Daniel" -> "Martin"
  )

  votesMap.keys.foreach {citizen =>
    votingStation ! Vote(citizen, votesMap(citizen))
  }

  votingStation ! "print"
}
