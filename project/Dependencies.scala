import sbt._

object Dependencies {

  val akkaVersion = "2.5.14"
  val akka = Seq(
    "com.typesafe.akka" %% "akka-testkit" % "2.5.14" % Test,
    "com.typesafe.akka" %% "akka-http" % "10.1.3",
    "com.typesafe.akka" %% "akka-stream" % akkaVersion
  )

  val circeVersion = "0.9.3"
  val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % circeVersion)

  val monixVersion = "3.0.0-RC1"
  val monix = Seq(
    "io.monix" %% "monix" % monixVersion
  )

  val logging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "ch.qos.logback" % "logback-core" % "1.2.3"
  )

  val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    "org.mockito" % "mockito-core" % "2.21.0" % Test
  )

  val all = akka ++ circe ++ monix ++ logging ++ scalaTest

}
