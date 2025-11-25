# Data Mapper - ETL Configuration System

**Example Project for AI SDLC Method v3.0**

---

## Overview

This is an **example project** demonstrating how to use the **AI SDLC Method** (7-stage lifecycle) to build a data transformation system.

**What is Data Mapper?**
A flexible, configuration-based ETL (Extract, Transform, Load) system that allows data engineers to define data transformations using declarative YAML configurations instead of writing custom code.

---

## Project Purpose

This example demonstrates:

✅ **Complete 7-Stage Lifecycle** - From intent to production monitoring
✅ **Folder-Based Asset Discovery** - Requirements, designs, tasks in `.ai-workspace/`
✅ **Requirement Traceability** - Track transformations through all stages
✅ **TDD Workflow** - RED → GREEN → REFACTOR for all code
✅ **BDD Testing** - Given/When/Then scenarios for integration tests
✅ **Performance Focus** - 10,000 records/second target
✅ **Data Quality** - Built-in validation framework

---

## Getting Started

### Prerequisites

- Python 3.9+
- AI SDLC Method installed
- Claude Code (recommended)

### Setup

```bash
# 1. Navigate to project
cd examples/local_projects/data_mapper

# 2. Install AI SDLC workspace and commands
python /path/to/ai_sdlc_method/installers/setup_all.py

# 3. Review the intent
cat INTENT.md

# 4. Start the AI SDLC process
# See RUN_AI_SDLC.md for step-by-step guide
```

---

## Project Structure

```
data_mapper/
  │
  ├─ INTENT.md                 # Original business intent (INT-001)
  ├─ README.md                 # This file
  ├─ RUN_AI_SDLC.md           # Step-by-step execution guide
  ├─ project.json              # Project metadata
  │
  ├─ config/
  │   └─ config.yml            # AI SDLC configuration (7 stages)
  │
  ├─ .ai-workspace/            # Hidden AI SDLC folder
  │   ├─ requirements/         # Generated requirements (REQ-*)
  │   ├─ designs/              # Architecture & design docs
  │   ├─ tasks/                # Work breakdown
  │   ├─ tests/                # Test plans & scenarios
  │   ├─ runtime/              # Monitoring & alerts
  │   └─ traceability/         # Requirement → code mappings
  │
  ├─ src/                      # Implementation (TDD)
  │   ├─ schema_discovery/     # Auto-detect schemas
  │   ├─ mapping_engine/       # Core transformation engine
  │   ├─ transformations/      # Built-in transforms
  │   ├─ validators/           # Validation framework
  │   └─ cli/                  # Command-line interface
  │
  └─ tests/                    # Tests (unit + integration)
      ├─ unit/                 # TDD unit tests
      ├─ integration/          # BDD integration tests
      └─ fixtures/             # Test data
```

---

## The Intent (INT-001)

**Problem**: Data engineers spend days writing custom transformation code for each new data source.

**Solution**: Configuration-based data mapping system where transformations are defined in YAML.

**Example Transformation**:

```yaml
# Input: {"customer_id": "C12345", "full_name": "John Doe"}
# Output: {"id": 12345, "name": "JOHN DOE"}

mappings:
  - source: customer_id
    target: id
    transforms:
      - type: remove_prefix
        value: "C"
      - type: to_integer

  - source: full_name
    target: name
    transforms:
      - type: trim
      - type: uppercase
```

---

## 7-Stage AI SDLC Flow

### 1. Requirements Stage
**Agent**: AISDLC Requirements Agent
**Output**: `.ai-workspace/requirements/`

Generate structured requirements:
- `functional/schema-discovery.md` (REQ-F-SCHEMA-001)
- `functional/mapping-engine.md` (REQ-F-MAP-001)
- `functional/transformations.md` (REQ-F-TRANS-001)
- `non-functional/performance.yml` (REQ-NFR-PERF-001: 10k records/sec)
- `non-functional/scalability.yml` (REQ-NFR-SCALE-001: 10GB files)
- `business-rules/transformation-rules.md` (BR-001: type conversions)

### 2. Design Stage
**Agent**: Design Agent
**Output**: `.ai-workspace/designs/`

Create technical architecture:
- `data-mapper-architecture.md` - Component design
- `transformation-pipeline.mermaid` - Data flow diagram
- `api-spec.yml` - Python API design
- `data-models/` - Schema definitions

### 3. Tasks Stage
**Agent**: Tasks Agent
**Output**: `.ai-workspace/tasks/`

Break into work units:
- `active/task-001-schema-discovery.md` → REQ-F-SCHEMA-001
- `active/task-002-mapping-engine.md` → REQ-F-MAP-001
- `active/task-003-transformations.md` → REQ-F-TRANS-001
- `active/task-004-validation.md` → REQ-F-VAL-001
- `active/task-005-performance.md` → REQ-NFR-PERF-001

### 4. Code Stage
**Agent**: Code Agent
**Methodology**: TDD (RED → GREEN → REFACTOR)

