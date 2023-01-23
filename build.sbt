import BuildHelper._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.dev/zio-actors/")),
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

val zioVersion            = "2.0.2"
val zioNioVersion         = "2.0.0"
val zioConfigVersion      = "3.0.2"
val zioInteropCatsVersion = "22.0.0.0"
val zioCatsInteropVersion = "3.3.0"
val akkaActorTypedVersion = "2.6.19"
val doobieVersion         = "0.13.4"
val shardcakeVersion      = "2.0.3"
val testContainersVersion = "0.40.9"

lazy val root =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(zioActors, zioActorsPersistence, zioActorsPersistenceJDBC, examples, zioActorsAkkaInterop, docs)

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
  .dependsOn(zioActors, zioActorsAkkaInterop, zioActorsPersistence, zioActorsPersistenceJDBC)

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

lazy val zioActorsSharding = module("zio-actors-sharding", "sharding")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"        %% "zio-test"                     % zioVersion            % "test",
      "dev.zio"        %% "zio-test-sbt"                 % zioVersion            % "test",
      "com.dimafeng"   %% "testcontainers-scala-core"    % testContainersVersion % "test",
      "dev.zio"        %% "zio-interop-cats"             % zioCatsInteropVersion,
      "com.devsisters" %% "shardcake-core"               % shardcakeVersion,
      "com.devsisters" %% "shardcake-entities"           % shardcakeVersion,
      "com.devsisters" %% "shardcake-manager"            % shardcakeVersion,
      "com.devsisters" %% "shardcake-health-k8s"         % shardcakeVersion,
      "com.devsisters" %% "shardcake-protocol-grpc"      % shardcakeVersion,
      "com.devsisters" %% "shardcake-serialization-kryo" % shardcakeVersion,
      "com.devsisters" %% "shardcake-storage-redis"      % shardcakeVersion
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .dependsOn(zioActors, zioActorsPersistence)

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
    moduleName := "zio-actors-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq("dev.zio" %% "zio" % zioVersion),
    projectName := "ZIO Actors",
    mainModuleName := (zioActors / moduleName).value,
    projectStage := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      zioActors,
      zioActorsPersistence,
      zioActorsPersistenceJDBC,
      zioActorsAkkaInterop
    ),
    docsPublishBranch := "master"
  )
  .dependsOn(zioActors, zioActorsPersistence, zioActorsAkkaInterop)
  .enablePlugins(WebsitePlugin)
