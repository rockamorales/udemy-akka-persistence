package part1_recap

import akka.actor.{Actor, ActorSystem, PoisonPill, Props, Stash}
import akka.util.Timeout

import scala.language.postfixOps

object AkkaRecap extends App {
  class SimpleActor extends Actor with Stash{
    override def receive: Receive = {
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor], "myChild")
        childActor ! "hello"
      case "stashThis" =>
        stash()
      case "change" =>
        unstashAll()
        context.become(anotherHandler)
      case message => println(s"I received: $message")
    }

    def anotherHandler: Receive = {
      case message => println(s"In another receive handler: $message")
    }
  }

  // actor encapsulation
  val system = ActorSystem("AkkaRecap")

  // #1 you can only instantiate and actor via the actor system
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")

  // #2: sending messages
  actor ! "hello"

  /*
    Principles of the actor mechanism
      - messages are sent asyncrhonously
      - many actors (in the millions) can share a few dozen threads
      - each message is processed/handled ATOMICALLY
      - no need for locks
   */

  // Actors are not tied to a certain message handler
  // you can change the message handler using context.become

  // chaning actor behavior + stashing
  // the way this is done is by mixing in Stash trait

  //actors sapwn other actors

  // guardians: top level actors: /system, /user, / = root guardian

  // actors have a defined lifecycle: they can be started, resumed, stopped, suspended

  // stopping actors - context.stop
  actor ! PoisonPill

  // Logging - mixin trait ActorLogging

  // supervision

  // supervision strategy. e.g. OneForOneStrategy

  // configure akka infrastructure: dispatchers, routers, mailboxes

  // schedulers
  import scala.concurrent.duration._
  import system.dispatcher
  import akka.pattern.ask

  system.scheduler.scheduleOnce(2 seconds) {
    // logic
  }

  // akka patterns including FSM and ask pattern
  implicit val timeout = Timeout(3 seconds)
  val future = actor ? "question"

  // the pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor) // when the future completes a message will be send to anotherActor

}
