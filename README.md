# Example Projects - Claude Code Plugin Usage

This directory contains **example projects** demonstrating how to use ai_sdlc_method with Claude Code plugins and the 7-stage AI SDLC methodology.

**Version**: 3.0.0

## Directory Structure

```
examples/
├── local_projects/              # Example project configurations
│   ├── customer_portal/        # ⭐ Complete 7-stage example with homeostatic control
│   ├── api_platform/           # Public API with backwards compatibility
│   ├── data_mapper.test01/     # Simple data mapping example
│   └── data_mapper.test02/     # Category theory data mapper (dogfooding)
│
└── README.md                   # This file
```

---

## Philosophy: Federated Plugin Architecture

The AI SDLC uses **Claude Code plugin marketplaces** for federated configuration management:

```
Corporate Marketplace (GitHub: acme-corp/claude-contexts)
  └─ aisdlc-core, aisdlc-methodology, python-standards, security-baseline
       ↓
Division Marketplace (Git: eng-division/plugins)
  └─ backend-standards, microservices-patterns
       ↓
Team/Project Plugin (Local: .claude-claude-code/claude-code/plugins/)
  └─ team-conventions, project-context
```

**Plugin Loading Order** = **Merge Priority** (later plugins override earlier ones)

---

## Using the AI SDLC Plugins

**For Claude Code Users** (Recommended):

**Advantages**:
- ✅ Native Claude Code integration
- ✅ Auto-updates via marketplaces
- ✅ Simpler setup (no Python required)
- ✅ Federated via multiple marketplaces
- ✅ Version control built-in

**Setup**:
```bash
# Add marketplace
/plugin marketplace add foolishimp/ai_sdlc_method

# Install plugins
/plugin install @aisdlc/aisdlc-core
/plugin install @aisdlc/aisdlc-methodology
/plugin install @aisdlc/startup-bundle
```

---

## Federated Plugin Architecture

### Concept

Multiple plugin marketplaces compose to provide layered configuration:

```
┌────────────────────────────────────────────────────────────┐
│ Your Development Environment (Claude Code)                 │
│                                                             │
│ Plugin Marketplaces:                                        │
│ ├─ corporate (github:acme-corp/claude-contexts)           │
│ ├─ division  (git:eng-division/plugins)                   │
│ └─ local     (local:./.claude-plugins)                    │
└────────────────────────────────────────────────────────────┘
```

### Plugin Composition

Plugins are loaded as **layers** with priority-based merging:

```json
// .claude/settings.json
{
  "extraKnownMarketplaces": {
    "corporate": {
      "source": {"source": "github", "repo": "acme-corp/claude-contexts"}
    },
    "division": {
      "source": {"source": "git", "url": "https://git.company.com/eng/plugins.git"}
    },
    "local": {
      "source": {"source": "local", "path": "./.claude-plugins"}
    }
  },
  "plugins": [
    "@corporate/aisdlc-core",              // Layer 0: Foundation
    "@corporate/aisdlc-methodology",       // Layer 1: Base methodology
    "@corporate/python-standards",         // Layer 2: Language standards
    "@division/backend-standards",         // Layer 3: Division overrides
    "@local/team-conventions",             // Layer 4: Team customizations
    "@local/my-project-context"            // Layer 5: Project-specific
  ]
}
```

**Merge Order**: `corporate` → `division` → `local` (later overrides earlier)

---

## Usage Examples

### Example 1: Startup Bundle (Quick Start)

```bash
# In Claude Code, add marketplace and install startup bundle
/plugin marketplace add foolishimp/ai_sdlc_method
/plugin install @aisdlc/startup-bundle

# Startup bundle includes:
# - aisdlc-core (traceability foundation)
# - code-skills (TDD workflow)
# - principles-key (Seven Principles)
```

### Example 2: Enterprise Bundle (Full 7-Stage SDLC)

```bash
# Install enterprise bundle for complete lifecycle
/plugin install @aisdlc/enterprise-bundle

# Enterprise bundle includes:
# - aisdlc-core (foundation)
# - requirements-skills
# - design-skills
# - code-skills
# - testing-skills
# - runtime-skills
# - principles-key
```

### Example 3: Corporate + Division + Project (Federated)

