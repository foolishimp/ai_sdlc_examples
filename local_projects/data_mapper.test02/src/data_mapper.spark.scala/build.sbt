// CDME Spark Implementation
// Implements: Design from docs/design/design_spark/

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.18"  // Spark 3.5.x compatibility
ThisBuild / organization := "com.cdme"

// Compiler options
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-unused:imports",
  "-Ywarn-dead-code",
  "-language:higherKinds",
  "-language:implicitConversions"
)

// Test options
ThisBuild / Test / fork := true
ThisBuild / Test / parallelExecution := false  // Spark tests can conflict

// Dependency versions
val sparkVersion = "3.5.0"
val catsVersion = "2.10.0"
val circeVersion = "0.14.6"
val refinedVersion = "0.11.0"
val scalaTestVersion = "3.2.17"

// Dependencies
lazy val commonDependencies = Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test
)

lazy val sparkDependencies = Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % Provided,
  "org.apache.spark" %% "spark-sql" % sparkVersion % Provided
)

lazy val circeDependencies = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-yaml" % "0.14.2"
)

lazy val refinedDependencies = Seq(
  "eu.timepit" %% "refined" % refinedVersion,
  "eu.timepit" %% "refined-cats" % refinedVersion,
  "io.circe" %% "circe-refined" % circeVersion
)

lazy val loggingDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.11"
)

// Root project
lazy val root = (project in file("."))
  .settings(
    name := "cdme-spark",
    libraryDependencies ++=
      commonDependencies ++
      sparkDependencies ++
      circeDependencies ++
      refinedDependencies ++
      loggingDependencies,

    // Assembly settings for fat JAR
    assembly / mainClass := Some("cdme.Main"),
    assembly / assemblyJarName := "cdme-spark.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x if x.endsWith(".proto") => MergeStrategy.first
      case _ => MergeStrategy.first
    }
  )
