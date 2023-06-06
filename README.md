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