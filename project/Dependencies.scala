import sbt._

object Dependencies {
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.4.0"
  lazy val neo4jDriver = "org.neo4j.driver" % "neo4j-java-driver" % "4.3.4"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
  lazy val scalaMock = "org.scalamock" %% "scalamock" % "5.1.0" % Test
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.10" % Test
  lazy val shapeless = "com.chuusai" %% "shapeless" % "2.3.7"
}
