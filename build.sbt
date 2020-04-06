
val AkkaVersion = "2.6.4"
val AkkaHttpVersion = "10.1.11"
val LogbackVersion = "1.2.3"

lazy val buildSettings = Seq(
  organization := "com.lightbend.akka.samples",
  scalaVersion := "2.13.1"
)

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-unused:imports",
  "-encoding", "UTF-8"
)

lazy val commonJavacOptions = Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

lazy val commonSettings = Seq(
  Compile / scalacOptions ++= commonScalacOptions,
  Compile / javacOptions ++= commonJavacOptions,
  run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m"),
  run / fork := false,
  Global / cancelable := false,
)

lazy val rms = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    mainClass in(Compile, run) := Some("sample.rms.rms"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka" %% "akka-distributed-data" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.1.1" % Test
    )
  )

// Startup aliases for the first two seed nodes and a third, more can be started.
addCommandAlias("sharding1", "runMain sample.rms.rms 2551")
addCommandAlias("sharding2", "runMain sample.rms.rms 2552")
addCommandAlias("sharding3", "runMain sample.rms.rms 0")
