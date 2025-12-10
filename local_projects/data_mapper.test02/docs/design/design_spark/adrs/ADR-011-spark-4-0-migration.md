# ADR-011: Migration to Apache Spark 4.0.1

**Status**: Accepted
**Date**: 2025-12-11
**Deciders**: Design Agent
**Supersedes**: ADR-001 (Apache Spark as Execution Engine)
**Updates**: ADR-002 (Language Choice), ADR-006 (Scala Type System), ADR-009 (Project Structure)
**Implements**: All 60 REQ-* requirements (Spark variant)

---

## Context

The CDME Spark implementation was initially designed for Apache Spark 3.5.0 with Scala 2.12.18 and Java 8/11/17 support. However, several factors necessitate upgrading to Spark 4.0:

### Technical Drivers

1. **Java 25 Compatibility Issues**: Spark 3.5.x has compatibility issues with Java 25, limiting deployment on modern JVM runtimes
2. **Scala 2.13 Ecosystem**: The broader Scala ecosystem has largely migrated to Scala 2.13, with better library support and performance improvements
3. **Modern Java Features**: Java 17 and 21 LTS versions provide significant performance improvements and language features (pattern matching, records, virtual threads)
4. **Security & Support**: Java 8 has reached end-of-life for public updates; Spark 4.0 aligns with supported Java versions

### Spark 4.0 Key Features

1. **Scala 2.13 Required**: Scala 2.12 is no longer supported
2. **Java Support**:
   - Java 8 dropped (end-of-life)
   - Java 17 and 21 officially supported
   - Java 25 may be compatible (to be validated)
3. **ANSI SQL Mode**: Enabled by default for stricter SQL semantics
4. **VARIANT Data Type**: Native support for semi-structured data (JSON, etc.)
5. **Spark Connect**: Enabled by default in new tarball distribution for client-server architecture
6. **Performance Improvements**: Catalyst optimizer enhancements, better memory management
7. **API Stability**: Breaking changes from 3.x require migration effort

---

## Decision

**Upgrade to Apache Spark 4.0.1 with Scala 2.13 and Java 17/21 support**

### Technology Stack Changes

| Component | Before (Spark 3.5) | After (Spark 4.0) |
|-----------|-------------------|-------------------|
| Spark Version | 3.5.0 | 4.0.1 |
| Scala Version | 2.12.18 | 2.13.12 |
| Java Support | 8, 11, 17 | 17, 21 (25 TBD) |
| ANSI SQL | Optional | Default (can opt-out) |
| Spark Connect | Optional | Default-enabled |
| VARIANT Type | Not available | Available |

---

## Rationale

### 1. Future-Proof Platform

**Java 17/21 LTS Support**:
- Java 17 (LTS): Released Sep 2021, supported until Sep 2029
- Java 21 (LTS): Released Sep 2023, supported until Sep 2031
- Modern performance improvements: G1GC enhancements, ZGC, virtual threads (21+)
- Access to modern language features for better code quality

**Scala 2.13 Maturity**:
- Better collections performance (lazy views, optimized operations)
- Improved type inference
- Better macro ecosystem
- Libraries migrating away from 2.12 support

### 2. Spark 4.0 Feature Alignment

**ANSI SQL Mode Benefits for CDME**:
- Stricter type checking aligns with REQ-TYP-01 (type safety)
- Explicit error handling for type mismatches
- More predictable behavior for grain validation
- Can be disabled if needed: `spark.sql.ansi.enabled=false`

**VARIANT Data Type**:
- Useful for storing adjoint metadata as semi-structured data
- Better support for heterogeneous error objects in DLQ
- Potential use in future schema evolution scenarios

**Spark Connect**:
- Decouples client and server versions
- Enables remote execution for future cloud deployments
- Can be disabled for local/embedded mode

### 3. Performance & Optimization

Spark 4.0 includes:
- Catalyst optimizer improvements for complex joins
- Better memory management for aggregations
- Improved shuffle performance
- More efficient predicate pushdown (critical for grain filtering)

### 4. Migration Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Breaking API changes | Medium | Comprehensive testing, version-specific code paths if needed |
| Scala 2.13 migration | Medium | Automated scalafix rules, dependency updates |
| ANSI SQL strictness | Low | Explicit configuration, testing with ANSI mode |
| Build system changes | Low | sbt handles cross-compilation well |
| Third-party dependencies | Medium | Verify Cats, Circe, Refined compatibility with 2.13 |

