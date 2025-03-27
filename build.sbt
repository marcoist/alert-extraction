import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "Alert Extractor",
    scalaVersion := "2.13.11",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.36",
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
      "io.circe" %% "circe-core" % "0.14.3",
      "io.circe" %% "circe-generic" % "0.14.3",
      "io.circe" %% "circe-parser" % "0.14.3",
      "com.github.pathikrit" %% "better-files" % "3.9.1",
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
      "org.mockito" % "mockito-core" % "5.3.1" % Test,
      "com.softwaremill.sttp.client3" %% "core" % "3.9.0" % Test,
      "com.softwaremill.sttp.client3" %% "slf4j-backend" % "3.9.0" % Test
    ),
    libraryDependencies += munit % Test
  )