version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.12"
val zioVersion = "2.0.0"

lazy val root = (project in file("."))
  .settings(
    name := "database"
  )

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "org.slf4j" % "slf4j-api" % "2.0.17",
  "ch.qos.logback" % "logback-classic" % "1.5.18",

  "dev.zio" %% "zio" % "2.0.13",
  "dev.zio" %% "zio-json" % "0.5.0",
  "dev.zio" %% "zio-streams" % "2.0.13",
  "io.d11" %% "zhttp" % "2.0.0-RC10",
)

libraryDependencies ++= Seq(
  "dev.zio"               %% "zio-test"                  % zioVersion  % Test,
  "dev.zio"               %% "zio-test-sbt"              % zioVersion  % Test
)