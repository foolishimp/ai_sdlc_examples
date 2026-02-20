// CDME — Categorical Data Mapping & Computation Engine
// Implements: REQ-NFR-VER-001 (configuration artifact versioning)
//
// Root build definition — 8 modules, strict dependency DAG
// See docs/design/scala_spark/DESIGN.md section 6.3

ThisBuild / organization := "com.cdme"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-language:implicitConversions",
  "-language:higherKinds"
)

// ---------------------------------------------------------------------------
// Root project — aggregates all modules, publishes nothing
// ---------------------------------------------------------------------------
lazy val root = (project in file("."))
  .aggregate(model, compiler, runtime, spark, lineage, ai, context, testkit)
  .settings(
    name := "cdme",
    publish / skip := true
  )

// ---------------------------------------------------------------------------
// cdme-model — pure domain ADT, ZERO external dependencies
// ---------------------------------------------------------------------------
lazy val model = (project in file("cdme-model"))
  .settings(
    name := "cdme-model"
    // Zero external deps — pure Scala 2.13 standard library only
  )

// ---------------------------------------------------------------------------
// cdme-compiler — topological validation, depends on model only
// ---------------------------------------------------------------------------
lazy val compiler = (project in file("cdme-compiler"))
  .dependsOn(model)
  .settings(
    name := "cdme-compiler"
  )

// ---------------------------------------------------------------------------
// cdme-runtime — abstract execution, depends on model only
// ---------------------------------------------------------------------------
lazy val runtime = (project in file("cdme-runtime"))
  .dependsOn(model)
  .settings(
    name := "cdme-runtime"
  )

// ---------------------------------------------------------------------------
// cdme-spark — Spark binding, depends on runtime + model
// ---------------------------------------------------------------------------
lazy val spark = (project in file("cdme-spark"))
  .dependsOn(runtime, model)
  .settings(
    name := "cdme-spark",
    libraryDependencies ++= Seq(
      Dependencies.sparkSql % "provided"
    )
  )

// ---------------------------------------------------------------------------
// cdme-lineage — OpenLineage, ledger, checkpoints; depends on runtime + model
// Note: lineage uses runtime types (WriterTelemetry, AdjointMetadata) for
// ledger generation and backward traversal.
// ---------------------------------------------------------------------------
lazy val lineage = (project in file("cdme-lineage"))
  .dependsOn(runtime, model)
  .settings(
    name := "cdme-lineage"
  )

// ---------------------------------------------------------------------------
// cdme-ai — AI mapping validation; depends on compiler + model
// ---------------------------------------------------------------------------
lazy val ai = (project in file("cdme-ai"))
  .dependsOn(compiler, model, lineage, runtime)
  .settings(
    name := "cdme-ai"
  )

// ---------------------------------------------------------------------------
// cdme-context — epoch, fiber, temporal binding; depends on model only
// ---------------------------------------------------------------------------
lazy val context = (project in file("cdme-context"))
  .dependsOn(model)
  .settings(
    name := "cdme-context"
  )

// ---------------------------------------------------------------------------
// cdme-testkit — generators, assertions, fixtures; depends on model
// ---------------------------------------------------------------------------
lazy val testkit = (project in file("cdme-testkit"))
  .dependsOn(model)
  .settings(
    name := "cdme-testkit",
    libraryDependencies ++= Seq(
      Dependencies.scalaTest,
      Dependencies.scalaTestPlusCheck,
      Dependencies.scalaCheck
    )
  )
