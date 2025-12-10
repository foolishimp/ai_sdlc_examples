# CDME Spark - Quick Start Guide

## Prerequisites

- **Java**: JDK 11 or higher
- **Scala**: 2.12.18 (managed by sbt)
- **sbt**: 1.9.7 or higher
- **Spark**: 3.5.x (provided at runtime)

## Installation

### 1. Install sbt

**macOS**:
```bash
brew install sbt
```

**Linux**:
```bash
curl -fL https://github.com/sbt/sbt/releases/download/v1.9.7/sbt-1.9.7.tgz | tar -xz
export PATH=$PATH:$PWD/sbt/bin
```

**Windows**:
Download from https://www.scala-sbt.org/download.html

### 2. Verify Installation

```bash
sbt --version
# Should show: sbt version 1.9.7
```

## Build

### Compile

```bash
cd /Users/jim/src/apps/ai_sdlc_examples/local_projects/data_mapper.test02
sbt compile
```

Expected output:
```
[info] Compiling 11 Scala sources to target/scala-2.12/classes ...
[success] Total time: 45 s
```

### Run Tests

```bash
sbt test
```

Expected output:
```
[info] CompilerSpec:
[info] SchemaRegistry
[info] - should validate valid paths
[info] - should reject invalid entity paths
[info] Run completed in X seconds.
[info] Total number of tests run: X
[info] Suites: completed X, aborted 0
[info] Tests: succeeded X, failed 0
```

### Build Fat JAR

```bash
sbt assembly
```

Output:
```
[success] Total time: 60 s
```

JAR location: `target/scala-2.12/cdme-spark.jar`

## Project Structure

```
data_mapper.test02/
├── build.sbt                           # Build definition
├── project/
│   ├── build.properties               # sbt version
│   └── plugins.sbt                    # Plugins
├── src/
│   ├── main/scala/cdme/
│   │   ├── core/                      # Core types
│   │   │   ├── Types.scala
│   │   │   ├── Domain.scala
│   │   │   └── Algebra.scala
│   │   ├── config/                    # Configuration
│   │   │   ├── ConfigModel.scala
│   │   │   └── ConfigLoader.scala
│   │   ├── registry/                  # Schema registry
│   │   │   └── SchemaRegistry.scala
│   │   ├── compiler/                  # Compiler
│   │   │   └── Compiler.scala
│   │   ├── executor/                  # Executor
│   │   │   ├── Executor.scala
│   │   │   └── morphisms/
│   │   │       ├── FilterMorphism.scala
│   │   │       └── AggregateMorphism.scala
│   │   └── Main.scala                 # Entry point
│   └── test/scala/cdme/
│       └── CompilerSpec.scala         # Tests
├── README.md                          # Documentation
├── IMPLEMENTATION_SUMMARY.md          # Implementation summary
└── QUICK_START.md                     # This file
```

## Running

### Local Mode (for testing)

```bash
sbt "run config/cdme_config.yaml mapping_name 2024-12-15"
```

### Spark Submit (cluster)

```bash
spark-submit \
  --class cdme.Main \
  --master yarn \
  --deploy-mode cluster \
  --num-executors 4 \
  --executor-memory 4G \
  --executor-cores 2 \
  target/scala-2.12/cdme-spark.jar \
  config/cdme_config.yaml \
  order_summary \
  2024-12-15
```

## Development Workflow

### 1. Make Changes

Edit files in `src/main/scala/cdme/`

### 2. Compile

```bash
sbt compile
```

### 3. Run Tests

```bash
sbt test
```

### 4. Format Code

```bash
sbt scalafmtAll
```

### 5. Check Coverage

```bash
sbt clean coverage test coverageReport
```

Coverage report: `target/scala-2.12/scoverage-report/index.html`

## Common sbt Commands

```bash
# Compile
sbt compile

# Run tests
sbt test

# Run specific test
sbt "testOnly cdme.CompilerSpec"

# Run continuous compilation (watches for changes)
sbt ~compile

# Run continuous testing
sbt ~test

# Build fat JAR
sbt assembly

# Clean build artifacts
sbt clean

# Show dependencies
sbt dependencyTree

# Start Scala REPL with project on classpath
sbt console

# Format code
sbt scalafmtAll

# Check code style
sbt scalafmtCheck
```

## IDE Setup

### IntelliJ IDEA

1. Install Scala plugin
2. Open project: File → Open → select `build.sbt`
3. Import project (use default settings)
4. Wait for dependencies to download
5. Right-click on `src/test/scala/cdme/CompilerSpec.scala` → Run

### VS Code

1. Install "Metals" extension
2. Open project folder
3. Metals will auto-detect sbt project
4. Wait for import to complete
5. Run tests via Test Explorer

### Vim/Neovim

1. Install coc-nvim or nvim-lspconfig
2. Install Metals language server
3. Open project, Metals will auto-detect

## Troubleshooting

### "Java heap space" error

Increase sbt memory:
```bash
export SBT_OPTS="-Xmx2G -Xss2M"
sbt compile
```

### "Unresolved dependency"

Clear Ivy cache:
```bash
rm -rf ~/.ivy2/cache
sbt update
```

### "Scala version mismatch"

Ensure Scala 2.12.18 is used:
```bash
sbt "show scalaVersion"
# Should output: [info] 2.12.18
```

### Tests fail with Spark error

Tests require Spark local mode. Check:
```bash
# Spark should be available
spark-submit --version
```

## Example Configuration

### Minimal cdme_config.yaml

```yaml
cdme:
  version: "1.0"
  registry:
    ldm_path: "config/ldm/"
    pdm_path: "config/pdm/"
  execution:
    mode: BATCH
    error_threshold: 0.05
    lineage_mode: BASIC
  output:
    data_path: "output/data/"
    error_path: "output/errors/"
    lineage_path: "output/lineage/"
```

### Minimal Entity (config/ldm/order.yaml)

```yaml
entity:
  name: Order
  grain:
    level: ATOMIC
    key: [order_id]
  attributes:
    - name: order_id
      type: String
      nullable: false
      primary_key: true
    - name: total_amount
      type: Decimal
      nullable: false
```

### Minimal Binding (config/pdm/order_binding.yaml)

```yaml
binding:
  entity: Order
  physical:
    type: PARQUET
    location: "data/orders/"
```

## Next Steps

1. **Add sample data** - Create test Parquet files
2. **Create mappings** - Define transformations
3. **Run steel thread** - Execute end-to-end
4. **Extend morphisms** - Add Traverse, Window
5. **Add integration tests** - Full Spark tests
6. **Deploy to cluster** - YARN, Kubernetes, etc.

## Resources

- **README.md** - Complete documentation
- **IMPLEMENTATION_SUMMARY.md** - Implementation details
- **Design docs** - `docs/design/design_spark/`
- **Spark docs** - https://spark.apache.org/docs/latest/
- **Cats docs** - https://typelevel.org/cats/
- **Circe docs** - https://circe.github.io/circe/

## Support

For issues:
1. Check logs: `sbt --debug compile`
2. Review design docs: `docs/design/design_spark/`
3. See ADRs: `docs/design/design_spark/adrs/`

---

**Version**: 1.0
**Last Updated**: 2025-12-10
