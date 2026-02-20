// Implements: REQ-F-CDME-001 (Project build definition)
// CDME - Categorical Data Mapping & Computation Engine

val scala3Version = "3.3.3"

lazy val commonSettings = Seq(
  scalaVersion := scala3Version,
  organization := "com.cdme",
  version      := "0.1.0-SNAPSHOT",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-language:implicitConversions",
    "-language:higherKinds"
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "cdme",
    commonSettings
  )
  .aggregate(
    cdmeModel,
    cdmeCompiler,
    cdmeRuntime,
    cdmeApi,
    cdmeSpark,
    cdmeLineage,
    cdmeRegulatory,
    cdmeExternal
  )

lazy val cdmeModel = (project in file("cdme-model"))
  .settings(
    name := "cdme-model",
    commonSettings
    // Model layer: ZERO external dependencies
  )

lazy val cdmeCompiler = (project in file("cdme-compiler"))
  .settings(
    name := "cdme-compiler",
    commonSettings
  )
  .dependsOn(cdmeModel)

lazy val cdmeRuntime = (project in file("cdme-runtime"))
  .settings(
    name := "cdme-runtime",
    commonSettings
  )
  .dependsOn(cdmeModel, cdmeCompiler)

lazy val cdmeApi = (project in file("cdme-api"))
  .settings(
    name := "cdme-api",
    commonSettings
  )
  .dependsOn(cdmeModel, cdmeCompiler, cdmeRuntime)

lazy val cdmeSpark = (project in file("cdme-spark"))
  .settings(
    name := "cdme-spark",
    commonSettings,
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "3.5.0" % "provided" cross CrossVersion.for3Use2_13
    )
  )
  .dependsOn(cdmeRuntime)

lazy val cdmeLineage = (project in file("cdme-lineage"))
  .settings(
    name := "cdme-lineage",
    commonSettings
  )
  .dependsOn(cdmeRuntime)

lazy val cdmeRegulatory = (project in file("cdme-regulatory"))
  .settings(
    name := "cdme-regulatory",
    commonSettings
  )
  .dependsOn(cdmeRuntime)

lazy val cdmeExternal = (project in file("cdme-external"))
  .settings(
    name := "cdme-external",
    commonSettings
  )
  .dependsOn(cdmeRuntime)
