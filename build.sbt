import PlayKeys._
import com.typesafe.config._
import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}
import com.typesafe.sbt.pgp.PgpKeys._
import sbt.ExclusionRule

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
  //   "play.server.netty.transport" -> "jdk" // uncomment for MacOS
)

libraryDependencies ++= Seq(
  "org.adada" %% "ada-server" % "0.8.1",
  "org.in-cal" %% "incal-play" % "0.2.4",
  "com.typesafe.play" %% "play-mailer" % "6.0.1",        // to send emails
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",  // to send emails (Guice)
  "jp.t2v" %% "play2-auth" % "0.14.1",
  "org.scalaz" % "scalaz-core_2.11" % "7.2.1",
  "org.webjars" % "typeaheadjs" % "0.11.1",              // typeahead (autocompletion)
  "org.webjars" % "html5shiv" % "3.7.0",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "highcharts" % "5.0.14",               // highcharts for plotting
  "org.webjars.npm" % "bootstrap-select" % "1.13.2",     // bootstrap select element
  "org.webjars.bower" % "plotly.js" % "1.5.1",           // Plotly
  "org.webjars.bower" % "d3" % "3.5.16",
  "org.webjars.bower" % "Autolinker.js" % "0.25.0",      // to convert links to a-href elements
  "org.webjars" % "jquery-ui" % "1.11.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.+" % "test",
  // Because of Spark (turning janino logging to warn: https://github.com/janino-compiler/janino/issues/13)
  "ch.qos.logback" % "logback-classic" % "1.2.3"
) map { _.excludeAll(ExclusionRule(organization = "org.slf4j")) }

val jacksonVersion = "2.8.8"

// Jackson overrides because of Spark
dependencyOverrides ++= Set(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
  "net.sourceforge.cssparser" % "cssparser" % "0.9.19"  // we use a newer version (not 0.9.18) because of a license mismatch
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
    Artifact(moduleName.value, "jar", "jar",     "assets") -> assets
//    Artifact(moduleName.value, "jar", "jar.asc", "assets") -> new java.io.File(assets.getAbsolutePath + ".asc")  // manually sign assets.jar, uncomment, and republish
  )
}

// remove the custom conf from the produced jar
mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.endsWith("custom.conf")) }

// Asset stages

pipelineStages in Assets := Seq(closure, cssCompress, digest, gzip)

excludeFilter in gzip := (excludeFilter in gzip).value || new SimpleFileFilter(file => new File(file.getAbsolutePath + ".gz").exists)

includeFilter in closure := (includeFilter in closure).value && new SimpleFileFilter(f => f.getPath.contains("javascripts"))

includeFilter in cssCompress := (includeFilter in cssCompress).value && new SimpleFileFilter(f => f.getPath.contains("stylesheets"))

//includeFilter in uglify := GlobFilter("javascripts/*.js")


