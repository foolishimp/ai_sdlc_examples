# Show Full Context State

Display the complete materialized context state with all configuration layers, merged values, and active persona.

## Usage

```
/show-full-context
```

This command provides complete transparency into the current context state.

## What You'll See

### Configuration Layer Stack
Shows which configuration files are active and their merge priority:
```
Layer 1: Corporate Base (01_corporate_base.yml)
Layer 2: Methodology (02_methodology_python.yml)
Layer 3: Project-Specific (03_project_payment_gateway.yml)
Layer 4: Persona: Security Engineer (if active)
```

### Merge Order
Shows the priority from lowest to highest:
```
Corporate Base → Methodology → Project-Specific → Persona
```

### Materialized Context
Shows the complete merged configuration including:
- **Project Information**: Name, team, classification
- **Testing Requirements**: Coverage, frameworks, test types
- **Coding Standards**: Style guides, complexity limits, linting
- **Security Requirements**: Vulnerability management, compliance
- **Quality Gates**: Maintainability, code smells, technical debt
- **Deployment Requirements**: Approval chains

### Active Persona
If a persona is active, shows:
- Persona name and role
- Focus areas
- Which overrides are applied

## Example Output

```
================================================================================
🎯 FULL CONTEXT STATE: payment_gateway
================================================================================

**Project Type**: standard
**Active Layers**: 4

## Configuration Layer Stack

**Merge Order**: Corporate Base → Methodology → Project-Specific → Persona: Security Engineer

### Layer 1: Corporate Base
- **File**: `01_corporate_base.yml`
- **Description**: Company-wide standards and policies
- **Path**: `/path/to/projects_repo/payment_gateway/01_corporate_base.yml`

### Layer 2: Methodology
- **File**: `02_methodology_python.yml`
- **Description**: Language/framework-specific standards
- **Path**: `/path/to/projects_repo/payment_gateway/02_methodology_python.yml`

### Layer 3: Project-Specific
- **File**: `03_project_payment_gateway.yml`
- **Description**: Project-specific requirements
- **Path**: `/path/to/projects_repo/payment_gateway/03_project_payment_gateway.yml`

### Layer 4: Persona: Security Engineer
- **File**: `persona_override`
- **Description**: Role-specific overrides for security_engineer
- **Path**: `runtime_memory`

## Active Persona

**Name**: Security Engineer
**Role**: security_engineer
**Focus Areas**:
  - Security testing
  - Vulnerability management
  - Compliance
  - Threat modeling

## Materialized Context (Merged Configuration)

### Project Information
- **Name**: Payment Gateway
- **Team**: Platform Team
- **Tech Lead**: Sarah Chen
- **Classification**: high
- **PCI Compliant**: True

### Testing Requirements
- **Minimum Coverage**: 95%
- **Framework**: pytest
- **Required Test Types**:
  - unit
  - integration
  - sast
  - dast
  - penetration

### Coding Standards
- **Style Guide**: PEP 8
- **Max Function Lines**: 50
- **Max Complexity**: 10
- **Linting Tools**: pylint, black, mypy

### Security Requirements

**Vulnerability Management**:
  - Scan Frequency: continuous
  - Critical Fix SLA: 4 hours
  - High Fix SLA: 24 hours

**Compliance Frameworks**:
  - PCI DSS
  - OWASP Top 10

### Quality Gates

**Code Quality**:
  - Min Maintainability: 65
  - Max Code Smells: 0
  - Max Technical Debt Ratio: 5%

### Deployment Requirements

**Approval Chain**:
  - tech_lead
  - security_team
  - compliance_officer

## Summary

- **Policies Loaded**: 3
- **Documentation Files**: 2
- **Configuration Layers**: 4

================================================================================
```

## Use Cases

### Understanding Active Configuration
```bash
# Load context
/load-context payment_gateway

# See complete state
/show-full-context
```

### Verifying Persona Application
```bash
# Load context and apply persona
/load-context payment_gateway
/apply-persona security_engineer

# Verify persona was applied
/show-full-context
# Should show "Layer 4: Persona: Security Engineer"
```

### Debugging Configuration
```bash
# If unexpected values, check which layers are active
/show-full-context
# Review merge order and materialized values
```

### Pre-Commit Review
```bash
# Before committing, verify active requirements
/show-full-context
# Review testing coverage, quality gates, etc.
```

## See Also

- `/current-context` - Quick summary of active context
- `/load-context` - Load a project context
- `/apply-persona` - Apply a persona to context
- `/switch-context` - Switch to different project
