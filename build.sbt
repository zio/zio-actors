import BuildHelper._

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
      ),
      Developer(
        "softinio",
        "Salar Rahmanian",
        "code@softinio.com",
        url("https://www.softinio.com")
      )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio-actors/"), "scm:git:git@github.com:zio/zio-actors.git")
    )
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

val zioVersion    = "1.0.0-RC17"
val zioNioVersion = "0.4.0"
libraryDependencies ++= Seq(
  "dev.zio"        %% "zio"          % zioVersion,
  "dev.zio"        %% "zio-nio"      % zioNioVersion,
  "dev.zio"        %% "zio-test"     % zioVersion % "test",
  "dev.zio"        %% "zio-test-sbt" % zioVersion % "test",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

lazy val root =
  (project in file("."))
    .settings(
      stdSettings("zio-actors")
    )
    .settings(buildInfoSettings("zio.actors"))
    .enablePlugins(BuildInfoPlugin)

lazy val docs = project
  .in(file("zio-actors-docs"))
  .settings(
    skip.in(publish) := true,
    moduleName := "zio-actors-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    ),
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(root),
    target in (ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
    cleanFiles += (target in (ScalaUnidoc, unidoc)).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(unidoc in Compile).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(unidoc in Compile).value,
  )
  .dependsOn(root)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
