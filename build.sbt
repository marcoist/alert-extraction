import Dependencies._

ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "Alert Extractor",
    scalaVersion := "2.13.16",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "2.0.17",
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "com.softwaremill.sttp.client3" %% "core" % "3.10.3",
      "com.softwaremill.sttp.client3" %% "circe" % "3.10.3",
      "io.circe" %% "circe-core" % "0.14.12",
      "io.circe" %% "circe-generic" % "0.14.12",
      "io.circe" %% "circe-parser" % "0.14.12",
      "com.github.pathikrit" %% "better-files" % "3.9.2",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.mockito" % "mockito-core" % "5.16.1" % Test,
      "com.softwaremill.sttp.client3" %% "slf4j-backend" % "3.10.3" % Test
    ),
    libraryDependencies += munit % Test
  )