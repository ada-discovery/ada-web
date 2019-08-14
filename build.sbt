import PlayKeys._
import com.typesafe.config._
import com.typesafe.sbt.pgp.PgpKeys._

organization := "org.adada"

name := "ada-web"

// load version from the app config
val conf = ConfigFactory.parseFile(new java.io.File("conf/application.conf")).resolve()
version := conf.getString("app.version")

description := "Web part of Ada Discovery Analytics backed by Play Framework."

isSnapshot := false

scalaVersion := "2.11.12"

resolvers ++= Seq(
  Resolver.mavenLocal
)

routesImport ++= Seq(
  "reactivemongo.bson.BSONObjectID",
  "org.ada.web.controllers.PathBindables._",
  "org.ada.web.controllers.QueryStringBinders._"
)

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

PlayKeys.devSettings := Seq(
  "play.server.netty.maxInitialLineLength" -> "16384"
  //   "play.server.netty.transport" -> "jdk"
)

libraryDependencies ++= Seq(
  "org.adada" %% "ada-server" % "0.8.1.RC.4",
  "org.in-cal" %% "incal-play" % "0.2.2.RC.3",
  "com.typesafe.play" %% "play-mailer" % "6.0.1",        // to send emails
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",  // to send emails (Guice)
  "jp.t2v" %% "play2-auth" % "0.14.1",
  "org.scalaz" % "scalaz-core_2.11" % "7.2.1",
  "org.webjars" % "typeaheadjs" % "0.11.1",              // typeahead (autocompletion)
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "highcharts" % "5.0.14",               // highcharts for plotting
  "org.webjars.npm" % "bootstrap-select" % "1.13.2",     // bootstrap select element
  "org.webjars.bower" % "plotly.js" % "1.5.1",           // not used - can be removed
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars.bower" % "Autolinker.js" % "0.25.0",      // to convert links to a-href elements
  "org.webjars" % "jquery-ui" % "1.11.1",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

// Because of Spark (turning janino logging to warn: https://github.com/janino-compiler/janino/issues/13)
libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

val jacksonVersion = "2.8.8"

// Because of Spark
dependencyOverrides ++= Set(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion
)

packagedArtifacts in publishLocal := {
  val artifacts: Map[sbt.Artifact, java.io.File] = (packagedArtifacts in publishLocal).value
  val assets: java.io.File = (playPackageAssets in Compile).value
  artifacts + (Artifact(moduleName.value, "jar", "jar", "assets") -> assets)
}

signedArtifacts := {
  val artifacts: Map[sbt.Artifact, java.io.File] = signedArtifacts.value
  val assets: java.io.File = (playPackageAssets in Compile).value
  artifacts ++ Seq(
    Artifact(moduleName.value, "jar", "jar",     "assets") -> assets,
    Artifact(moduleName.value, "jar", "jar.asc", "assets") -> new java.io.File(assets.getAbsolutePath + ".asc")  // requires a manual signing of assets.jar
  )
}

// remove custom conf form the jar
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith("custom.conf")) }

// Asset stages

pipelineStages in Assets := Seq(closure, cssCompress, digest, gzip)

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

includeFilter in closure := (includeFilter in closure).value && new SimpleFileFilter(f => f.getPath.contains("javascripts"))

includeFilter in cssCompress := (includeFilter in cssCompress).value && new SimpleFileFilter(f => f.getPath.contains("stylesheets"))

//includeFilter in uglify := GlobFilter("javascripts/*.js")


// POM settings for Sonatype

homepage := Some(url("https://ada-discovery.github.io"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/ada-discovery/ada-web"), "scm:git@github.com:ada-discovery/ada-web.git"))

developers := List(Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")))

licenses ++= Seq(
  "Creative Commons Attribution-NonCommercial 3.0" -> url("http://creativecommons.org/licenses/by-nc/3.0"),
  "Highcharts" -> url("https://www.highcharts.com/blog/products/highcharts")
)

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)