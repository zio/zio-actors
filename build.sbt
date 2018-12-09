import Scalaz._

organization in ThisBuild := "org.scalaz"

version in ThisBuild := "0.1.0-SNAPSHOT"

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots".at(nexus + "content/repositories/snapshots"))
  else
    Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
}

dynverSonatypeSnapshots in ThisBuild := true

lazy val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

credentials in ThisBuild ++= sonataCredentials.toSeq

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.2.26",
  "org.scalaz" %% "scalaz-zio"  % "0.5.0"
)

lazy val root =
  (project in file("."))
    .settings(
      stdSettings("actors")
    )
