# CLAUDE.md — data_mapper.test06

## Project Overview

**Categorical Data Mapping & Computation Engine (CDME)** — a data mapping and computation engine built from first principles using Category Theory. Provides mathematical guarantees that if a mapping definition is valid, the resulting pipeline is topologically correct, lineage is preserved, and granularities are respected.

**Build**: test06 — single-shot build using AI SDLC v2.8 Genesis methodology
**Design variant**: scala_spark (single design, no parallel variants)
**Profile**: full (all edges traversed including TDD + BDD + UAT)

## Technology Stack

- **Language**: Scala 2.13.12
- **Runtime**: Apache Spark 3.5.0 (provided scope)
- **Build**: sbt multi-module (8 sub-projects)
- **Test**: ScalaTest 3.2.17, ScalaCheck 1.17.0
- **Style**: scalafmt

## Build Commands

```bash
sbt compile          # Compile all modules
sbt test             # Run all tests
sbt coverageOn test coverageReport   # Test with coverage
scalafmt --check .   # Check formatting
```

## Architecture (8 sbt Modules)

```
cdme-model/        → Pure domain: Category, Morphism, Entity, Grain, SemanticType (zero deps)
cdme-compiler/     → Topological compiler: path validation, grain checks, type safety (depends: model)
cdme-runtime/      → Execution engine: context/epoch management, accounting ledger (depends: compiler)
cdme-spark/        → Spark binding: DataFrame adapters, distributed execution (depends: runtime)
cdme-lineage/      → OpenLineage integration: traceability, audit trail (depends: runtime)
cdme-adjoint/      → Adjoint/Frobenius: backward traversal, impact analysis (depends: model)
cdme-ai-assurance/ → AI validation layer: hallucination detection, EU AI Act (depends: compiler)
cdme-api/          → REST/gRPC API: external interface (depends: all)
```

**Dependency rules**:
- `cdme-model` has zero external dependencies
- `cdme-compiler` depends on `cdme-model` only
- `cdme-spark` depends on `cdme-runtime`, never on `cdme-compiler` internals
- No global mutable state, no circular imports

**Patterns**: Category Theory (schemas as categories, transforms as functors), Hexagonal/ports-and-adapters, Compiler pattern (validate-then-execute)

## REQ Key Conventions

- Code: `// Implements: REQ-F-LDM-001` (in Scala comments)
- Tests: `// Validates: REQ-F-LDM-001`
- Commits: `feat: implement X — Implements: REQ-F-LDM-001`

75 requirements across domains: LDM, TRV, CTX, ACC, ERR, AIA, ADJ, OBS, SEC, NFR

## Specification Documents

- `specification/INTENT.md` — 6 intents (INT-001 through INT-006)
- `specification/mapper_requirements.md` — Product spec v7.2 (the axioms, ontology, abstract machine)
- `specification/REQUIREMENTS.md` — 75 formal requirements (REQ-F-*, REQ-NFR-*)
- `specification/appendices/APPENDIX_A_FROBENIUS_ALGEBRAS.md` — Theoretical foundation

## Key Domain Concepts

- **Schema = Topology**: Logical data model is a Category of Objects (Entities) and Morphisms (Relationships)
- **Transform = Functor**: LDM→PDM binding preserves structure
- **Grain = Dimension**: Mixing grains without explicit aggregation is topologically forbidden
- **Errors = Data**: Failed records map to Error Domain objects (dead letter queues)
- **Adjoint Morphisms**: Every morphism has a backward morphism forming a Galois Connection
- **AI Assurance**: Topological compiler rejects hallucinated AI-generated mappings

## Regulatory Compliance

BCBS 239, FRTB, GDPR/CCPA, EU AI Act — full lineage traceability required
