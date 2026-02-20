// Implements: REQ-F-CDME-001 (Project build definition)
// CDME - Categorical Data Mapping & Computation Engine
// ADR-013: Scala 2.13 + Spark 3.5

val scala2Version = "2.13.12"
val sparkVersion = "3.5.0"

lazy val commonSettings = Seq(
  scalaVersion := scala2Version,
  organization := "com.cdme",
  version      := "0.1.0-SNAPSHOT",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-language:implicitConversions",
    "-language:higherKinds"
  ),
  libraryDependencies ++= Seq(
    "org.scalatest"     %% "scalatest"        % "3.2.18"   % Test,
    "org.scalatestplus" %% "scalacheck-1-17"  % "3.2.18.0" % Test,
    "org.scalacheck"    %% "scalacheck"       % "1.17.0"   % Test
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
      "org.apache.spark" %% "spark-sql" % sparkVersion % "provided"
    )
  )
  .dependsOn(cdmeRuntime, cdmeApi)

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
