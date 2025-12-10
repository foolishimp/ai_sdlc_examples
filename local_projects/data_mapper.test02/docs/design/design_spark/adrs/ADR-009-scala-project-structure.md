# ADR-009: Scala Project Structure and Build System

**Status**: Accepted
**Date**: 2025-12-10
**Updated**: 2025-12-11 (Spark 4.0 migration - ADR-011)
**Deciders**: Design Agent
**Depends On**: ADR-002 (Language Choice - Scala), ADR-011 (Spark 4.0 Migration)
**Implements**: Best practices for maintainability, testability, deployment

---

## Context

The CDME Scala implementation requires:
- Build system for dependency management and compilation
- Module structure for separating concerns (config, registry, compiler, executor)
- Testing framework for unit, integration, and property-based tests
- Packaging for Spark job submission
- CI/CD integration
- IDE support (IntelliJ, VSCode)

Scala build tools:
- **sbt** (Simple Build Tool) - Scala-native, standard in ecosystem
- **Maven** - Java-based, enterprise-friendly
- **Mill** - Modern alternative, less adoption
- **Gradle** - Android/Java focus, Scala support via plugin

Testing frameworks:
- **ScalaTest** - Feature-rich, multiple testing styles
- **Specs2** - BDD-focused, more opinionated
- **MUnit** - Lightweight, fast compile times
- **JUnit** - Java standard, less idiomatic for Scala

---

## Decision

**Use the following project structure:**

1. **sbt** as build tool
2. **Multi-module project** (core, config, executor, tests)
3. **ScalaTest** with **ScalaCheck** for property-based testing
4. **sbt-assembly** for fat JAR packaging
5. **sbt-native-packager** for Docker images
6. **Coursier** for fast dependency resolution

---

## Rationale

### 1. sbt vs Maven

| Feature | sbt | Maven |
|---------|-----|-------|
| Scala integration | Native | Via plugin |
| Incremental compilation | Yes | Limited |
| Build speed | Fast (incremental) | Slower |
| Ecosystem | Scala-standard | Java-standard |
| Learning curve | Steeper | Gentler |
| Enterprise adoption | Growing | Dominant |

**Decision: sbt**
- Scala-native with best incremental compilation
- Rich plugin ecosystem (sbt-assembly, sbt-docker, etc.)
- Standard in Spark/Scala projects
- Faster iteration during development

**Trade-off accepted**: Enterprise teams familiar with Maven will need to learn sbt.

### 2. Module Structure

