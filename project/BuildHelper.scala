import sbt._
import sbt.Keys._

object Build {
  val compileOnlyDeps = Seq("com.github.ghik" %% "silencer-lib" % "1.4.1" % "provided")

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        )
      case _ =>
        Seq(
          "-Xexperimental",
          "-Ywarn-unused-import"
        )
    }

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    crossScalaVersions := Seq("2.12.8"),
    scalaVersion in ThisBuild := crossScalaVersions.value.head,
    scalacOptions := extraOptions(scalaVersion.value),
    libraryDependencies ++= compileOnlyDeps ++ Seq(
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.4.1")
    ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
