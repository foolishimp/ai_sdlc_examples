# AI SDLC Method Quick Reference

**Version:** 3.1.0
**Purpose:** Quick reference for AI SDLC methodology

---

## Core Principle

**"Session = Context. Context persists in ACTIVE_TASKS.md."**

---

## Workspace Structure

```
.ai-workspace/
├── tasks/
│   ├── active/
│   │   └── ACTIVE_TASKS.md        # Single file: tasks + status + summary
│   └── finished/                  # Completed task documentation
│       └── YYYYMMDD_HHMM_task_name.md
│
├── templates/                     # Templates for tasks
│   ├── TASK_TEMPLATE.md
│   ├── FINISHED_TASK_TEMPLATE.md
│   └── AISDLC_METHOD_REFERENCE.md (this file)
│
└── config/                        # Workspace configuration
```

---

## Workflow

### During Work
```bash
# Use TodoWrite tool to track progress
# Work on tasks from ACTIVE_TASKS.md
# Follow TDD for code: RED → GREEN → REFACTOR
```

### After Work (CRITICAL!)
```bash
/aisdlc-checkpoint-tasks
# Syncs tasks, creates finished docs, updates ACTIVE_TASKS.md
```

### Commit
```bash
/aisdlc-commit-task <id>
# Generates proper commit message with REQ tags
```

---

## Slash Commands

| When | Command |
|------|---------|
| After work | `/aisdlc-checkpoint-tasks` ⭐ |
| Finish task | `/aisdlc-finish-task <id>` |
| Commit | `/aisdlc-commit-task <id>` |
| Check status | `/aisdlc-status` |
| Release | `/aisdlc-release` |
| Help | `/aisdlc-help` |

---

## The 7 Key Principles (Code Stage)

1. **Test Driven Development** - RED → GREEN → REFACTOR → COMMIT
2. **Fail Fast & Root Cause** - Fix at source, no workarounds
3. **Modular & Maintainable** - Single responsibility
4. **Reuse Before Build** - Check existing first
5. **Open Source First** - Suggest alternatives
6. **No Legacy Baggage** - Start clean
7. **Perfectionist Excellence** - Excellence or nothing 🔥

---

## TDD Cycle

```
RED    → Write failing test first
GREEN  → Implement minimal solution
REFACTOR → Improve code quality
COMMIT → Save with REQ tags
```

---

## 7-Stage AI SDLC

```
Intent → Requirements → Design → Tasks → Code → System Test → UAT → Runtime Feedback
           ↑                                                                   ↓
           └────────────────────── Feedback Loop ─────────────────────────────┘
```

**Quick stage reference:**
1. Requirements → REQ-F-*, REQ-NFR-*, REQ-DATA-*
2. Design → Components, APIs, ADRs
3. Tasks → Tickets with REQ tags
4. **Code** → TDD (RED→GREEN→REFACTOR), tag with `# Implements: REQ-*` ⭐
5. System Test → BDD (Given/When/Then)
6. UAT → Business validation
7. Runtime Feedback → Telemetry → new intents

---

**"Excellence or nothing"** 🔥