Example TDD cycle:
```python
# RED: Write failing test
def test_remove_prefix_transform():
    # Validates: .ai-workspace/requirements/functional/transformations.md
    input_value = "C12345"
    transform = RemovePrefixTransform(prefix="C")
    result = transform.apply(input_value)
    assert result == "12345"  # FAILS (not implemented yet)

# GREEN: Implement minimal code
class RemovePrefixTransform:
    def __init__(self, prefix):
        self.prefix = prefix

    def apply(self, value):
        if value.startswith(self.prefix):
            return value[len(self.prefix):]
        return value
    # TEST PASSES

# REFACTOR: Improve quality
class RemovePrefixTransform:
    """Remove prefix from string value.

    Implements: .ai-workspace/requirements/functional/transformations.md
    """
    def __init__(self, prefix: str):
        self.prefix = prefix

    def apply(self, value: str) -> str:
        """Apply transformation."""
        if not isinstance(value, str):
            raise TypeError(f"Expected str, got {type(value)}")
        return value.removeprefix(self.prefix)  # Python 3.9+
    # TEST STILL PASSES

# COMMIT
git commit -m "feat: Add RemovePrefixTransform

Implements: .ai-workspace/requirements/functional/transformations.md

- Add RemovePrefixTransform class
- Add unit tests
- Coverage: 100%"
```

### 5. System Test Stage
**Agent**: System Test Agent
**Methodology**: BDD (Given/When/Then)

Example BDD scenario:
```gherkin
# tests/integration/mapping.feature

Feature: Data Mapping
  # Validates: .ai-workspace/requirements/functional/mapping-engine.md

  Scenario: Transform customer data
    Given a source JSON file "customers.json":
      """
      {"customer_id": "C12345", "full_name": "John Doe"}
      """
    And a mapping configuration "customer_mapping.yml":
      """
      mappings:
        - source: customer_id
          target: id
          transforms:
            - type: remove_prefix
              value: "C"
            - type: to_integer
      """
    When I run the mapping engine
    Then the output should contain:
      """
      {"id": 12345}
      """
    And the transformation should complete in < 100ms
```

### 6. UAT Stage
**Agent**: AISDLC UAT Agent
**Output**: Business validation test cases

Data team validates with real-world data:
- Load actual customer files
- Verify transformations match expectations
- Sign-off on data quality

### 7. Runtime Feedback Stage
**Agent**: AISDLC Runtime Feedback Agent
**Output**: Production monitoring

Tag all metrics with requirement keys:
```python
# src/mapping_engine/engine.py

# Implements: .ai-workspace/requirements/functional/mapping-engine.md
logger.info("Mapping completed", extra={
    "requirement": ".ai-workspace/requirements/functional/mapping-engine.md",
    "records_processed": 10000,
    "duration_ms": 950,
    "success_rate": 0.98
})
```

Alert on violations:
```yaml
# .ai-workspace/runtime/alerts/performance-sla.yml

alert:
  name: "Mapping Performance Degradation"
  requirement: ".ai-workspace/requirements/non-functional/performance.yml"
  condition: "records_per_second < 10000"
  action: "Generate INT-002: Optimize mapping performance"
```

---

## Key Features Demonstrated

### 1. Folder-Based Asset Discovery
All assets in `.ai-workspace/` are automatically discovered:
- Requirements: `.ai-workspace/requirements/**/*.md`
- Designs: `.ai-workspace/designs/**/*.md`
- Tasks: `.ai-workspace/tasks/active/*.md`

No hardcoded requirement keys (like `REQ-F-001`) - just file paths!

### 2. Requirement Traceability
Every artifact references requirement files:
```python
# src/transformations/remove_prefix.py

# Implements: .ai-workspace/requirements/functional/transformations.md
class RemovePrefixTransform:
    ...
```

### 3. TDD Workflow
All code follows RED → GREEN → REFACTOR:
1. Write failing test
2. Implement minimal code
3. Refactor for quality
4. Commit with requirement reference

### 4. Performance as a Requirement
Performance targets are first-class requirements:
- `REQ-NFR-PERF-001`: 10,000 records/second
- Validated in tests
- Monitored in production

---

## Running the Example

### Option 1: Follow the Complete 7-Stage Process

```bash
# See RUN_AI_SDLC.md for step-by-step guide
cat RUN_AI_SDLC.md
```

### Option 2: Install and Explore

```bash
# Install AI SDLC workspace
python /path/to/ai_sdlc_method/installers/setup_all.py

# Explore generated structure
tree .ai-workspace/

# Try slash commands
/todo "Implement schema auto-detection"
/start-session
```

### Option 3: Build It Yourself

Use this as a template and build your own data mapper:
1. Review INTENT.md
2. Generate requirements using Requirements Agent
3. Design architecture using Design Agent
4. Implement using TDD
5. Test using BDD
6. Monitor in production

---

## Learning Path

**For Developers**:
1. Read INTENT.md (understand the problem)
2. Review .ai-workspace/requirements/ (structured requirements)
3. Study TDD examples in src/ + tests/
4. See BDD scenarios in tests/integration/

**For Architects**:
1. Read INTENT.md
2. Review .ai-workspace/designs/ (architecture)
3. Study component diagrams and API specs

**For QA Engineers**:
1. Review .ai-workspace/requirements/
2. Study BDD scenarios in .ai-workspace/tests/
3. See integration tests

**For Product Owners**:
1. Read INTENT.md
2. Review .ai-workspace/requirements/ (acceptance criteria)
3. See UAT test cases

---

## Documentation

- **INTENT.md** - Original business intent
- **RUN_AI_SDLC.md** - Step-by-step execution guide
- **config/config.yml** - AI SDLC 7-stage configuration
- **.ai-workspace/requirements/** - All requirements
- **.ai-workspace/designs/** - Architecture docs
- **docs/** - Additional documentation

---

## Contributing

This is an example project for demonstration. To adapt for your own use:

1. Copy this template
2. Modify INTENT.md with your requirements
3. Run through 7-stage AI SDLC
4. Build your own system

---

## Related Examples

- **customer_portal** - Authentication system example
- More examples coming soon!

---

**"Excellence or nothing"** 🔥