```
cdme-spark/
├── build.sbt                        # Root build definition
├── project/
│   ├── build.properties            # sbt version
│   ├── plugins.sbt                 # sbt plugins
│   └── Dependencies.scala          # Centralized dependency versions
│
├── modules/
│   ├── core/                       # Core abstractions
│   │   ├── src/
│   │   │   ├── main/scala/com/cdme/
│   │   │   │   ├── types/          # Grain, Morphism, Entity types
│   │   │   │   ├── domain/         # Domain model (REQ-* concepts)
│   │   │   │   └── algebra/        # Monoid, Adjoint abstractions
│   │   │   └── test/scala/com/cdme/
│   │   └── build.sbt
│   │
│   ├── config/                     # Configuration loading
│   │   ├── src/
│   │   │   ├── main/scala/com/cdme/config/
│   │   │   │   ├── ConfigLoader.scala
│   │   │   │   ├── YamlParser.scala
│   │   │   │   └── models/         # Config case classes
│   │   │   └── test/scala/com/cdme/config/
│   │   └── build.sbt
│   │
│   ├── registry/                   # Schema registry
│   │   ├── src/
│   │   │   ├── main/scala/com/cdme/registry/
│   │   │   │   ├── SchemaRegistry.scala
│   │   │   │   ├── TypeSystem.scala
│   │   │   │   └── PathValidator.scala
│   │   │   └── test/scala/com/cdme/registry/
│   │   └── build.sbt
│   │
│   ├── compiler/                   # Compilation stage
│   │   ├── src/
│   │   │   ├── main/scala/com/cdme/compiler/
│   │   │   │   ├── Compiler.scala
│   │   │   │   ├── Validator.scala
│   │   │   │   ├── Planner.scala
│   │   │   │   └── GrainChecker.scala
│   │   │   └── test/scala/com/cdme/compiler/
│   │   └── build.sbt
│   │
│   ├── executor/                   # Execution engine
│   │   ├── src/
│   │   │   ├── main/scala/com/cdme/executor/
│   │   │   │   ├── Executor.scala
│   │   │   │   ├── morphisms/
│   │   │   │   │   ├── FilterMorphism.scala
│   │   │   │   │   ├── TraverseMorphism.scala
│   │   │   │   │   ├── AggregateMorphism.scala
│   │   │   │   │   └── WindowMorphism.scala
│   │   │   │   ├── aggregators/    # Custom Aggregator classes
│   │   │   │   └── ErrorHandler.scala
│   │   │   └── test/scala/com/cdme/executor/
│   │   └── build.sbt
│   │
│   └── app/                        # Application entry point
│       ├── src/
│       │   ├── main/scala/com/cdme/
│       │   │   ├── Main.scala      # CLI entry point
│       │   │   └── CdmeEngine.scala
│       │   └── test/scala/com/cdme/
│       └── build.sbt
│
├── integration-tests/              # End-to-end tests
│   ├── src/
│   │   └── test/scala/com/cdme/integration/
│   │       ├── SteelThreadSpec.scala
│   │       └── fixtures/           # Test data
│   └── build.sbt
│
├── benchmarks/                     # Performance benchmarks
│   ├── src/
│   │   └── test/scala/com/cdme/benchmarks/
│   └── build.sbt
│
└── docs/
    ├── api/                        # Generated ScalaDoc
    └── examples/                   # Usage examples
```

**Benefits**:
- Clear separation of concerns
- Independent testing per module
- Incremental compilation per module
- Parallel compilation
- Reusable core library

### 3. Root build.sbt

```scala
// build.sbt
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"  // Spark 4.0.x compatibility (was 2.12.18)
ThisBuild / organization := "com.cdme"

// Java version requirement (Spark 4.0 requires Java 17+)
ThisBuild / javacOptions ++= Seq("-source", "17", "-target", "17")

// Compiler options (updated for Scala 2.13)
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-unused:imports",
  "-Ywarn-dead-code",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xsource:3"  // Forward compatibility with Scala 3
)

// Test options
ThisBuild / Test / fork := true
ThisBuild / Test / parallelExecution := true

// Modules
lazy val root = (project in file("."))
  .aggregate(core, config, registry, compiler, executor, app)
  .settings(
    name := "cdme-spark",
    publish / skip := true
  )

lazy val core = (project in file("modules/core"))
  .settings(
    name := "cdme-core",
    libraryDependencies ++= Dependencies.core
  )

lazy val config = (project in file("modules/config"))
  .dependsOn(core)
  .settings(
    name := "cdme-config",
    libraryDependencies ++= Dependencies.config
  )

lazy val registry = (project in file("modules/registry"))
  .dependsOn(core, config)
  .settings(
    name := "cdme-registry",
    libraryDependencies ++= Dependencies.registry
  )

lazy val compiler = (project in file("modules/compiler"))
  .dependsOn(core, registry)
  .settings(
    name := "cdme-compiler",
    libraryDependencies ++= Dependencies.compiler
  )

lazy val executor = (project in file("modules/executor"))
  .dependsOn(core, compiler)
  .settings(
    name := "cdme-executor",
    libraryDependencies ++= Dependencies.executor
  )

lazy val app = (project in file("modules/app"))
  .dependsOn(core, config, registry, compiler, executor)
  .enablePlugins(JavaAppPackaging)  // sbt-native-packager
  .settings(
    name := "cdme-app",
    libraryDependencies ++= Dependencies.app,

    // Assembly settings for fat JAR
    assembly / mainClass := Some("com.cdme.Main"),
    assembly / assemblyJarName := "cdme-spark.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )

lazy val integrationTests = (project in file("integration-tests"))
  .dependsOn(app)
  .settings(
    name := "cdme-integration-tests",
    libraryDependencies ++= Dependencies.integrationTests,
    publish / skip := true
  )

lazy val benchmarks = (project in file("benchmarks"))
  .dependsOn(app)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "cdme-benchmarks",
    libraryDependencies ++= Dependencies.benchmarks,
    publish / skip := true
  )
```

