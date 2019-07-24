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

val zioVersion    = "1.0.0-RC10-1"
val specs2Version = "4.6.0"
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

lazy val microsite = project
  .enablePlugins(MicrositesPlugin)
  .settings(
    scalacOptions -= "-Yno-imports",
    scalacOptions ~= { _.filterNot(_.startsWith("-Ywarn")) },
    scalacOptions ~= { _.filterNot(_.startsWith("-Xlint")) },
    skip in publish := true,
    libraryDependencies ++= Seq(
      "com.github.ghik" %% "silencer-lib" % "1.3.1",
      "commons-io"      % "commons-io"    % "2.6"
    ),
    micrositeFooterText := Some(
      """
        |<p>&copy; 2019 <a
        href="https://github.com/zio/zio-actors">zio-actors Maintainers</a></p> |""".stripMargin
    ),
    micrositeName := "ZIO Actors",
    micrositeDescription := "A high-performance, purely-functional library for building, composing, and supervising typed actors based on ZIO",
    micrositeAuthor := "zio-actors contributors",
    micrositeOrganizationHomepage := "https://github.com/zio/zio-actors",
    micrositeGitterChannelUrl := "zio/zio-actors",
    micrositeGitHostingUrl := "https://github.com/zio/zio-actors",
    micrositeGithubOwner := "zio",
    micrositeGithubRepo := "zio-actors",
    micrositeFavicons := Seq(microsites.MicrositeFavicon("favicon.png", "512x512")),
    micrositeDocumentationUrl := s"https://javadoc.io/doc/org.scalaz/scalaz-actors_2.12/${(version in Compile).value}",
    micrositeDocumentationLabelDescription := "Scaladoc",
    micrositeBaseUrl := "/zio-actors",
    micrositePalette := Map(
      "brand-primary"   -> "#990000",
      "brand-secondary" -> "#000000",
      "brand-tertiary"  -> "#990000",
      "gray-dark"       -> "#453E46",
      "gray"            -> "#837F84",
      "gray-light"      -> "#E3E2E3",
      "gray-lighter"    -> "#F4F3F4",
      "white-color"     -> "#FFFFFF"
    ),
    micrositeCompilingDocsTool := WithMdoc
  )
