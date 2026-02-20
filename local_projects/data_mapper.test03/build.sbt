// Implements: REQ-NFR-PERF-001 (Spark), REQ-F-TYP-001 (Scala 3 types)

val scala3Version = "3.3.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "cdme",
    version := "0.1.0",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "3.5.1" % "provided" cross CrossVersion.for3Use2_13,
      "org.apache.spark" %% "spark-core" % "3.5.1" % "provided" cross CrossVersion.for3Use2_13,
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
  )
