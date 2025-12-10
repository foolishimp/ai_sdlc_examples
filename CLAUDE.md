# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This repository contains **example projects** demonstrating the AI SDLC Method - a 7-stage framework for AI-augmented software development with full requirement traceability from intent to runtime feedback.

## Directory Structure

```
ai_sdlc_examples/
├── local_projects/              # Example project configurations
│   ├── customer_portal/         # Complete 7-stage example with homeostatic control
│   ├── api_platform/            # Public API with backwards compatibility overrides
│   ├── payment_gateway/         # Payment processing with security focus
│   ├── acme_corporate/          # Corporate standards example
│   ├── admin_dashboard/         # Admin UI example
│   └── data_mapper.test*/       # Category theory data mapper examples
│
├── merged_projects/             # Merged configurations for deployment
│   └── payment_gateway_prod_v1_0_0/  # Production-ready merged config
│
└── README.md
```

## Architecture: Federated Plugin System

The AI SDLC uses a layered plugin architecture for configuration:

```
Layer 0: Foundation (aisdlc-core)
Layer 1: Methodology (aisdlc-methodology) - 7-stage SDLC
Layer 2: Language standards (python-standards)
Layer 3: Corporate/Division plugins (organizational overrides)
Layer 4: Project plugins (local customizations)
```

**Merge Priority**: Later plugins override earlier ones.

## 7-Stage AI SDLC Pipeline

```
Intent → Requirements → Design → Tasks → Code → System Test → UAT → Runtime Feedback
           ↑                                                                   ↓
           └────────────────────── Feedback Loop ─────────────────────────────┘
```

1. **Requirements** - Intent → structured requirements (REQ-F-*, REQ-NFR-*, REQ-DATA-*)
2. **Design** - Requirements → components, APIs, ADRs
3. **Tasks** - Work breakdown with Jira orchestration
4. **Code** - TDD implementation (RED → GREEN → REFACTOR)
5. **System Test** - BDD integration testing (Given/When/Then)
6. **UAT** - Business validation
7. **Runtime Feedback** - Production telemetry feeding back to new intents

## Key Principles (Code Stage)

1. **Test Driven Development** - RED → GREEN → REFACTOR → COMMIT
2. **Fail Fast & Root Cause** - Fix at source, no workarounds
3. **Modular & Maintainable** - Single responsibility principle
4. **Reuse Before Build** - Check existing solutions first
5. **Open Source First** - Leverage community solutions
6. **No Legacy Baggage** - Start clean, avoid technical debt
7. **Perfectionist Excellence** - Build best-of-breed only

## Working with Example Projects

Each project in `local_projects/` contains:

- `.ai-workspace/` - Task tracking and session management
- `.claude/` - Claude Code commands and agents
- `config/config.yml` - Project-specific AI SDLC configuration
- `CLAUDE.md` - Project-specific guidance

### Project Structure Convention

```
project/
├── .ai-workspace/
│   ├── tasks/
│   │   ├── active/ACTIVE_TASKS.md    # Current work
│   │   └── finished/                  # Completed task docs
│   └── templates/                     # Task templates
├── .claude/
│   ├── commands/                      # Slash commands
│   └── agents/                        # Agent configurations
└── config/config.yml                  # AI SDLC stage config
```

### Available Slash Commands (per project)

- `/aisdlc-checkpoint-tasks` - Sync task state with actual work
- `/aisdlc-finish-task <id>` - Complete task with documentation
- `/aisdlc-commit-task <id>` - Generate proper commit message
- `/aisdlc-status` - Show current task queue
- `/aisdlc-release` - Deploy framework to projects
- `/aisdlc-refresh-context` - Refresh methodology context

## Requirement Traceability

All stages propagate requirement keys:

```yaml
# Requirements Stage
REQ-F-AUTH-001 created

# Code Stage
# auth.py
# Implements: REQ-F-AUTH-001

# System Test Stage
# test_auth.feature
# Validates: REQ-F-AUTH-001

# Git Commits
feat: Add auth flow
Implements: REQ-F-AUTH-001

# Runtime Feedback
ERROR: REQ-F-AUTH-001 - Auth failure rate 5%
```

## TDD Workflow

```
RED    → Write failing test first
GREEN  → Implement minimal solution
REFACTOR → Improve code quality
COMMIT → Save with REQ tags
```

**No code without tests. Ever.**

## Merged Projects

`merged_projects/` contains configurations merged from multiple source projects for deployment:

```json
// .merge_info.json
{
  "source_projects": ["acme_corporate", "python_standards", "payment_gateway"],
  "runtime_overrides": { "environment": "production" }
}
```

## Key Files Reference

| Purpose | Location |
|---------|----------|
| Active tasks | `.ai-workspace/tasks/active/ACTIVE_TASKS.md` |
| Finished tasks | `.ai-workspace/tasks/finished/YYYYMMDD_HHMM_*.md` |
| Task templates | `.ai-workspace/templates/` |
| Methodology reference | `.ai-workspace/templates/AISDLC_METHOD_REFERENCE.md` |
| Project config | `config/config.yml` |
| Slash commands | `.claude/commands/` |
