package com.rp.rms

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, ClusterSingleton, SingletonActor}
import akka.http.scaladsl.server.Directives._
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.rp.rms.reader.{EventProcessorSettings, ReadRoutes, ReadStore}
import com.rp.rms.writer.{Business, Connection, WriteRoutes}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    args.headOption match {

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt
        val httpPort = ("80" + portString.takeRight(2)).toInt
        startNode(port, httpPort)

      case Some("cassandra") =>
        startCassandraDatabase()
        println("Started Cassandra, press Ctrl + C to kill")
        new CountDownLatch(1).await()

      case None =>
        throw new IllegalArgumentException("port number, or cassandra required argument")
    }
  }

  def startNode(port: Int, httpPort: Int): Unit = {
    val system = ActorSystem[Nothing](Guardian(), "RMS", config(port, httpPort))

    if (Cluster(system).selfMember.hasRole("read-model")) {
      createTables(system)
    }
  }

  def config(port: Int, httpPort: Int): Config =
    ConfigFactory.parseString(
      s"""
      akka.remote.artery.canonical.port = $port
      rms.http.port = $httpPort
       """).withFallback(ConfigFactory.load())

  /**
   * To make the sample easier to run we kickstart a Cassandra instance to
   * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
   * in a real application a pre-existing Cassandra cluster should be used.
   */
  def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }

  def createTables(system: ActorSystem[_]): Unit = {
    val session = CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

    // TODO use real replication strategy in real application
    val keyspaceStmt =
      """
      CREATE KEYSPACE IF NOT EXISTS rms
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
      """

    val offsetTableStmt =
      """
      CREATE TABLE IF NOT EXISTS rms.offsetStore (
        eventProcessorId text,
        tag text,
        timeUuidOffset timeuuid,
        PRIMARY KEY (eventProcessorId, tag)
      )
      """

    // ok to block here, main thread
    Await.ready(session.executeDDL(keyspaceStmt), 30.seconds)
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
  }

}

object Guardian {
  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      val system = context.system
      val settings = EventProcessorSettings(system)
      val httpPort = context.system.settings.config.getInt("rms.http.port")

      Connection.init(system, settings)
      Business.init(system, settings)

      val singletonManager = ClusterSingleton(system)
      val proxy: ActorRef[ReadStore.Command] = singletonManager.init(
        SingletonActor(Behaviors.supervise(ReadStore(system)).onFailure[Exception](SupervisorStrategy.restart), "ReadStore"))
      proxy ! ReadStore.Start

      val readRoutes = new ReadRoutes(proxy)(context.system)
      val writeRoutes = new WriteRoutes()(context.system)


      new RMSServer(concat(writeRoutes.writeRoutes, readRoutes.routes), httpPort, context.system).start()

      Behaviors.empty
    }
  }

}
