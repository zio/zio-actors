import sbt._
import sbt.Keys._
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {
  private val Scala212 = "2.12.18"
  private val Scala213 = "2.13.12"
  private val Scala3   = "3.3.1"

  private val stdOptions = Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
    "-language:higherKinds",
    "-language:existentials",
    "-Wunused:privates",
    "-Wunused:imports",
    "-Wunused:params",
    "-Wvalue-discard"
  )

  private val stdOpts3 = Seq(
    "-explain-types"
  )

  private val stdOpts213 = Seq(
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:3",
    "-explaintypes",
    "-Yrangepos",
    "-Wunused:patvars",
    "-Xlint:-infer-any"
  )

  private val stdOptsUpto212 = Seq(
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:3",
    "-explaintypes",
    "-Yrangepos",
    "-Xfuture",
    "-Ypartial-unification",
    "-Ywarn-nullary-override",
    "-Yno-adapted-args",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused-import",
    "-Xlint:-infer-any"
  )

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _))  => stdOpts3
      case Some((2, 13)) => stdOpts213
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        ) ++ stdOptsUpto212
      case _             =>
        Seq("-Xexperimental") ++ stdOptsUpto212
    }

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName,
      buildInfoObject  := "BuildInfo"
    )

  def stdSettings(prjName: String) =
    Seq(
      name                     := s"$prjName",
      crossScalaVersions       := Seq(Scala212, Scala213, Scala3),
      ThisBuild / scalaVersion := Scala3,
      scalacOptions            := stdOptions ++ extraOptions(scalaVersion.value),
      incOptions ~= (_.withLogRecompileOnMacro(false))
    )
}
