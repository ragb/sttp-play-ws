lazy val root: Project = (project in file("."))
  .settings(
    publishArtifact := false,
    publishLocal := {},
    publish := {}
  )
  .aggregate(
    play26Project,
    play27Project,
    play28Project
  )

val commonSettings: Seq[Def.Setting[_]] = inThisBuild(
  List(
    organization := "com.ruiandrebatista",
    scalaVersion := "2.12.11",
    organizationName := "Rui Batista",
    startYear := Some(2018),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/ragb/sttp-play-ws")),
    developers := List(
      Developer(
        "ragb",
        "Rui Batista",
        "ruiandrebatista@gmail.com",
        url("http://www.ruiandrebatista.com")
      )
    )
  )
) ++ Seq(
  scalaSource in Compile := (LocalProject("root") / baseDirectory).value / "common" / "src" / "main" / "scala",
  scalaSource in Test := (LocalProject("root") / baseDirectory).value / "common" / "src" / "test" / "scala",
  unmanagedResourceDirectories in Test ++= Seq(
    (LocalProject("root") / baseDirectory).value / "common" / "src" / "test" / "resources"
  ),
  fork in Test := true,
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC2",
    ("com.softwaremill.sttp.client" %% "core" % "2.0.0-RC2" classifier "tests") % Test,
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    "com.typesafe.akka" %% "akka-http" % "10.1.8" % Test,
    "com.typesafe.akka" %% "akka-stream" % "2.5.31" % Test,
    "ch.megard" %% "akka-http-cors" % "0.4.2" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3" % Test
  )
)

lazy val play26Project = Project("play26", file("play26"))
  .settings(commonSettings)
  .settings(
    name := "sttp-play-ws-26",
    crossScalaVersions := Seq("2.11.12", "2.12.11"),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % "2.6.23",
      "com.typesafe.play" %% "play-ahc-ws" % "2.6.23"
    )
  )

lazy val play27Project = Project("play27", file("play27"))
  .settings(commonSettings)
  .settings(
    name := "sttp-play-ws-27",
    crossScalaVersions := Seq("2.11.12", "2.12.11", "2.13.2"),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % "2.7.5",
      "com.typesafe.play" %% "play-ahc-ws" % "2.7.5"
    )
  )

lazy val play28Project = Project("play28", file("play28"))
  .settings(commonSettings)
  .settings(
    name := "sttp-play-ws-28",
    crossScalaVersions := Seq("2.12.10", "2.13.2"),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws" % "2.8.2",
      "com.typesafe.play" %% "play-ahc-ws" % "2.8.2"
    )
  )
