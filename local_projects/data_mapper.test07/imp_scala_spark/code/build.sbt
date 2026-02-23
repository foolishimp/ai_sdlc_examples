// Implements: REQ-LDM-01 (module isolation), REQ-PDM-01 (LDM/PDM separation)
// See ADR-007: Multi-Module sbt Structure

val scalaTestVersion  = "3.2.18"
val scalaCheckVersion = "1.17.0"
val sparkVersion      = "3.5.0"
val openLineageVersion = "1.6.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.12",
  organization := "com.cdme",
  version      := "1.0.0-SNAPSHOT",
  scalacOptions ++= Seq(
    "-Xlint:_",
    "-Xfatal-warnings",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "UTF-8"
  ),
  libraryDependencies ++= Seq(
    "org.scalatest"  %% "scalatest"  % scalaTestVersion  % Test,
    "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test,
    "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test
  )
)

// --- cdme-model: Pure domain types (zero external deps) ---
lazy val model = (project in file("cdme-model"))
  .settings(commonSettings)
  .settings(
    name := "cdme-model"
  )

// --- cdme-compiler: Topological compiler (depends: model) ---
lazy val compiler = (project in file("cdme-compiler"))
  .dependsOn(model)
  .settings(commonSettings)
  .settings(
    name := "cdme-compiler"
  )

// --- cdme-runtime: Execution engine (depends: compiler) ---
lazy val runtime = (project in file("cdme-runtime"))
  .dependsOn(compiler)
  .settings(commonSettings)
  .settings(
    name := "cdme-runtime"
  )

// --- cdme-spark: Spark binding (depends: runtime, Spark provided) ---
lazy val spark = (project in file("cdme-spark"))
  .dependsOn(runtime)
  .settings(commonSettings)
  .settings(
    name := "cdme-spark",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql"  % sparkVersion % Provided,
      "org.apache.spark" %% "spark-core" % sparkVersion % Provided
    )
  )

// --- cdme-lineage: OpenLineage integration (depends: runtime) ---
lazy val lineage = (project in file("cdme-lineage"))
  .dependsOn(runtime)
  .settings(commonSettings)
  .settings(
    name := "cdme-lineage",
    libraryDependencies ++= Seq(
      "io.openlineage" % "openlineage-java" % openLineageVersion
    )
  )

// --- cdme-adjoint: Backward traversal, Galois connections (depends: model) ---
lazy val adjoint = (project in file("cdme-adjoint"))
  .dependsOn(model)
  .settings(commonSettings)
  .settings(
    name := "cdme-adjoint"
  )

// --- cdme-external: External calculator registry (depends: runtime) ---
lazy val external = (project in file("cdme-external"))
  .dependsOn(runtime)
  .settings(commonSettings)
  .settings(
    name := "cdme-external"
  )

// --- cdme-api: Public API (depends: all modules) ---
lazy val api = (project in file("cdme-api"))
  .dependsOn(compiler, runtime, spark, lineage, adjoint, external)
  .settings(commonSettings)
  .settings(
    name := "cdme-api"
  )

// --- Root project: aggregates all modules ---
lazy val root = (project in file("."))
  .aggregate(model, compiler, runtime, spark, lineage, adjoint, external, api)
  .settings(
    name := "cdme",
    publish / skip := true
  )
