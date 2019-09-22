import Build._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.github.io/zio-actors/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

pgpPublicRing := file("/tmp/public.asc")
pgpSecretRing := file("/tmp/secret.asc")
releaseEarlyWith := SonatypePublisher
scmInfo := Some(
  ScmInfo(url("https://github.com/zio/zio-actors/"), "scm:git:git@github.com:zio/zio-actors.git")
)

val zioVersion    = "1.0.0-RC13"
val specs2Version = "4.7.1"
libraryDependencies ++= Seq(
  "dev.zio"    %% "zio"                  % zioVersion,
  "org.specs2" %% "specs2-core"          % specs2Version % "test",
  "org.specs2" %% "specs2-scalacheck"    % specs2Version % Test,
  "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test
)

lazy val root =
  (project in file("."))
    .settings(
      stdSettings("zio-actors")
    )

lazy val docs = project
  .in(file("zio-actors-docs"))
  .settings(
    skip.in(publish) := true,
    moduleName := "zio-actors-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    )
  )
  .dependsOn(root)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
