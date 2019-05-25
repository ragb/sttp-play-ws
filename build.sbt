import Dependencies._

lazy val root: Project = (project in file("."))
  .settings(
    publishArtifact := false,
    publishLocal := {},
    publish := {}
  )
  .aggregate(play26Project, play27Project)

val commonSettings: Seq[Def.Setting[_]] = inThisBuild(
  List(
    organization := "com.ruiandrebatista",
    scalaVersion := "2.12.8",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    organizationName := "Rui Batista",
      startYear := Some(2019),
    licenses := Seq(("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")))
  )) ++ Seq(
  scalaSource in Compile := (LocalProject("root") / baseDirectory).value / "common" / "src" / "main" / "scala",
  scalaSource in Test := (LocalProject("root") / baseDirectory).value / "common" / "src" / "test" / "scala",
  libraryDependencies ++= Seq(
    sttpCore,
    (sttpCore classifier "tests") % Test,
    scalatest % Test,
    akkaHttp % Test,
    akkaStreams % Test,
    akkaHttpCors % Test
  ),
  addCompilerPlugin("org.spire-math" % "kind-projector" % kindProjectorVersion cross CrossVersion.binary),
  git.useGitDescribe := true
)

def sttpPlayWsProject(playVersion: String, sufix: String, id: String) =
  Project(id = id, base = file(id))
    .settings(commonSettings: _*)
    .settings(
      name := s"play-ws-$sufix",
      libraryDependencies ++= playWsDependencies(playVersion)
    )
.enablePlugins(GitVersioning)

lazy val play26Project = sttpPlayWsProject(play26Version, "26", "play26")
lazy val play27Project = sttpPlayWsProject(play27Version, "27", "play27")



