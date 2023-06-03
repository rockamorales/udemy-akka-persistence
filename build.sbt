ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.11"
lazy val akkaVersion = "2.8.2"
lazy val slickVersion = "3.4.1"

lazy val root = (project in file("."))
  .settings(
    name := "udemy-akka-persistance",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
      "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.2.1",
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "org.postgresql" % "postgresql" % "42.6.0",
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.1.1",
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "1.1.1" % Test,
      "com.google.protobuf" % "protobuf-java" % "3.23.2"
    )
  )