---

## Migration Strategy

### Phase 1: Dependency Updates

```scala
// project/Dependencies.scala
object Dependencies {
  // Versions
  val sparkVersion = "4.0.1"              // Was: 3.5.0
  val scalaVersion = "2.13.12"            // Was: 2.12.18
  val catsVersion = "2.10.0"              // Already 2.13 compatible
  val circeVersion = "0.14.6"             // Already 2.13 compatible
  val refinedVersion = "0.11.0"           // Already 2.13 compatible
  val scalaTestVersion = "3.2.17"         // Already 2.13 compatible
  val scalaCheckVersion = "1.17.0"        // Already 2.13 compatible

  // All dependencies support Scala 2.13
}
```

### Phase 2: Scala 2.13 Code Migration

**Key Changes**:
1. **Collections**: `scala.collection.JavaConverters` → `scala.jdk.CollectionConverters`
2. **Lazy Views**: Explicit `.view` calls where needed
3. **Type Inference**: May require fewer explicit type annotations
4. **Deprecation Fixes**: Address Scala 2.13 deprecations

**Automated Migration**:
```bash
# Use scalafix for automated migration
sbt "scalafixAll dependency:Scala213Upgrade@org.scala-lang:scala-rewrites:0.1.4"
```

### Phase 3: Spark API Migration

