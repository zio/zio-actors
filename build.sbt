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

val zioVersion            = "1.0.3"
val zioNioVersion         = "1.0.0-RC9"
val zioConfigVersion      = "1.0.0-RC30-1"
val zioInteropCatsVersion = "2.2.0.1"
val akkaActorTypedVersion = "2.6.10"
val doobieVersion         = "0.9.4"

lazy val root =
  project
    .in(file("."))
    .settings(skip in publish := true)
    .aggregate(
      zioActors.jvm,
      zioActors.js,
      zioActorsPersistence,
      zioActorsPersistenceJDBC,
      examples,
      zioActorsAkkaInterop
    )

lazy val zioActors =
  crossProject(JVMPlatform, JSPlatform)
    .withoutSuffixFor(JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("actors"))
    .enablePlugins(BuildInfoPlugin)
    .settings(buildInfoSettings("zio.actors"))
    .settings(
      name := "zio-actors",
      libraryDependencies ++= Seq(
        "dev.zio"      %%% "zio"           % zioVersion,
        "dev.zio"      %%% "zio-test"      % zioVersion % "test",
        "dev.zio"      %%% "zio-test-sbt"  % zioVersion % "test",
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
    )
    .settings(
      stdSettings("zio-actors")
    )

    .jvmSettings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-nio"             % zioNioVersion,
        "dev.zio" %% "zio-config-typesafe" % zioConfigVersion
      )
    )

lazy val zioActorsPersistence = module("zio-actors-persistence", "persistence")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors.jvm)

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
    skip in publish := true,
    fork := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors.jvm, zioActorsPersistence, zioActorsPersistenceJDBC)

lazy val zioActorsAkkaInterop = module("zio-actors-akka-interop", "akka-interop")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"           %% "zio-test"         % zioVersion % "test",
      "dev.zio"           %% "zio-test-sbt"     % zioVersion % "test",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaActorTypedVersion
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors.jvm)

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
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(unidoc in Compile).value
  )
  .dependsOn(zioActors.jvm, zioActorsPersistence, zioActorsAkkaInterop)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
