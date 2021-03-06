import java.io.File

import play.core.PlayVersion

name := "play-cxf-example"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.11"

val playVersionSuffix: String = {
  val versions = PlayVersion.current.split('.')
  require(versions.length >= 2)
  versions.take(2).mkString
}

val CxfVersion = "3.1.12"

libraryDependencies ++= Seq(
  guice,

  "org.apache.cxf" % "cxf-core"                 % CxfVersion,
  "org.apache.cxf" % "cxf-rt-frontend-jaxws"    % CxfVersion,
  "org.apache.cxf" % "cxf-rt-transports-http"   % CxfVersion
)

libraryDependencies += "eu.sipria.play" %% "play-guice-cxf_play26" % "1.6.0"

version in cxf := CxfVersion

defaultArgs in wsdl2java := Seq(
  "-p", "services.sunset.rise"
)

wsdls in wsdl2java := Seq(
  Wsdl("SunsetRiseService", (resourceDirectory in Compile).value / "sunsetriseservice.wsdl", Seq(
    "-wsdlLocation", "conf/sunsetriseservice.wsdl"
  ))
)

scalacOptions := Seq(
  "-deprecation"
)

lazy val playCxf = RootProject(file("../../play-cxf/"))

lazy val myInfo = taskKey[Seq[File]]("List")
lazy val newList = taskKey[Seq[File]]("List")

lazy val root = Project("play-cxf-example", file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(CxfPlugin)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(playCxf)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "hello",

    myInfo := {
      val sources = (managedSources in Compile).value
      val managed = (sourceManaged in Compile).value

      val PathMatch = s"""^([^\\${File.separator}]+)(?:\\${File.separator}.*)""".r
      object Path {
        def unapply(arg: File): Option[String] = arg.toString match {
          case PathMatch(value) => Some(value)
          case _ => None
        }
      }

      sources.map(_.relativeTo(managed)).collect { case Some(Path(value)) => value }.distinct.map(managed / _)
    },
    newList := {
      val managed = (sourceManaged in Compile).value

      sourceDirectories.in(Compile).value.filterNot(_.getPath == managed.getPath) ++ myInfo.value
    }
  )