**Identify Breaking Changes**:
- Review [Spark 4.0 Migration Guide](https://spark.apache.org/docs/4.0.0/migration-guide.html)
- Test all Spark operations (groupBy, join, window, aggregation)
- Validate custom Aggregator implementations
- Check Catalyst integration points

**Expected Changes**:
- Some DataFrame/Dataset API deprecations
- Behavioral changes in ANSI mode (type coercion, overflow handling)
- Potential changes to Catalyst LogicalPlan interfaces

### Phase 4: Testing & Validation

1. **Unit Tests**: All existing tests must pass
2. **Integration Tests**: Steel thread scenarios with Spark 4.0
3. **Performance Benchmarks**: Validate no performance regression
4. **ANSI SQL Mode Testing**: Test with both enabled/disabled
5. **Java 17/21 Testing**: Validate on both supported Java versions

---

## Consequences

### Positive

1. **Modern Platform**: Access to latest Java and Scala features
2. **Long-Term Support**: Java 17/21 LTS versions supported until 2029/2031
3. **Performance Gains**: Spark 4.0 optimizer improvements
4. **Type Safety**: ANSI SQL mode aligns with CDME correctness principles
5. **Future Features**: VARIANT type opens new design possibilities
6. **Security**: Modern Java versions receive security updates
7. **Community Support**: Active development and support for Spark 4.x

### Negative

1. **Migration Effort**:
   - Scala 2.13 code migration (estimated 2-3 days)
   - Dependency verification (1 day)
   - Testing across new platform (3-5 days)
2. **Breaking Changes**: Potential API changes require code updates
3. **ANSI SQL Learning Curve**: Stricter semantics may require adjustment
4. **Build System**: May need sbt version update
5. **Deployment**: Infrastructure must support Java 17/21

### Mitigations

1. **Comprehensive Testing**: Full test suite coverage before migration
2. **Feature Flags**: Allow ANSI SQL opt-out if needed
3. **Documentation**: Document all migration-related changes
4. **Rollback Plan**: Keep Spark 3.5 branch until Spark 4.0 validated
5. **Gradual Rollout**: Test in non-production environments first

---

## Configuration Changes

### ANSI SQL Mode Configuration

```yaml
# config/spark_config.yaml
spark:
  sql:
    ansi:
      enabled: true  # Default in Spark 4.0
      # Can override to false if needed for specific operations
```

**ANSI Mode Implications for CDME**:
- **Type Safety**: Stricter type checking in morphisms (beneficial for REQ-TYP-01)
- **Overflow Detection**: Aggregations fail on overflow instead of silently wrapping
- **Division by Zero**: Fails instead of returning null
- **Type Coercion**: More explicit, fewer implicit conversions

**Recommendation**: Keep ANSI mode enabled to align with CDME's type-safety goals. Add explicit error handling where needed.

### Spark Connect Configuration

```yaml
# config/spark_config.yaml
spark:
  connect:
    enabled: true  # Default in Spark 4.0
    # For local/embedded mode, can set to false
```

**Recommendation**: Keep enabled for future remote execution capabilities.

---

## Build System Updates

### Updated build.sbt

```scala
// build.sbt
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"  // Was: 2.12.18
ThisBuild / organization := "com.cdme"

// Java version requirement
ThisBuild / javacOptions ++= Seq("-source", "17", "-target", "17")

// Updated Scala compiler options for 2.13
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
```

### CI/CD Updates

```yaml
# .github/workflows/ci.yml
- uses: coursier/setup-action@v1
  with:
    jvm: temurin:21  # Was: temurin:11
    apps: sbt
```

---

## Validation Checklist

Before considering migration complete:

- [ ] All dependencies updated to Scala 2.13 compatible versions
- [ ] All unit tests pass on Spark 4.0.1
- [ ] Integration tests pass (steel thread scenarios)
- [ ] Performance benchmarks show no regression
- [ ] ANSI SQL mode tested (enabled and disabled)
- [ ] Custom Aggregator implementations work correctly
- [ ] Adjoint wrappers capture metadata correctly
- [ ] Error handling works with ANSI mode strictness
- [ ] Lineage collection still functional
- [ ] Documentation updated with Spark 4.0 specifics
- [ ] Java 17 testing complete
- [ ] Java 21 testing complete
- [ ] Java 25 compatibility validated (if applicable)

---

## Rollback Plan

If critical issues arise during migration:

1. **Code**: Revert to Spark 3.5 branch
2. **Dependencies**: Restore Scala 2.12.18, Spark 3.5.0
3. **Testing**: Re-run full test suite on 3.5
4. **Documentation**: Document issues encountered for future attempt

**Rollback Triggers**:
- Critical API incompatibilities
- Performance regression > 20%
- Unresolvable dependency conflicts
- Inability to compile with Scala 2.13

---

## Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1: Dependency Updates | 1 day | Updated build.sbt, Dependencies.scala |
| Phase 2: Scala 2.13 Migration | 2-3 days | Migrated source code, scalafix applied |
| Phase 3: Spark API Migration | 2-3 days | Updated Spark operations, API changes |
| Phase 4: Testing & Validation | 3-5 days | All tests passing, benchmarks validated |
| **Total** | **8-12 days** | Fully migrated CDME on Spark 4.0.1 |

---

## Alternatives Considered

### Alternative A: Stay on Spark 3.5 + Scala 2.12

**Pros**: No migration effort, stable platform
**Cons**: Java 25 incompatibility, Java 8 end-of-life, missing new features, eventual forced migration

**Decision**: Rejected - Technical debt accumulation, forced migration later more expensive

### Alternative B: Wait for Spark 4.1+

**Pros**: More mature Spark 4.x, potential bug fixes
**Cons**: Delays Java 25 support, misses Spark 4.0 improvements, uncertain timeline

**Decision**: Rejected - Spark 4.0 is stable, migration effort same regardless of 4.0 vs 4.1

### Alternative C: Partial Migration (Scala 2.13 only, stay Spark 3.5)

**Pros**: Incremental approach
**Cons**: Spark 3.5.x still on Scala 2.12 (official builds), doesn't solve Java 25 issue

**Decision**: Rejected - Doesn't address core compatibility issue

---

## References

- [Apache Spark 4.0 Release Notes](https://spark.apache.org/releases/spark-release-4-0-0.html)
- [Spark 4.0 Migration Guide](https://spark.apache.org/docs/4.0.0/migration-guide.html)
- [Scala 2.13 Migration Guide](https://docs.scala-lang.org/overviews/core/collections-migration-213.html)
- [Java 17 Features](https://openjdk.org/projects/jdk/17/)
- [Java 21 Features](https://openjdk.org/projects/jdk/21/)
- [Spark ANSI SQL Documentation](https://spark.apache.org/docs/4.0.0/sql-ref-ansi-compliance.html)
- [Spark VARIANT Type Documentation](https://spark.apache.org/docs/4.0.0/sql-ref-datatypes.html#variant-type)

---

## Related ADRs

- **ADR-001**: Apache Spark as Execution Engine (superseded by this ADR)
- **ADR-002**: Language Choice - Scala (updated with Scala 2.13)
- **ADR-006**: Scala Type System (validated with Scala 2.13 improvements)
- **ADR-009**: Scala Project Structure (updated build configuration)

---

**Document Status**: Accepted
**Last Updated**: 2025-12-11
