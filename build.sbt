organization := "org.adada"

name := "ada-web"

version := "0.7.3.RC.6"

description := "Web part of Ada Discovery Analytics backed by Play Framework."

isSnapshot := false

scalaVersion := "2.11.12"

val playVersion = "2.5.9"

resolvers ++= Seq(
  Resolver.mavenLocal
)

routesImport ++= Seq(
  "reactivemongo.bson.BSONObjectID",
  "org.ada.web.controllers.PathBindables._",
  "org.ada.web.controllers.QueryStringBinders._"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "org.adada" %% "ada-server" % "0.7.3.RC.6",
  "org.in-cal" %% "incal-play" % "0.1.6",
  "com.typesafe.play" %% "play-mailer" % "4.0.0",
  "jp.t2v" %% "play2-auth" % "0.14.1",
  "commons-net" % "commons-net" % "3.5", // for ftp access
  "org.scalaz" % "scalaz-core_2.11" % "7.2.1",
  "org.webjars" % "bootstrap" % "3.3.7",
  "org.webjars" % "bootswatch-united" % "3.3.4+1",
  "org.webjars" % "typeaheadjs" % "0.11.1",
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "highcharts" % "5.0.14",
  "org.webjars.npm" % "bootstrap-select" % "1.13.2", // bootstrap select element
  "org.webjars.bower" % "plotly.js" % "1.5.1",
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars.bower" % "Autolinker.js" % "0.25.0", // to convert links to a-href elements
  "org.webjars" % "jquery-ui" % "1.11.1"
)

// POM settings for Sonatype
homepage := Some(url("https://ada-discovery.org"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/ada-discovery/ada-web"), "scm:git@github.com:ada-discovery/ada-web.git"))

developers := List(Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")))

licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)
