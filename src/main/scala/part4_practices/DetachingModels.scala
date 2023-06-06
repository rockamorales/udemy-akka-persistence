package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DataModel {
  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)
  case class WrittenCouponAppliedV2(code: String, userId: String, userEmail: String, userName: String)
}
object DomainModel {
  case class User(name: String, id: String, email: String)
  case class Coupon(code: String, promotionAmount: Int)

  // command
  case class ApplyCoupon(coupon: Coupon, user: User)

  //event
  case class CouponApplied(code: String, user: User)
}

object DetachingModels extends App {
  import DomainModel._
  class CouponManager extends PersistentActor with ActorLogging {
    val coupons: mutable.Map[String, User] = new mutable.HashMap[String, User] ()

    override def persistenceId: String = "coupon-manager"

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code)) {
          persist(CouponApplied(coupon.code, user)) { e =>
            log.info(s"Persisted $e")
            coupons.put(coupon.code, user)
          }
        }
    }

    override def receiveRecover: Receive = {
      case event @ CouponApplied(code, user) =>
        log.info(s"Recovered $event")
        coupons.put(code, user)
    }
  }

  val system = ActorSystem("DetachingModels", ConfigFactory.load().getConfig("detachingModels"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")

//  for (i <- 6 to 10) {
//    val coupon = Coupon(s"MEGA_COUPON_$i", 100)
//    val user = User(s"$i", s"user_$i@rtjvm.com", s"$i")
//    couponManager ! ApplyCoupon(coupon, user)
//  }
}

class ModelAdapter extends EventAdapter {
  import DomainModel._
  import DataModel._
  // actor -> toJournal -> serializer -> to the actor
  override def toJournal(event: Any): Any = event match {
    case event @ CouponApplied(code, user) =>
      println(s"Converting $event to DATA model")
      WrittenCouponAppliedV2(code, user.id, user.email, user.name)
  }

  // journal -> serializer -> fromJournal -> to the actor
  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case event@WrittenCouponApplied(code, userId, userEmail) =>
      println(s"Converting $event to DOMAIN model")
      EventSeq.single(CouponApplied(code, User(userId, userEmail, "Unnamed")))
    case event@WrittenCouponAppliedV2(code, userId, userEmail, userName) =>
      println(s"Converting $event to DOMAIN model")
      EventSeq.single(CouponApplied(code, User(userId, userEmail, userName)))
    case other =>
      EventSeq.single(other)
  }

  override def manifest(event: Any): String = "CMA"
}
