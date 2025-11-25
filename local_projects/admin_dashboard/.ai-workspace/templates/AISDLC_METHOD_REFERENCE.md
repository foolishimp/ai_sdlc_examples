# AI SDLC Context Refresh for Claude

**Version:** 3.0.0 (v0.1.4 Implicit Session Model)
**Purpose:** Load this to refresh Claude's context with AI SDLC methodology
**When to use:** After context loss, before any work, after violations

---

## 🎯 Core Principle

**"Session = Context. Context persists in ACTIVE_TASKS.md."**

**How to use this document:**
1. Read after making mistakes
2. Read when unsure of workflow
3. Reference quick cards at bottom for fast lookup
4. **Note**: You DON'T need to read this at session start - CLAUDE.md auto-loads context

---

## 📁 Workspace Structure (NEVER VIOLATE THIS!)

```
.ai-workspace/
├── tasks/
│   ├── active/
│   │   └── ACTIVE_TASKS.md        # Single file: tasks + status + summary
│   ├── finished/                  # Completed task documentation
│   │   └── YYYYMMDD_HHMM_task_name.md
│   └── archive/                   # Old completed tasks
│
├── templates/                     # Templates for tasks
│   ├── TASK_TEMPLATE.md
│   ├── FINISHED_TASK_TEMPLATE.md
│   ├── AISDLC_METHOD_REFERENCE.md (this file)
│   └── deprecated/                # Archived session templates
│       ├── SESSION_STARTER.md
│       └── SESSION_TEMPLATE.md
│
└── config/                        # Workspace configuration
```

**CRITICAL RULES:**
- ✅ **DO**: Put finished tasks in `.ai-workspace/tasks/finished/`
- ❌ **DON'T**: Put tasks in `docs/`, `finished_tasks/`, or anywhere else
- ✅ **DO**: Use `TodoWrite` tool for task tracking during work
- ❌ **DON'T**: Create task files manually without following workflow
- ✅ **DO**: Update ACTIVE_TASKS.md via `/aisdlc-checkpoint-tasks`
- ❌ **DON'T**: Manually edit ACTIVE_TASKS.md timestamp/summary sections

---

## 🔄 Proper Workflow (ALWAYS FOLLOW THIS!)

### No Explicit Session Start Needed
**v0.1.4 Change**: Context auto-loads via CLAUDE.md. Just open Claude and start working.

**What auto-loads:**
1. CLAUDE.md (project guide)
2. This reference file (methodology)
3. ACTIVE_TASKS.md (current work)

**No ceremony. Just work.**

### During Work
```bash
# Use TodoWrite tool to track progress
# (Claude should proactively use this!)

# Work on tasks from ACTIVE_TASKS.md
# Follow TDD for code: RED → GREEN → REFACTOR
```

### After Work (CRITICAL!)
```bash
/aisdlc-checkpoint-tasks
# Claude will:
# - Review conversation history
# - Evaluate active tasks
# - Create finished task docs in CORRECT location
# - Update ACTIVE_TASKS.md (timestamp, status, summary)
# - Provide summary report
```

### Commit
```bash
/aisdlc-commit-task <id>
# Generates proper commit message with REQ tags
```

---

## 🛠️ Tools to Use (Claude's Toolkit)

### Task Tracking
- **`TodoWrite` tool** - Track task progress in real-time
  - Use proactively during work
  - Update status: pending → in_progress → completed

### Slash Commands
- `/aisdlc-checkpoint-tasks` - Sync tasks with reality ⭐ **USE AFTER WORK**
- `/aisdlc-finish-task <id>` - Complete specific task
- `/aisdlc-commit-task <id>` - Generate commit message
- `/aisdlc-status` - Show task queue status
- `/aisdlc-release` - Deploy framework to example projects
- `/aisdlc-refresh-context` - Refresh methodology context

### File Operations
- **Read** - Read existing files
- **Write** - Create new files (use sparingly!)
- **Edit** - Modify existing files (preferred)
- **Bash** - Git operations, tests, builds

---

## 📋 Task Lifecycle

