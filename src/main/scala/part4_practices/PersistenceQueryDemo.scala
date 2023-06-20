package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object PersistenceQueryDemo extends App {

  // actor system needs to be declared as implicit
  implicit val system = ActorSystem("PersistenceQueryDemo", ConfigFactory.load().getConfig("persistenceQuery"))

  //read journal
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // retrieve all persistence IDs - live - infinite source, will keep updating when more persistence Ids are added
  val persistenceIds = readJournal.persistenceIds()

  // to query a finite list of persistenceIds use the following

  val currentPersistenceIds = readJournal.currentPersistenceIds()


  //
  persistenceIds.runForeach{ persistenceIds =>
    println(s"Found persistence ID: $persistenceIds")
  }

//  currentPersistenceIds.runForeach { persistenceIds =>
//    println(s"1 - Found persistence ID: $persistenceIds")
//  }

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "persistence-query-id-1"

    override def receiveCommand: Receive =  {
      case m => persist(m) {_ =>
        log.info(s"Persisted: ${m}")
      }
    }

    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered: ${e}")
    }
  }

  val simpleActor = system.actorOf(Props[SimplePersistentActor], "simplePersistenceActor")

  import system.dispatcher
  system.scheduler.scheduleOnce(5 seconds) {
    val message = "hello a second time"
    simpleActor ! message
  }

  // Events by persistence ID
  val events = readJournal.eventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)
  events.runForeach { event =>
    println(s"Read event: $event")
  }

  // finite current
  val currentEvents = readJournal.currentEventsByPersistenceId("persistence-query-id-1", 0, Long.MaxValue)

  // events by tags
  val genres = Array("pop", "rock", "hip-hop", "jazz", "disco")
  case class Song(artist: String, title: String, genre: String)
  case class Playlist(songs: List[Song])

  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "music-store-checkout"

    var latestPlaylistId = 0

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPlaylistId, songs)) { - =>
          log.info(s"User purchased: $songs")
          latestPlaylistId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) =>
        log.info(s"Recovered: $event")
        latestPlaylistId = id
    }
  }


  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "musicStore"

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(event, genres)
      case event => event
    }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")
  val r = new Random
  for (_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", "My Love Song $i", randomGenre)
    }
    checkoutActor ! Playlist(songs.toList)
  }

  val rockPlaylists = readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlaylists.runForeach { event =>
    println(s"Found a playlist and with a rock song: $event")
  }
}
