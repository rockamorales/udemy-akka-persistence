# Akka Persistence

## The Why
Long term backends have persistence
- databases
- files S3 buckets, HDFS ...

Scenario: Akka actors interacting with a database

Problems
- How do you query a previous state ?
- How did you arrive to this state

Examples
- tracing orders in an online store
- transaction history in a bank
- chat messages
- document versioning in a Dropbox-like system

## Event sourcing
Online store: Asking for all the data about an order

- traditional relation model will have only the last state
- a much richer description track all events about the order
- Instead of storing the current state, we will store events.
- We can always recreate the current state by replaying the events

## A new mental model
Pros:
- high performance: events are only appended
- avoids relatioal store and ORM entirely
- full trace of every state
- fits the Akka actor model perfectly

Cons:
- querying a state potentially expensive <------ Akka Persistence Query
- potencial performance issues with long-lived entities - snapshotting
- data model subject to change <---------- Schema evolution
- just a very different model


## Persistent Actors 
Can do everything a normal actor can do
- send and receive messages 
- hold internal state
- run in parallel with many other actors

Extra capabilities
- have persistence ID
- persist events to a long-term store
- recover state by replaying events from the store

When an actor handler a message command
- it can (asynchronously) persist an event to the store
- after the event is persisted, it changes its internal state
- it replays all events with its persistence ID

#### Persistence is based on messages
- think of persisting as sending message to the journal

#### Calls to persist() are executed in order
#### Handlers for subsequent persist() calls are executed in order
#### A nested persist are executed after their enclosing persist

# Snapshots
Problem: long-lived entities take a long time to recover
Solution: save checkpoints

- Save the entire state as checkpoints (snapshots)
- recover the last snapshot + events since then

```scala
  saveSnapshot(state)
  case SnapshotOffer(metadata, contents) => state = contents
```

### Saving snapshots
- dedicated store
- asynchronous
- can fail, but no big deal


# Recovery
- Messages (commands) sent during recovery are stashed
- if recovery failes, onRecoveryFailure is called and the actor is stopped
- customizing recovery `override def recovery: Recovery = Recovery(fromSequenceNbr = 100`
- disable recovery `override def recovery: Recovery = Recovery.none`
- Get a signal when recovery is completed `case RecoveryCompleted => `
- Stateless actors
  - use context.becme in receiveCommand (like normal actors)
  - also fine in receiveRecover, but the last handler will be used, and after recovery

# persistAsync
- High throughput use-cases
- Relaxed event ordering guarantees

### Guarantees
- persist calls happen in order
- persist callbacks are called in order
- no other guarantees: new messages may be handled in the time gaps

Bonus: Mix persistAsync with command sourcing


# Local Store: LevelDB
- file-based key-value store
- compaction
- generally not suited for production

```scala
    akka {
      persistence {
        journal {
          plugin = "akka.persistence.journal.leveldb"
          leveldb.dir = "target/localStores/journal"
          leveldb.compaction-intervals {
            simple-persistent-actor = 1000
            "*" = 5000
          }
        }
      }
      actor {
        allow-java-serialization = on
      }
    }
```

## Local snapshot store
- file-based
- can write anything serializable
```scala
    akka {
      persistence {
        snapshot - store {
          plugin = "akka.persistence.snapshot-store.local"
          local.dir = "target/localStores/snapshots"
        }
      }
    }
```

# Postgres Store
Add the plugin dependencies in build.sbt
```scala
"com.lightbend.akka" %% "akka-persistence-jdbc" % "5.2.1",
"org.postgresql" % "postgresql" % "42.6.0"
```

Configure in application.conf: use the code are reference

to work with docker, check docker-compose.yaml file

# lATEST Postgres configuration requires the following schema

https://github.com/akka/akka-persistence-jdbc/blob/v5.2.1/core/src/main/resources/schema/postgres/postgres-create-schema.sql


# Cassandra

### Distributed database
- high availability
- high throughput

### Costs
- eventual consistency
- soft deletes and tombstones

## Steps to enable cassandra store
Add the plugin dependencies in build.sbt
```scala
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.1.1",
    "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "1.1.1" % Test
```

Configure application.conf (I'm using a newer version, so configuration is slightly different from the suggested in the course)
NOTE: keyspace-autocreate and tables-autocreate are not recomended to be used for production
```scala
    akka {
        persistence {
            journal {
                plugin = "akka.persistence.cassandra.journal"
            }
            snapshot-store{
                plugin = "akka.persistence.cassandra.snapshot"
            }
            cassandra {
                snapshot {
                    keyspace-autocreate = true
                    tables-autocreate = true
                }
                journal {
                    keyspace-autocreate = true
                    tables-autocreate = true
                }
            }
        }
    }
```

To work with docker check docker-compose.yaml

# Serialization
- Java serialization is used by default
- Serializartion = turning in-memory objects into a recognizable format
- Java serialization is not idea
  - memory consumption
  - speed / performance
- Many Akka serializers are out of date

## Defining a customSerializer
- extends Serializer
- implement following methods
```scala
class UserRegistrationSerializer extends Serializer {
  val SEPARATOR = "//"
  override def identifier: Int = 53278

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event @ UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id$SEPARATOR$email$SEPARATOR$name]".getBytes()
    case _ => throw new IllegalArgumentException("Only user registration events supported in this serializer")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split(SEPARATOR)
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)
    val result = UserRegistered(id, email, name)
    println(s"Deserialized $string to $result")
    result
  }

  override def includeManifest: Boolean = false
}
```

## Configure application.conf
```scala
akka {
  actor {
    serializers {
      java = "akka.serialization.JavaSerializer"
      rtjvm = "part3_stores_serialization.UserRegistrationSerializer"
    }

    serialization - bindings {
      "part3_stores_serialization.UserRegistered" = rtjvm
    }
  }
}
```

# Schema Evolution

as our app evolves we may need to change the events structure
- What do we do with already persisted data?
- how do persist new data with the new Schema?

Schema versioning and event adapters
Step 1: Create the new event version with any changes to the schema
```scala
case class MyEventV2(...)
```

Step 2: Define an event adapter

```scala
class MyEventAdapter extends EventAdapter {
  def fromJournal(event: Any, manifest: String): EventSeq
  def toJournal(event: Any, manifest: String): Any
  def manifest(event: Any): String
}
```

Step 3:  Configuration (should be tied to the journal plugin)

```scala
    akka {
        persistence {
          ...
            cassandra {
              ...
                journal {
                    event-adapters {
                        guitar-inventory-enhancer = "part4_practices.EventAdapters$GuitarReadEventAdapter"
                    }

                    event-adapter-bindings {
                        "part4_practices.EventAdapters$GuitarAdded" = guitar-inventory-enhancer
                    }
                            ...
                }
            }
        }
                ...
    }
```


# Detaching domain model from the Data Model
Consist on separating the domain model from the data model
- domain model = events our actor thinks it persists
- data model = objects which actually get persisted
- good practice: make the two models independent
- side effect: easier schema evolution


### Benefits
- Persistence is transparent to the actor
- schema evolution done in the adapter only


# Persistence Query
Persistent stores are also used for reading data
Queries: 
- select persistence IDs
- select events by persistence ID
- select events across persistence IDs, by tags

User cases:
- which persistence actors are active
- recreate older states
- track how we arrived to the current state
- data processing on events in the entire store


