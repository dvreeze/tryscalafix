val scalaVer = "2.13.12"
val crossScalaVer = Seq(scalaVer)

ThisBuild / description := "Trying out Scalafix for helping understand large (Scala) code bases"
ThisBuild / organization := "eu.cdevreeze.tryscalafix"
ThisBuild / version := "0.4.0-SNAPSHOT"

ThisBuild / versionScheme := Some("strict")

ThisBuild / scalaVersion := scalaVer
ThisBuild / crossScalaVersions := crossScalaVer

ThisBuild / semanticdbEnabled := true // enable SemanticDB
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision // only required for Scala 2.x

ThisBuild / scalacOptions ++= Seq(
  "-Wconf:cat=unused-imports:w,cat=unchecked:w,cat=deprecation:w,cat=feature:w,cat=lint:w"
)

ThisBuild / publishMavenStyle := true

ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) {
    Some("snapshots".at(nexus + "content/repositories/snapshots"))
  } else {
    Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
  }
}

// TODO versionScheme, such as early-semver

ThisBuild / pomExtra := pomData
ThisBuild / pomIncludeRepository := { _ => false }

// Artifact org.scalameta::scalameta depends on artifact org.scalameta::trees and on the Scala compiler. This makes
// sense, because org.scalameta::scalameta ships with Metacp, which uses the Scala compiler to generate SemanticDB
// output (e.g. symbols and types). Artifact org.scalameta::trees, on the other hand, needs no Scala compiler,
// does not depend on it, and uses its internal FastParse parser combinator to parse Scala sources into syntactic trees (ASTs).

ThisBuild / libraryDependencies += "org.scalameta" %% "scalameta" % "4.8.14"

ThisBuild / libraryDependencies += "org.scalameta" % "scalafmt-interfaces" % "3.7.17"

ThisBuild / libraryDependencies += "org.scalameta" %% "scalafmt-dynamic" % "3.7.17"

ThisBuild / libraryDependencies += "ch.epfl.scala" %% "scalafix-rules" % "0.11.1"

ThisBuild / libraryDependencies += "com.geirsson" %% "metaconfig-typesafe-config" % "0.11.1"

ThisBuild / libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"

val circeVersion = "0.14.1"

ThisBuild / libraryDependencies += "io.circe" %% "circe-core" % circeVersion
ThisBuild / libraryDependencies += "io.circe" %% "circe-generic" % circeVersion
ThisBuild / libraryDependencies += "io.circe" %% "circe-parser" % circeVersion

ThisBuild / libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test

lazy val root = project
  .in(file("."))
  .settings(
    name := "tryscalafix"
  )

lazy val pomData =
  <url>https://github.com/dvreeze/tryscalafix</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
        <comments>Try-scalafix is licensed under Apache License, Version 2.0</comments>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:git@github.com:dvreeze/tryscalafix.git</connection>
      <url>https://github.com/dvreeze/tryscalafix.git</url>
      <developerConnection>scm:git:git@github.com:dvreeze/tryscalafix.git</developerConnection>
    </scm>
    <developers>
      <developer>
        <id>dvreeze</id>
        <name>Chris de Vreeze</name>
        <email>chris.de.vreeze@caiway.net</email>
      </developer>
    </developers>
