package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.google.protobuf.Message

import java.util.Date

object PersistenceActors extends App {
  /*
    Scenario: we have a business and an accountant which keeps track of our invoices
   */

  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])
  case object Shutdown

  class Accountant extends PersistentActor with ActorLogging {
    var latestInvoiceId = 0
    var totalAmount = 0
    override def persistenceId: String = "simple-accountant" // best practice: make it unique

    /**
     * the normal recieve method
     * @return
     */
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
          Pattern goes like this:
            when you receive a command
            1) create and EVENT to persist into the store
            2) you persist the event, then pass in a callback that will get triggered once the event is written
            3) update the actor state when the event has persisted
         */
        log.info(s"Recieve invoice foramount: $amount")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        persist(event)
        /* There is a time gap between the call to persist and the callback execution. What happens if any messages arrive during this time
        * ? --> They are STASHED */
        { e =>
          // update state
          // SAFE to access mutable state here
          latestInvoiceId += 1
          totalAmount += amount
//          sender() ! "PersistenceACK"
          log.info(s"Persisted ${e} as invoice #${e.id}, for total amount $totalAmount")
        }
      case InvoiceBulk(invoices) =>
        /*
          1) create events (plural)
          2) persist all the events
          3) update the actor state when each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }

        persistAll(events) { e =>
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(s"Persisted SINGLE $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case Shutdown =>
        context.stop(self)
        // you are not obliged to persist any event. you can act like a normal actor
      case "print" =>
        log.info(s"Latest invoice id: $latestInvoiceId, total amount: $totalAmount")

    }

    /*
      Handlers that will be called on recovery
     */
    override def receiveRecover: Receive = {
      /*
         Best practice: follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id, _, _, amount) =>
        log.info(s"Recovered invoice #$id for amount $amount, total amount $totalAmount")
        latestInvoiceId = id
        totalAmount += amount
    }

    /*
    This method is called if persisting failed.
    The actor will be STOPPED
    Best practice: start the actor again after a while.
    (use Backoff supervisor)
     */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.info(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

//  for (i <- 1 to 10) {
//    accountant ! Invoice("The Sofa Company", new Date, i * 1000)
//  }


  /*
  Persistence failures
   */

  /*
    Persisting multiple events
    persistAll
   */

  val newInvoices = for (i <- 1 to 5 ) yield Invoice("The awesome chairs", new Date, i * 2000)
  accountant ! InvoiceBulk(newInvoices.toList)

  /*
    NEVER EVER CALL PERSIST OR PERSIST ALL FROM FUTURE

   */

  /**
   * SHUTDOWN of persistent actors
   * dont SHUTDOWN using PoisonPill, you risk losing data
   *
   * Best practice, define your own shutdown mechanism
   */

  accountant ! Shutdown


}
