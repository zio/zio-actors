import Scalaz._

name := "scalaz-actors"

inThisBuild(
  List(
    organization := "org.scalaz",
    homepage := Some(url("https://scalaz.github.io/scalaz-actors/")),
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

val scalazVersion = "7.2.26"
val testzVersion  = "0.0.5"
val zioVersion    = "0.6.0"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core"  % scalazVersion,
  "org.scalaz" %% "scalaz-zio"   % zioVersion,
  "org.scalaz" %% "testz-stdlib" % testzVersion
)

lazy val root =
  (project in file("."))
    .settings(
      stdSettings("actors")
    )
    .aggregate(
      microsite
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
        href="https://github.com/scalaz/scalaz-actors">Scalaz-actors Maintainers</a></p> |""".stripMargin
    ),
    micrositeName := "Scalaz Actors",
    micrositeDescription := "A high-performance, purely-functional library for building, composing, and supervising typed actors based on Scalaz ZIO",
    micrositeAuthor := "scalaz-actors contributors",
    micrositeOrganizationHomepage := "https://github.com/scalaz/scalaz-actors",
    micrositeGitterChannelUrl := "scalaz/scalaz-actors",
    micrositeGitHostingUrl := "https://github.com/scalaz/scalaz-actors",
    micrositeGithubOwner := "scalaz",
    micrositeGithubRepo := "scalaz-actors",
    micrositeFavicons := Seq(microsites.MicrositeFavicon("favicon.png", "512x512")),
    micrositeDocumentationUrl := s"https://javadoc.io/doc/org.scalaz/scalaz-actors_2.12/${(version in Compile).value}",
    micrositeDocumentationLabelDescription := "Scaladoc",
    micrositeBaseUrl := "/scalaz-actors",
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