### 4. Dependencies Management

```scala
// project/Dependencies.scala
import sbt._

object Dependencies {
  // Versions (updated for Spark 4.0 - see ADR-011)
  val sparkVersion = "4.0.1"        // Was: 3.5.0
  val scalaVersion = "2.13.12"      // Was: 2.12.18
  val catsVersion = "2.10.0"
  val circeVersion = "0.14.6"
  val refinedVersion = "0.11.0"
  val scalaTestVersion = "3.2.17"
  val scalaCheckVersion = "1.17.0"

  // Spark
  val sparkCore = "org.apache.spark" %% "spark-core" % sparkVersion % Provided
  val sparkSql = "org.apache.spark" %% "spark-sql" % sparkVersion % Provided

  // Cats ecosystem
  val catsCore = "org.typelevel" %% "cats-core" % catsVersion
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.2"

  // Circe (JSON/YAML parsing)
  val circeCore = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeParser = "io.circe" %% "circe-parser" % circeVersion
  val circeYaml = "io.circe" %% "circe-yaml" % "0.14.2"

  // Refined types
  val refined = "eu.timepit" %% "refined" % refinedVersion
  val refinedCats = "eu.timepit" %% "refined-cats" % refinedVersion

  // Logging
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  val logback = "ch.qos.logback" % "logback-classic" % "1.4.11"

  // Testing
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  val scalaCheck = "org.scalacheck" %% "scalacheck" % scalaCheckVersion % Test
  val scalaTestCheck = "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test

  // Module dependencies
  val core = Seq(
    catsCore,
    refined,
    refinedCats,
    scalaLogging,
    scalaTest,
    scalaCheck,
    scalaTestCheck
  )

  val config = Seq(
    circeCore,
    circeGeneric,
    circeParser,
    circeYaml
  ) ++ core

  val registry = Seq() ++ core

  val compiler = Seq() ++ core

  val executor = Seq(
    sparkCore,
    sparkSql
  ) ++ core

  val app = Seq(
    logback
  ) ++ executor

  val integrationTests = Seq(
    sparkCore.withConfigurations(Some("test")),
    sparkSql.withConfigurations(Some("test")),
    scalaTest,
    scalaCheck
  )

  val benchmarks = Seq(
    "org.openjdk.jmh" % "jmh-core" % "1.37" % Test,
    "org.openjdk.jmh" % "jmh-generator-annprocess" % "1.37" % Test
  )
}
```

### 5. Plugins Configuration

```scala
// project/plugins.sbt
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")         // Fat JAR
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")  // Docker
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")        // Code formatting
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")       // Linting/refactoring
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.9")       // Code coverage
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.6")        // Benchmarking
```

### 6. Testing Framework: ScalaTest

```scala
// Example test structure
package com.cdme.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class CompilerSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  "Compiler" should "validate valid paths" in {
    val compiler = new Compiler(registry)
    val result = compiler.validatePath("Order.customer.name")

    result shouldBe Right(ValidPath(/* ... */))
  }

  it should "reject invalid paths" in {
    val compiler = new Compiler(registry)
    val result = compiler.validatePath("Order.invalid_relationship")

    result shouldBe Left(PathNotFound(/* ... */))
  }

  // Property-based test
  it should "reject all paths with invalid segments" in {
    forAll { (segment: String) =>
      whenever(!validSegments.contains(segment)) {
        val path = s"Order.$segment.field"
        val result = compiler.validatePath(path)

        result shouldBe a[Left[_, _]]
      }
    }
  }
}
```