### 1. Active Task (Primary)
```markdown
Location: .ai-workspace/tasks/active/ACTIVE_TASKS.md
Single file containing:
- Task details (ID, priority, status, acceptance criteria)
- Summary section (counts, recently completed)
- Recovery commands

Required per task:
- Task ID
- Priority (High/Medium/Low)
- Status (Not Started/In Progress/Blocked)
- Acceptance Criteria
- Feature Flag (if code, optional)
- Requirement Traceability (REQ-*)

TDD Required: YES (unless documentation task)

Updated by: /aisdlc-checkpoint-tasks (timestamp, status, summary)
```

### 2. Finished Task (Documentation)
```markdown
Location: .ai-workspace/tasks/finished/YYYYMMDD_HHMM_task_name.md
Template: .ai-workspace/templates/FINISHED_TASK_TEMPLATE.md
Created by: /aisdlc-checkpoint-tasks (when task completed)

Required Sections:
- Problem
- Investigation
- Solution
- TDD Process (if code)
- Files Modified
- Test Coverage (if code)
- Result
- Traceability
- Metrics
- Lessons Learned
```

---

## 🎨 The 7 Key Principles (Code Stage)

**Full details:** `claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md`

1. **Test Driven Development** - RED → GREEN → REFACTOR → COMMIT
2. **Fail Fast & Root Cause** - Fix at source, no workarounds
3. **Modular & Maintainable** - Single responsibility
4. **Reuse Before Build** - Check existing first
5. **Open Source First** - Suggest alternatives
6. **No Legacy Baggage** - Start clean
7. **Perfectionist Excellence** - Excellence or nothing 🔥

**TDD Workflow:** `claude-code/claude-code/plugins/aisdlc-methodology/docs/processes/TDD_WORKFLOW.md`

---

## 🎯 7-Stage AI SDLC

**Full details:** `docs/ai_sdlc_method.md` (3,300+ lines)
**Agent configs:** `claude-code/claude-code/plugins/aisdlc-methodology/config/stages_config.yml` (1,273 lines)

```
Intent → Requirements → Design → Tasks → Code → System Test → UAT → Runtime Feedback
           ↑                                                                   ↓
           └────────────────────── Feedback Loop ─────────────────────────────┘
```

**Quick stage reference:**
1. Requirements → REQ-F-*, REQ-NFR-*, REQ-DATA-*
2. Design → Components, APIs, ADRs
3. Tasks → Jira tickets with REQ tags
4. **Code** → TDD (RED→GREEN→REFACTOR), tag with `# Implements: REQ-*` ⭐ **PRIMARY**
5. System Test → BDD (Given/When/Then)
6. UAT → Business validation
7. Runtime Feedback → Telemetry → new intents

**Use `/aisdlc-status` to see current stage**

---

## ⚠️ Common Violations (DON'T DO THESE!)

### ❌ Task Location Violations
```
WRONG: docs/finished_tasks/task.md
WRONG: finished_tasks/task.md
WRONG: tasks/task.md
RIGHT: .ai-workspace/tasks/finished/YYYYMMDD_HHMM_task.md
```

### ❌ Workflow Violations
```
WRONG: Finish work without /aisdlc-checkpoint-tasks
WRONG: Create task files manually
WRONG: Manually edit ACTIVE_TASKS.md sections
RIGHT: Work → /aisdlc-checkpoint-tasks → commit
```

### ❌ Tool Violations
```
WRONG: Don't use TodoWrite tool during work
WRONG: Manually create finished task files
WRONG: Skip /aisdlc-checkpoint-tasks after work
RIGHT: Use TodoWrite, use /aisdlc-checkpoint-tasks, follow workflow
```

### ❌ Outdated Command References
```
WRONG: /aisdlc-start-session (removed in v0.1.4)
WRONG: /aisdlc-todo (removed in v0.1.4)
WRONG: TODO_LIST.md (removed in v0.1.4)
RIGHT: Just open Claude (context auto-loads), use ACTIVE_TASKS.md
```

---

## ✅ Pre-Flight Checklist (Before Starting ANY Work)

1. [ ] Is there a task in `ACTIVE_TASKS.md`? (If not, add one)
2. [ ] Am I using `TodoWrite` tool to track progress?
3. [ ] Do I know which stage I'm in (Requirements/Design/Code/etc.)?
4. [ ] Will I run `/aisdlc-checkpoint-tasks` after completing work?