```json
// .claude/settings.json
{
  "extraKnownMarketplaces": {
    "corporate": {
      "source": {"source": "github", "repo": "acme-corp/claude-contexts"}
    },
    "division": {
      "source": {"source": "git", "url": "https://git.company.com/eng/plugins.git"}
    }
  },
  "plugins": [
    "@corporate/aisdlc-core",           // Corporate foundation
    "@corporate/aisdlc-methodology",    // Corporate 7-stage SDLC
    "@corporate/python-standards",      // Corporate Python standards
    "@division/backend-standards",      // Division overrides
    "@aisdlc/startup-bundle"            // Add AI SDLC skills
  ]
}
```

---

## Example Projects Explained

### customer_portal/ ⭐ **Complete 7-Stage AI SDLC Example**

**Purpose**: Demonstrates **complete 7-stage AI SDLC methodology** with homeostatic control and full requirement traceability

**Plugins Used**:
- `@aisdlc/aisdlc-core` v3.0.0
- `@aisdlc/aisdlc-methodology` v2.0.0
- `@aisdlc/python-standards` v1.0.0

**Demonstrates**:
- ✅ All 7 stages: Requirements → Design → Tasks → Code → System Test → UAT → Runtime Feedback
- ✅ Requirement key propagation (REQ-F-*, REQ-NFR-*, REQ-DATA-*)
- ✅ Homeostatic control (sensors detect gaps, actuators fix them)
- ✅ TDD workflow (RED → GREEN → REFACTOR)
- ✅ BDD testing (Given/When/Then scenarios)
- ✅ Bidirectional traceability (Intent ↔ Runtime)
- ✅ Agent orchestration and feedback loops

**Files**:
- `config/config.yml` - Complete 7-stage agent configuration (650+ lines)
- `README.md` - Detailed walkthrough (800+ lines)
- `INTENT.md` - Business problem statement

**Use case**: **Start here** to understand the complete AI SDLC methodology

👉 See [customer_portal/README.md](local_projects/customer_portal/README.md) for detailed walkthrough

---

### api_platform/

**Purpose**: Public API with backwards compatibility requirements
**Plugins**: `aisdlc-methodology`, `python-standards`
**Special**: Overrides Principle #6 (No Legacy Baggage) using feature flags

**Use case**: Customer-facing APIs requiring backwards compatibility

### data_mapper.test02/

**Purpose**: Category theory-based data mapper (dogfooding AI SDLC)
**Plugins**: `enterprise-bundle`
**Status**: Active development using AI SDLC methodology

**Use case**: Advanced data transformation using category theory

---

## Project Initialization Workflow

### Step 1: Add Marketplaces

```bash
# In Claude Code, add marketplaces
/plugin marketplace add foolishimp/ai_sdlc_method

# If your company has a marketplace:
# Add to .claude/settings.json:
{
  "extraKnownMarketplaces": {
    "corporate": {
      "source": {"source": "github", "repo": "acme-corp/claude-contexts"}
    }
  }
}
```

### Step 2: Install Base Plugins

```bash
# Choose a bundle based on your needs:

# For solo developers / startups:
/plugin install @aisdlc/startup-bundle

# For data science teams:
/plugin install @aisdlc/datascience-bundle

# For QA teams:
/plugin install @aisdlc/qa-bundle

# For enterprise teams (full 7-stage SDLC):
/plugin install @aisdlc/enterprise-bundle

# Or install individual plugins:
/plugin install @aisdlc/aisdlc-core
/plugin install @aisdlc/aisdlc-methodology
/plugin install @aisdlc/python-standards
```

### Step 3: Create Project Plugin (Optional)

```bash
# For project-specific customizations:
mkdir -p .claude-claude-code/claude-code/plugins/my-project-context/.claude-plugin
mkdir -p .claude-claude-code/claude-code/plugins/my-project-context/config

# Create plugin.json
cat > .claude-claude-code/claude-code/plugins/my-project-context/.claude-plugin/plugin.json <<EOF
{
  "name": "my-project-context",
  "version": "1.0.0",
  "displayName": "My Project Context",
  "dependencies": {
    "aisdlc-core": "^3.0.0",
    "aisdlc-methodology": "^2.0.0"
  }
}
EOF

# Create project config
cat > .claude-claude-code/claude-code/plugins/my-project-context/config/context.yml <<EOF
project:
  name: "my-api-service"
  type: "api_service"
  risk_level: "medium"

ai_sdlc:
  methodology_plugin: "file://../../claude-code/claude-code/plugins/aisdlc-methodology/config/stages_config.yml"

  enabled_stages:
    - requirements
    - design
    - code
    - system_test

  stages:
    code:
      testing:
        coverage_minimum: 90  # Override baseline 80%
EOF

# Install local plugin
/plugin marketplace add ./.claude-plugins
/plugin install my-project-context
```