// For licenses not automatically downloaded (need to list them manually)
licenseOverrides := {
  case
    DepModuleInfo("org.apache.commons", _, _)
    | DepModuleInfo("org.apache.curator", _, _)
    | DepModuleInfo("org.apache.directory.api", _, _)
    | DepModuleInfo("org.apache.directory.server", _, _)
    | DepModuleInfo("org.apache.httpcomponents", _, _)
    | DepModuleInfo("org.apache.hadoop", _, _)
    | DepModuleInfo("org.apache.parquet", _, _)
    | DepModuleInfo("org.apache.avro", _, _)
    | DepModuleInfo("commons-beanutils", "commons-beanutils", _)
    | DepModuleInfo("commons-beanutils", "commons-beanutils-core", _)
    | DepModuleInfo("commons-cli", "commons-cli", _)
    | DepModuleInfo("commons-codec", "commons-codec", _)
    | DepModuleInfo("commons-collections", "commons-collections", _)
    | DepModuleInfo("commons-io", "commons-io", _)
    | DepModuleInfo("commons-lang", "commons-lang", _)
    | DepModuleInfo("commons-logging", "commons-logging", _)
    | DepModuleInfo("commons-net", "commons-net", _)
    | DepModuleInfo("com.google.guava", "guava", _)
    | DepModuleInfo("com.google.inject", "guice", _)
    | DepModuleInfo("com.google.inject.extensions", "guice-multibindings", _)
    | DepModuleInfo("com.google.inject.extensions", "guice-assistedinject", "4.0")
    | DepModuleInfo("io.dropwizard.metrics", _, _)
    | DepModuleInfo("org.apache.xbean", "xbean-asm5-shaded", "4.4")
    | DepModuleInfo("org.apache.ivy", "ivy", "2.4.0")
    | DepModuleInfo("org.apache.zookeeper", "zookeeper", "3.4.6")
    | DepModuleInfo("com.fasterxml.jackson.module", "jackson-module-paranamer", "2.6.5")
    | DepModuleInfo("io.netty", "netty-all", "4.0.43.Final")
    | DepModuleInfo("com.bnd-lib", _, _)
    | DepModuleInfo("org.codehaus.jettison", "jettison", "1.1")
    | DepModuleInfo("org.htrace", "htrace-core", "3.0.4")
    | DepModuleInfo("org.mortbay.jetty", "jetty-util", "6.1.26")
    | DepModuleInfo("org.objenesis", "objenesis", "2.1")
    | DepModuleInfo("com.carrotsearch", "hppc", "0.7.1")
    | DepModuleInfo("com.github.lejon.T-SNE-Java", "tsne", "v2.5.0")
    | DepModuleInfo("oauth.signpost", "signpost-commonshttp4", "1.2.1.2")
    | DepModuleInfo("oauth.signpost", "signpost-core", "1.2.1.2")
    | DepModuleInfo("org.hibernate", "hibernate-validator", "5.2.4.Final")
    | DepModuleInfo("org.json4s", "json4s-ast_2.11", "3.2.11")
    | DepModuleInfo("org.json4s", "json4s-core_2.11", "3.2.11")
    | DepModuleInfo("org.json4s", "json4s-jackson_2.11", "3.2.11")
    | DepModuleInfo("javax.cache", "cache-api", "1.0.0")
    | DepModuleInfo("oro", "oro", "2.0.8")
    | DepModuleInfo("xerces", "xercesImpl", "2.9.1")
    | DepModuleInfo("net.java.dev.jna", "jna", _) // both jna and jna-platform libs have a dual LGPL / Apache 2.0 license, we choose Apache 2.0
    | DepModuleInfo("net.java.dev.jna", "jna-platform", _)
    | DepModuleInfo("cglib", "cglib-nodep", _)
    | DepModuleInfo("org.webjars", "bootswatch-united", "3.3.4+1")
  =>
    LicenseInfo(LicenseCategory.Apache, "Apache License v2.0", "http://www.apache.org/licenses/LICENSE-2.0")

  case
    DepModuleInfo("org.glassfish.hk2", "hk2-api", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2", "hk2-locator", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2", "hk2-utils", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2", "osgi-resource-locator", "1.0.1")
    | DepModuleInfo("org.glassfish.hk2.external", "aopalliance-repackaged", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.hk2.external", "javax.inject", "2.4.0-b34")
    | DepModuleInfo("org.glassfish.jersey.bundles.repackaged", "jersey-guava", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.containers", "jersey-container-servlet", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.containers", "jersey-container-servlet-core", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.core", "jersey-client", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.core", "jersey-common", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.core", "jersey-server", "2.22.2")
    | DepModuleInfo("org.glassfish.jersey.media", "jersey-media-jaxb", "2.22.2")
    | DepModuleInfo("javax.xml.bind", "jaxb-api", "2.2.2")
    | DepModuleInfo("javax.ws.rs", "javax.ws.rs-api", "2.0.1")
  =>
    LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://javaee.github.io/glassfish/LICENSE")

  case
    DepModuleInfo("javax.mail", "mail", "1.4.7")
    | DepModuleInfo("com.sun.mail", "javax.mail", "1.5.6")
  =>
    LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://javaee.github.io/javamail/LICENSE")

  case
    DepModuleInfo("javax.transaction", "jta", "1.1")
  =>
    LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://github.com/javaee/javax.transaction/blob/master/LICENSE")

  case
    DepModuleInfo("com.esotericsoftware", "kryo-shaded", "3.0.3")
    | DepModuleInfo("org.hamcrest", "hamcrest-core", "1.3")
  =>
    LicenseInfo(LicenseCategory.BSD, "BSD 2-clause", "https://opensource.org/licenses/BSD-2-Clause")

  case
    DepModuleInfo("com.github.fommil.netlib", "core", "1.1.2")
    | DepModuleInfo("com.github.fommil", "jniloader", "1.1")
    | DepModuleInfo("org.antlr", "antlr4-runtime", "4.5.3")
    | DepModuleInfo("org.fusesource.leveldbjni", "leveldbjni-all", "1.8")
  =>
    LicenseInfo(LicenseCategory.BSD, "BSD 3-clause", "https://opensource.org/licenses/BSD-3-Clause")

  case
    DepModuleInfo("org.codehaus.janino", "commons-compiler", "3.0.0")
    | DepModuleInfo("org.codehaus.janino", "janino", "3.0.0")
  =>
    LicenseInfo(LicenseCategory.BSD, "New BSD License", "http://www.opensource.org/licenses/bsd-license.php")

  case DepModuleInfo("org.slf4j", _, _) =>
    LicenseInfo(LicenseCategory.MIT, "MIT", "http://opensource.org/licenses/MIT")

  case DepModuleInfo("org.bouncycastle", "bcprov-jdk15on", "1.51") =>
    LicenseInfo(LicenseCategory.MIT, "Bouncy Castle Licence", "http://www.bouncycastle.org/licence.html")

  case
    DepModuleInfo("com.h2database", "h2", "1.3.175") // h2database has a dual MPL / EPL license (http://h2database.com/html/license.html), we choose EPL
    | DepModuleInfo("junit", "junit", "4.12")
    | DepModuleInfo("ch.qos.logback", "logback-classic", _) // logback libs have a dual LGPL / EPL license, we choose EPL
    | DepModuleInfo("ch.qos.logback", "logback-core", _)
  =>
    LicenseInfo(LicenseCategory.EPL, "Eclipse Public License 1.0", "http://www.eclipse.org/legal/epl-v10.html")

  case
    DepModuleInfo("com.unboundid", "unboundid-ldapsdk", "2.3.8") // LDAP SDK has a ternary GPLv2 / GPLv2.1 / UnboundID LDAP SDK Free Use license, we choose the last one
  =>
    LicenseInfo(LicenseCategory.Unrecognized, "UnboundID LDAP SDK Free Use License", "https://github.com/pingidentity/ldapsdk/blob/master/LICENSE-UnboundID-LDAPSDK.txt")
}

// POM settings for Sonatype

homepage := Some(url("https://ada-discovery.github.io"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/ada-discovery/ada-web"), "scm:git@github.com:ada-discovery/ada-web.git"))

developers := List(
  Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net")),
  Developer("sherzinger", "Sascha Herzinger", "sascha.herzinger@uni.lu", url("https://wwwfr.uni.lu/lcsb/people/sascha_herzinger"))
)

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

fork in Test := true