**If ANY answer is "no", STOP and correct before proceeding.**

**Note**: No need to run session start command - context auto-loads via CLAUDE.md

---

## 🚨 Recovery from Violations

### If you violated workspace structure:
1. **STOP** immediately
2. Acknowledge violation to user
3. Move/delete incorrectly placed files
4. Create files in CORRECT location
5. Update this reference if needed

### If you violated workflow:
1. **STOP** and acknowledge
2. Run `/aisdlc-checkpoint-tasks` to sync reality with docs
3. Properly document work retroactively
4. Learn from violation

### If you used removed commands:
1. **STOP** - /aisdlc-start-session and /aisdlc-todo were removed in v0.1.4
2. Use ACTIVE_TASKS.md directly (single file model)
3. Context auto-loads - no explicit session start needed

### If you're unsure:
1. **ASK** the user before proceeding
2. Reference this document
3. Check `.ai-workspace/` structure
4. Use slash commands

---

## 📚 Quick Reference Card (v0.1.4)

### File Locations
| What | Where |
|------|-------|
| Active tasks | `.ai-workspace/tasks/active/ACTIVE_TASKS.md` ⭐ (single file) |
| Finished tasks | `.ai-workspace/tasks/finished/YYYYMMDD_HHMM_*.md` |
| Templates | `.ai-workspace/templates/` |
| Archived sessions | `.ai-workspace/templates/deprecated/SESSION_*.md` |

### Commands (Current)
| When | Command |
|------|---------|
| After work | `/aisdlc-checkpoint-tasks` ⭐ |
| Finish task | `/aisdlc-finish-task <id>` |
| Commit | `/aisdlc-commit-task <id>` |
| Check status | `/aisdlc-status` |
| Deploy framework | `/aisdlc-release` |
| Refresh context | `/aisdlc-refresh-context` |

### Removed Commands (v0.1.4)
| Removed | Reason |
|---------|--------|
| `/aisdlc-start-session` | Context auto-loads (implicit model) |
| `/aisdlc-todo` | Over-engineered, use ACTIVE_TASKS.md directly |
| `/switch-context` | Not MVP |
| `/load-context` | Not MVP |
| `/current-context` | Not MVP |

### TDD Cycle
```
RED    → Write failing test first
GREEN  → Implement minimal solution
REFACTOR → Improve code quality
COMMIT → Save with REQ tags
```

---

## 🎓 Learning from Common Violations

**Violation: Put finished task in wrong location**
- ❌ Created in `docs/finished_tasks/`
- ✅ Should: `.ai-workspace/tasks/finished/YYYYMMDD_HHMM_*.md`

**Violation: Forgot to track progress**
- ❌ Didn't use TodoWrite tool during work
- ✅ Should: Use TodoWrite proactively to track progress

**Violation: Forgot to checkpoint**
- ❌ Finished work without `/aisdlc-checkpoint-tasks`
- ✅ Should: Always checkpoint after work to sync docs

**Violation: Used removed commands**
- ❌ Tried to use `/aisdlc-start-session` or `/aisdlc-todo`
- ✅ Should: v0.1.4 uses implicit sessions, ACTIVE_TASKS.md only

**Key Takeaways:**
1. Context auto-loads - no explicit session start needed
2. Use `/aisdlc-checkpoint-tasks` after ANY work
3. Single file: ACTIVE_TASKS.md (no TODO_LIST.md, no session files)
4. Never create files manually - use commands/tools
5. When in doubt, ASK before proceeding

---

## 📦 Version History

**v3.0.0 (2025-11-25)** - Implicit Session Model (v0.1.4)
- Removed /aisdlc-start-session (context auto-loads)
- Removed /aisdlc-todo and TODO_LIST.md
- Single file: ACTIVE_TASKS.md (tasks + status + summary)
- No session/ directory (implicit sessions)
- Simplified workflow: work → checkpoint → commit

**v2.0.0 (2025-11-23)** - Explicit Session Model
- Formal session start with goals
- Two-tier task system (todos + formal tasks)
- Session tracking in session/

**v1.0.0** - Initial release

---

**Version:** 3.0.0
**Last Updated:** 2025-11-25 (v0.1.4 release)
**Maintained By:** AI SDLC Method Team

**"Excellence or nothing"** 🔥
