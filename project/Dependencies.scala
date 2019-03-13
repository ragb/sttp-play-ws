import sbt._

object Dependencies {

  lazy val sttpCore = "com.softwaremill.sttp" %% "core" % sttpVersion
  lazy val playWs = "com.typesafe.play" %% "play-ws" % playVersion
  lazy val playAhcWs = "com.typesafe.play" %% "play-ahc-ws" % playVersion
  lazy val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion

  // Akka hhttp deps
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  lazy val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion
  lazy val akkaHttpCors = "ch.megard" %% "akka-http-cors" % akkaHttpCorsVersion

  val sttpVersion = "1.5.11"
  val playVersion = "2.6.17"

  val scalatestVersion = "3.0.5"
  val akkaHttpCorsVersion = "0.3.3"
  val akkaHttpVersion = "10.1.7"
  val akkaStreamsVersion = "2.5.19"
  val kindProjectorVersion = "0.9.7"

}
