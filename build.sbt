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

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

val zioVersion            = "2.0.3"
val zioNioVersion         = "2.0.0"
val zioConfigVersion      = "3.0.2"
val zioInteropCatsVersion = "22.0.0.0"
val akkaActorTypedVersion = "2.6.19"
val doobieVersion         = "0.13.4"

lazy val root =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(zioActors, zioActorsPersistence, zioActorsPersistenceJDBC, examples, zioActorsAkkaInterop)

lazy val zioActors = module("zio-actors", "actors")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("zio.actors"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio"                 % zioVersion,
      "dev.zio"       %% "zio-test"            % zioVersion % "test",
      "dev.zio"       %% "zio-test-sbt"        % zioVersion % "test",
      "dev.zio"       %% "zio-nio"             % zioNioVersion,
      "dev.zio"       %% "zio-config-typesafe" % zioConfigVersion,
      "org.scala-lang" % "scala-reflect"       % scalaVersion.value
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .settings(
    stdSettings("zio-actors")
  )

lazy val zioActorsPersistence = module("zio-actors-persistence", "persistence")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors)

lazy val zioActorsPersistenceJDBC = module("zio-actors-persistence-jdbc", "persistence-jdbc")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"      %% "zio-test"         % zioVersion % "test",
      "dev.zio"      %% "zio-test-sbt"     % zioVersion % "test",
      "dev.zio"      %% "zio-interop-cats" % zioInteropCatsVersion,
      "org.tpolecat" %% "doobie-core"      % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"    % doobieVersion,
      "org.tpolecat" %% "doobie-postgres"  % doobieVersion
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActorsPersistence)

lazy val examples = module("zio-actors-examples", "examples")
  .settings(
    publish / skip := true,
    fork := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors, zioActorsPersistence, zioActorsPersistenceJDBC)

lazy val zioActorsAkkaInterop = module("zio-actors-akka-interop", "akka-interop")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio-test"         % zioVersion % "test",
      "dev.zio"           %% "zio-test-sbt"     % zioVersion % "test",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaActorTypedVersion
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors)

def module(moduleName: String, fileName: String): Project =
  Project(moduleName, file(fileName))
    .settings(stdSettings(moduleName))
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio" % zioVersion
      )
    )

lazy val docs = project
  .in(file("zio-actors-docs"))
  .settings(
    publish / skip := true,
    moduleName := "zio-actors-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(root),
    //unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(root),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    //target in (ScalaUnidoc, unidoc) := ( in LocalRootProject).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    //cleanFiles += (target in (ScalaUnidoc, unidoc)).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(zioActors, zioActorsPersistence, zioActorsAkkaInterop)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
