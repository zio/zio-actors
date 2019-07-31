import sbt._
import sbt.Keys._

object Build {
  val compileOnlyDeps = Seq("com.github.ghik" %% "silencer-lib" % "1.4.2" % "provided")

  private val std2xOptions = Seq(
    "-Xfatal-warnings",
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xfuture",
    "-Xsource:2.13",
    "-Xlint:_,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xmax-classfile-name",
    "242"
  )

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
      case Some((2, 11)) =>
        Seq(
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Xexperimental",
          "-Ywarn-unused-import"
        ) ++ std2xOptions
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
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.4.2")
    ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
