import sbt._

object Dependencies {

  lazy val sttpCore = "com.softwaremill.sttp" %% "core" % sttpVersion
  def playWs(playVersion: String) = "com.typesafe.play" %% "play-ws" % playVersion
  def playAhcWs(playVersion: String) = "com.typesafe.play" %% "play-ahc-ws" % playVersion

  def playWsDependencies(playVersion: String) = Seq(playWs(playVersion), playAhcWs(playVersion))

  lazy val scalatest = "org.scalatest" %% "scalatest" % scalatestVersion

  // Akka hhttp deps
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  lazy val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion
  lazy val akkaHttpCors = "ch.megard" %% "akka-http-cors" % akkaHttpCorsVersion

  val sttpVersion = "1.5.17"
  val play26Version = "2.6.23"
  val play27Version = "2.7.2"

  val scalatestVersion = "3.0.5"
  val akkaHttpCorsVersion = "0.4.0"
  val akkaHttpVersion = "10.1.8"
  val akkaStreamsVersion = "2.5.19"
  val kindProjectorVersion = "0.9.7"

}