**Why ScalaTest**:
- Multiple testing styles (FlatSpec, FunSpec, WordSpec, etc.)
- Excellent matchers library
- ScalaCheck integration for property-based testing
- Async testing support
- Wide adoption in Spark ecosystem

---

## Consequences

### Positive

- **Incremental compilation**: Fast iteration with sbt
- **Module independence**: Test and compile modules separately
- **Parallel builds**: Multi-module parallel compilation
- **Rich tooling**: sbt plugins for assembly, Docker, formatting, coverage
- **Property-based testing**: ScalaCheck catches edge cases
- **IDE support**: IntelliJ and VSCode have excellent sbt support

### Negative

- **sbt learning curve**: DSL can be confusing for beginners
- **Build complexity**: Multi-module builds require coordination
- **Dependency conflicts**: Spark provided dependencies can clash
- **Assembly size**: Fat JARs can be large (100+ MB)

### Mitigations

- **Documentation**: Provide sbt quick start guide
- **Build helpers**: Makefile wrapper for common tasks
- **Dependency management**: Use `provided` scope for Spark
- **Assembly optimization**: Exclude unnecessary libraries, use assembly merge strategies

---

## Build Commands

```bash
# Compile all modules
sbt compile

# Run tests
sbt test

# Run specific module tests
sbt core/test
sbt executor/test

# Run integration tests
sbt integrationTests/test

# Build fat JAR
sbt app/assembly

# Run locally
sbt "app/run config/cdme_config.yaml order_summary 2024-12-15"

# Package Docker image
sbt app/docker:publishLocal

# Code formatting
sbt scalafmtAll

# Code coverage
sbt clean coverage test coverageReport

# Benchmarks
sbt benchmarks/jmh:run
```

---

## CI/CD Integration

```yaml
# .github/workflows/ci.yml
name: CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: coursier/setup-action@v1
        with:
          jvm: temurin:21  # Updated for Spark 4.0 (was temurin:11)
          apps: sbt

      - name: Cache sbt
        uses: actions/cache@v3
        with:
          path: |
            ~/.sbt
            ~/.ivy2/cache
            ~/.coursier
          key: ${{ runner.os }}-sbt-${{ hashFiles('**/build.sbt') }}

      - name: Compile
        run: sbt compile

      - name: Test
        run: sbt coverage test coverageReport

      - name: Upload coverage
        uses: codecov/codecov-action@v3

      - name: Build assembly
        run: sbt app/assembly
```

---

## Alternatives Considered

### Alternative A: Maven (Rejected)

**Pros**: Enterprise standard, XML-based (familiar)
**Cons**: Slow incremental compilation, verbose XML, less Scala-native

### Alternative B: Mill (Rejected)

**Pros**: Simpler than sbt, faster
**Cons**: Smaller ecosystem, less plugin support, less Spark adoption

### Alternative C: Gradle (Rejected)

**Pros**: Flexible, Kotlin DSL
**Cons**: Scala support via plugin (not native), heavier runtime

---

## References

- [sbt Documentation](https://www.scala-sbt.org/1.x/docs/)
- [ScalaTest User Guide](https://www.scalatest.org/user_guide)
- [ScalaCheck Properties](https://www.scalacheck.org/)
- [sbt-assembly Plugin](https://github.com/sbt/sbt-assembly)
- [sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/)
- [Spark Scala Style Guide](https://spark.apache.org/contributing.html)

---

## Updates

- **2025-12-11**: Updated for Spark 4.0.1 migration (ADR-011)
  - Scala version: 2.12.18 → 2.13.12
  - Spark version: 3.5.0 → 4.0.1
  - Java requirement: 11+ → 17+
  - Added Scala 2.13 compiler options