### Step 4: Start Development

```bash
# Claude Code now has access to:
# - aisdlc-core (foundation with homeostatic control)
# - aisdlc-methodology (7-stage SDLC)
# - python-standards (language standards)
# - my-project-context (project-specific overrides)

# Create INTENT.md and start the 7-stage workflow
```

---

## Creating Your Own Project Plugin

### 1. Start with Example

Copy an example project and customize:

```bash
cp -r examples/local_projects/customer_portal my-project
cd my-project

# Edit config/config.yml for project-specific settings
# Update INTENT.md with your business problem
```

### 2. Create Plugin Structure

```bash
mkdir -p .claude-plugin
cat > .claude-plugin/plugin.json <<EOF
{
  "name": "my-project",
  "version": "1.0.0",
  "displayName": "My Project",
  "dependencies": {
    "aisdlc-core": "^3.0.0",
    "aisdlc-methodology": "^2.0.0",
    "python-standards": "^1.0.0"
  }
}
EOF
```

### 3. Install and Use

```bash
# Add as local marketplace
/plugin marketplace add /path/to/my-project
/plugin install my-project
```

---

## Best Practices

### 1. Layer Your Plugins

- **Layer 0**: Foundation (`aisdlc-core`)
- **Layer 1**: Methodology (`aisdlc-methodology`)
- **Layer 2**: Language standards (`python-standards`)
- **Layer 3**: Organizational policies (corporate plugins)
- **Layer 4**: Division/team customizations (division plugins)
- **Layer 5**: Project-specific config (local plugins)

### 2. Use Appropriate Marketplaces

- **Corporate marketplace**: Baseline standards, managed by IT
- **Division marketplace**: Team-specific, managed by team leads
- **Local plugins**: Project configs, full control

### 3. Override Strategically

Don't override everything - only customize what's needed:

```yaml
# Good: Specific override
ai_sdlc:
  stages:
    code:
      testing:
        coverage_minimum: 95  # Higher than baseline 80%

# Bad: Copying entire config
# (defeats purpose of plugin inheritance)
```

### 4. Document Overrides

```yaml
# Always explain why you're overriding
ai_sdlc:
  stages:
    code:
      testing:
        coverage_minimum: 95  # Payment processing requires higher coverage
```

### 5. Use Bundles for Common Setups

Instead of installing plugins individually, use bundles:
- `startup-bundle` - Solo developers, quick projects
- `datascience-bundle` - ML/data science teams
- `qa-bundle` - QA-focused teams
- `enterprise-bundle` - Full governance

---

## Troubleshooting

### Plugin Not Found

```
Error: Plugin '@aisdlc/aisdlc-core' not found
```

**Solution**: Check marketplace is added:
```bash
/plugin marketplace list
/plugin marketplace add foolishimp/ai_sdlc_method
```

### Version Conflicts

```
Warning: Plugin version conflict for 'aisdlc-core'
```

**Solution**: Update plugins to compatible versions:
```bash
/plugin update @aisdlc/aisdlc-core
/plugin update @aisdlc/aisdlc-methodology
```

### Configuration Merge Issues

```
Warning: Conflicting values at path 'testing.coverage_minimum'
```

**Solution**: Check plugin loading order in `.claude/settings.json`

---

## Next Steps

1. **Try the example**: See [customer_portal/](local_projects/customer_portal/) - Complete 7-stage walkthrough
2. **Install plugins**: Use `/plugin install` with bundles or individual plugins
3. **Create project plugin**: Customize for your project needs
4. **Set up CI/CD**: Integrate AI SDLC into your deployment pipeline
5. **Enable homeostatic control**: Let sensors/actuators maintain quality automatically

For more information:
- [Plugin Guide](../PLUGIN_GUIDE.md) - How to create plugins
- [Plugins Documentation](../claude-code/claude-code/plugins/README.md) - All available plugins
- [Complete Methodology](../docs/ai_sdlc_method.md) - 7-stage SDLC reference

---

**"Excellence or nothing"** 🔥
