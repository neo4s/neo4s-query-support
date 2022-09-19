import Dependencies._

val scala212 = "2.12.16" // up to 2.12.16
val scala213 = "2.13.8"  // up to 2.13.8
val scala30  = "3.0.2"   // up to 3.0.2
val scala31  = "3.1.3"   // up to 3.1.3

ThisBuild / crossScalaVersions := Seq(scala213,scala212)
ThisBuild / scalaVersion     := crossScalaVersions.value.head
ThisBuild / version          := "0.1.2"
ThisBuild / organization     := "io.github.neo4s"
ThisBuild / organizationName := "Neo4s"

ThisBuild / versionScheme := Some("early-semver")

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

lazy val root = (project in file("."))
  .settings(
    name := "neo4s-query-support",
    libraryDependencies ++= Seq(
      logback,
      neo4jDriver,
      scalaLogging,
      scalaMock,
      scalaTest,
      shapeless,
    ),
    sonatypeProfileName := "io.github.neo4s",
    licenses := Seq("MIT" -> url("https://www.mit.edu/~amini/LICENSE.md")),
    homepage := Some(url("https://github.com/neo4s/neo4s-query-support")),
    pomIncludeRepository := (_ => false),
    scmInfo := Some(ScmInfo(url("https://github.com/neo4s/neo4s-query-support"),"scm:git:git@github.com:neo4s/neo4s-query-support.git")),
    developers := List(
      Developer(
        id = "thebrenthaines",
        name = "Brent Haines",
        email = "thebrenthaines@yahoo.com",
        url = url("https://github.com/neo4s")
      )
    ),
    publishMavenStyle := true,
    publishTo := sonatypePublishToBundle.value
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
