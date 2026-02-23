// CDME — Categorical Data Mapping & Computation Engine
// Implements: ADR-007 (sbt Multi-Module with Strict Dependency DAG)

ThisBuild / organization := "com.cdme"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard"
)

// Dependency versions
val sparkVersion     = "3.5.0"
val scalatestVersion = "3.2.17"
val scalacheckVersion = "1.17.0"
val circeVersion     = "0.14.6"

// ─── cdme-model: Pure domain types (zero external deps) ───
lazy val `cdme-model` = (project in file("cdme-model"))
  .settings(
    name := "cdme-model",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test,
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test
    )
  )

// ─── cdme-compiler: Topological compiler (depends: model) ───
lazy val `cdme-compiler` = (project in file("cdme-compiler"))
  .dependsOn(`cdme-model`)
  .settings(
    name := "cdme-compiler",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test,
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    )
  )

// ─── cdme-adjoint: Adjoint/Frobenius backward traversal (depends: model) ───
lazy val `cdme-adjoint` = (project in file("cdme-adjoint"))
  .dependsOn(`cdme-model`)
  .settings(
    name := "cdme-adjoint",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test,
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    )
  )

// ─── cdme-runtime: Execution engine (depends: compiler) ───
lazy val `cdme-runtime` = (project in file("cdme-runtime"))
  .dependsOn(`cdme-compiler`)
  .settings(
    name := "cdme-runtime",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test,
      "org.scalacheck" %% "scalacheck" % scalacheckVersion % Test
    )
  )

// ─── cdme-spark: Spark binding (depends: runtime) ───
lazy val `cdme-spark` = (project in file("cdme-spark"))
  .dependsOn(`cdme-runtime`)
  .settings(
    name := "cdme-spark",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % sparkVersion % Provided,
      "org.apache.spark" %% "spark-sql"  % sparkVersion % Provided,
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test,
      "org.apache.spark" %% "spark-core" % sparkVersion % Test,
      "org.apache.spark" %% "spark-sql"  % sparkVersion % Test
    )
  )

// ─── cdme-lineage: OpenLineage integration (depends: runtime) ───
lazy val `cdme-lineage` = (project in file("cdme-lineage"))
  .dependsOn(`cdme-runtime`)
  .settings(
    name := "cdme-lineage",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test
    )
  )

// ─── cdme-ai-assurance: AI validation layer (depends: compiler) ───
lazy val `cdme-ai-assurance` = (project in file("cdme-ai-assurance"))
  .dependsOn(`cdme-compiler`)
  .settings(
    name := "cdme-ai-assurance",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test
    )
  )

// ─── cdme-api: REST/gRPC API (depends: all) ───
lazy val `cdme-api` = (project in file("cdme-api"))
  .dependsOn(`cdme-runtime`, `cdme-spark`, `cdme-lineage`, `cdme-adjoint`, `cdme-ai-assurance`)
  .settings(
    name := "cdme-api",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      "org.scalatest"  %% "scalatest"  % scalatestVersion  % Test
    )
  )

// ─── Root aggregator ───
lazy val root = (project in file("."))
  .aggregate(
    `cdme-model`,
    `cdme-compiler`,
    `cdme-adjoint`,
    `cdme-runtime`,
    `cdme-spark`,
    `cdme-lineage`,
    `cdme-ai-assurance`,
    `cdme-api`
  )
  .settings(
    name := "cdme",
    publish / skip := true
  )
