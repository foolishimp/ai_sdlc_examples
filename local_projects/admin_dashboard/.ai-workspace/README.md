# Developer Workspace

**Version**: 1.0
**Purpose**: File-based task tracking and session management for AI-augmented development
**Integration**: Works standalone or with AI SDLC methodology

---

## Overview

The Developer Workspace provides a lightweight, file-based system for managing development tasks and sessions. It integrates practical workflow features from `ai_init` into the `ai_sdlc_method` framework.

**Key Features**:
- ✅ Two-tier task tracking (quick todos + formal tasks)
- ✅ Session management with context preservation
- ✅ TDD workflow support (RED → GREEN → REFACTOR)
- ✅ Pair programming patterns for human-AI collaboration
- ✅ File-based foundation (no external dependencies)
- ✅ Git-versioned documentation

---

## Quick Start

### 1. Structure Overview

```
.ai-workspace/
├── config/
│   └── workspace_config.yml        # Configuration
├── session/
│   ├── current_session.md          # Active session (git-ignored)
│   └── history/                    # Past sessions (git-ignored)
├── tasks/
│   ├── active/
│   │   └── ACTIVE_TASKS.md         # Active tasks (lightweight or formal)
│   ├── finished/                   # Completed task docs
│   └── archive/                    # Old completed tasks
└── templates/
    ├── TASK_TEMPLATE.md
    ├── FINISHED_TASK_TEMPLATE.md
    ├── SESSION_TEMPLATE.md
    ├── SESSION_STARTER.md
    └── PAIR_PROGRAMMING_GUIDE.md
```

### 2. Available Commands

- `/start-session` - Begin a new development session
- `/finish-task {id}` - Complete a task and create documentation
- `/commit-task {id}` - Commit a finished task with proper message

### 3. Typical Workflow

```bash
# Start your day
/start-session
# Set goals, align with Claude, begin work

# Work on tasks (lightweight or formal based on need)
# (edit ACTIVE_TASKS.md manually or with Claude's help)

# Work on tasks using TDD
# RED → GREEN → REFACTOR → Document

# Complete a task
/finish-task 5
# Creates comprehensive documentation

# Commit the work
/commit-task 5
# Generates proper commit message
```

---

## Task Management

### Active Tasks (Lightweight or Formal)

**File**: `tasks/active/ACTIVE_TASKS.md`
**Purpose**: Track all development work (can be lightweight or formal based on need)

**Lightweight Task** (quick work):
```markdown
## Task #3: Add rate limiting

**Priority**: Medium
**Status**: Not Started
**Estimated Time**: 1 hour

**Description**: Add rate limiting to password reset endpoint
```

**Formal Task** (complex work with TDD):
```markdown
## Task #5: Refactor Authentication Service

**Priority**: High
**Status**: In Progress
**Estimated Time**: 4 hours
**Feature Flag**: `task-5-auth-refactor`

**Requirements Traceability**:
- <REQ-ID>: User login functionality
- REQ-NFR-PERF-003: Response time < 200ms

**Acceptance Criteria**:
- [ ] All tests pass (RED → GREEN → REFACTOR)
- [ ] Test coverage ≥ 95%
- [ ] Feature flag tested both enabled/disabled

**TDD Checklist**:
- [x] RED: Write failing tests
- [x] GREEN: Implement solution
- [ ] REFACTOR: Improve code quality
- [ ] COMMIT: Create finished task document
```

### Finished Tasks (Documentation)

**Directory**: `tasks/finished/`
**Purpose**: Learning and reference
**Format**: `YYYYMMDD_HHMM_task_name.md`
**Contains**: Problem, solution, tests, lessons learned, metrics

Finished tasks become valuable documentation that feeds back to enterprise traceability.

---

## Session Management

### Starting a Session

```bash
/start-session
```

This runs through the session starter checklist:
1. Check git status and recent commits
2. Review active tasks
3. Quick methodology review (Key Principles, TDD)
4. Set session goals (primary, secondary, stretch)
5. Choose working mode
6. Align with AI assistant

### During a Session

- Check-in every 15-30 minutes
- Update session file with progress
- Track decisions made
- Document blockers

### Ending a Session

1. Complete or checkpoint current task
2. Run full test suite
3. Update ACTIVE_TASKS.md
4. Create finished task doc (if completed)
5. Commit with proper message
6. Archive session to history/

---

## Pair Programming with AI

**Guide**: See `templates/PAIR_PROGRAMMING_GUIDE.md`

### Three Collaboration Modes

1. **Human Driver / AI Navigator** (Most Common)
   - Human: Strategy, architecture, critical code
   - AI: Implementation suggestions, boilerplate, issue spotting

2. **AI Driver / Human Navigator** (Tactical)
   - AI: Repetitive code, refactoring, tests
   - Human: Specifications, reviews, approval

3. **Collaborative** (Complex Problems)
   - Both: Debugging, design, learning together

### Communication Patterns

- Think aloud continuously
- Check-in every 10-15 minutes
- Clear handoffs between roles
- Explicit approval for major changes

### Anti-Patterns to Avoid

- ❌ Silent coding (no communication)
- ❌ Assumption making (ask first)
- ❌ Big bang implementation (incremental is better)
- ❌ Ignoring feedback (listen and adapt)
- ❌ No knowledge transfer (explain as you go)

---

## TDD Workflow

**Always follow**: RED → GREEN → REFACTOR → COMMIT

1. **RED**: Write failing test first
2. **GREEN**: Write minimal code to pass
3. **REFACTOR**: Improve code quality
4. **COMMIT**: Save with clear message (tagged with REQ key)
5. **REPEAT**: Next test

**No code without tests. Ever.**

See `claude-code/claude-code/plugins/aisdlc-methodology/docs/processes/TDD_WORKFLOW.md` for complete workflow.

---

## Configuration

### File: `config/workspace_config.yml`

Key settings:
- Task tracking enabled/disabled
- Session check-in frequency (default: 15 min)
- TDD enforcement (default: true)
- Minimum test coverage (default: 80%)
- Pair programming verbosity (default: medium)

Edit this file to customize your workspace behavior.

---

## Integration with AI SDLC

### Standalone Mode (This Workspace Only)

Use the Developer Workspace without the full 7-stage SDLC:
- Task tracking works independently
- Session management works independently
- No REQ keys required (optional)

### Integrated Mode (Full SDLC)

Link to the enterprise 7-stage AI SDLC:
- Tasks reference REQ keys from Requirements Stage
- Finished tasks feed Runtime Feedback Stage
- Session metrics contribute to team analytics
- Full traceability: Intent → Code → Runtime

**No external integrations by default** - This implementation uses file system only (no Jira, GitHub, Azure DevOps).

---

## File System as Foundation

**IMPORTANT**: This workspace is built on a file-based foundation:

✅ **Always works** - No external dependencies
✅ **Offline capable** - Work without network
✅ **Git versioned** - Full history and backup
✅ **No vendor lock-in** - Portable between tools
✅ **Resilient** - Continues if external tools fail

Templates, configs, and finished tasks are tracked in git. Active session state is local and git-ignored.

---

## Examples

### Example 1: Starting a Task

```markdown
## Task #6: Add Payment Processing

**Priority**: High
**Status**: Not Started
**Estimated Time**: 8 hours
**Feature Flag**: `task-6-payment-processing`

**Requirements Traceability**:
- <REQ-ID>: Credit card processing
- REQ-NFR-SEC-005: PCI DSS compliance

**Acceptance Criteria**:
- [ ] Integration tests with Stripe test API
- [ ] Test coverage ≥ 90%
- [ ] Feature flag can disable payment flow

# Then follow TDD workflow...
```

### Example 2: Finishing a Task

```bash
# All tests passing, task complete
/finish-task 6

# Claude creates comprehensive documentation:
# .ai-workspace/tasks/finished/20250121_1530_payment_processing.md
# - Problem description
# - Solution approach
# - TDD process (RED/GREEN/REFACTOR)
# - Test coverage metrics
# - Code changes
# - Lessons learned
# - Traceability to requirements

✅ Task #6 finished. Document created.

# Commit the work
/commit-task 6

# Claude generates proper commit message:
# Task #6: Add Payment Processing
#
# Integrated Stripe API for credit card processing...
#
# Tests: 42 unit tests, 91% coverage
# TDD: RED → GREEN → REFACTOR
# Implements: <REQ-ID>, REQ-NFR-SEC-005

✅ Committed: abc123d
```

---

## Tips & Best Practices

### Daily Routine

1. **Morning**: Run `/start-session` to set goals
2. **During Work**: Track tasks in ACTIVE_TASKS.md (lightweight or formal)
3. **Task Completion**: Use `/finish-task` + `/commit-task`
4. **End of Day**: Review progress in session file

### Weekly Routine

1. Review finished tasks for patterns and learnings
2. Archive completed tasks
3. Update estimates based on actual time tracking
4. Share key learnings with team

### Maintaining Clean Workspace

```bash
# Archive finished tasks older than 90 days
find tasks/finished/ -name "*.md" -mtime +90 \
  -exec mv {} tasks/archive/ \;
```

---

## Troubleshooting

### Issue: "Context lost mid-session"

**Solution**: Check session file
```bash
cat .ai-workspace/session/current_session.md
git status
git log --oneline -5
```

### Issue: "Can't find finished task"

**Solution**: Search by date or keywords
```bash
ls -lt .ai-workspace/tasks/finished/
grep -r "authentication" .ai-workspace/tasks/finished/
```

---

## Migration from ai_init

If you're migrating from `ai_init/claude_tasks`:

```bash
# Backup existing
cp -r claude_tasks/ .ai-workspace-backup/

# Migrate active tasks
cp claude_tasks/active/ACTIVE_TASKS.md .ai-workspace/tasks/active/

# Migrate finished tasks
cp claude_tasks/finished/*.md .ai-workspace/tasks/finished/

# Update slash commands (paths changed)
sed -i 's|claude_tasks|.ai-workspace/tasks|g' .claude/commands/*.md

# Cleanup (after verification)
rm -rf claude_tasks/
```

---

## Resources

**Documentation**:
- [Complete Integration Plan](../docs/DEVELOPER_WORKSPACE_INTEGRATION.md) - 3,000+ line spec
- [AI SDLC Method](../docs/ai_sdlc_method.md) - Complete 7-stage methodology
- [Key Principles](../claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md)
- [TDD Workflow](../claude-code/claude-code/plugins/aisdlc-methodology/docs/processes/TDD_WORKFLOW.md)

**Templates**: All in `templates/` directory
**Configuration**: `config/workspace_config.yml`
**Support**: GitHub Issues at https://github.com/foolishimp/ai_sdlc_method/issues

---

## Success Metrics (Track After 30 Days)

- [ ] Average session startup time < 10 min
- [ ] Context loss incidents: 0
- [ ] Test coverage: ≥ 80%
- [ ] Post-release defects: < 5%
- [ ] Cycle time: -20% (faster)
- [ ] Rework rate: < 10%
- [ ] Developer satisfaction: ≥ 4/5

---

## License

MIT License - See LICENSE file in repository

---

## Author

**foolishimp** - https://github.com/foolishimp/ai_sdlc_method

Based on learnings from:
- `ai_init/claude_init` (developer experience)
- `ai_sdlc_method` (enterprise scale)
- Traditional pair programming research
- AI collaboration best practices

---

**"Excellence or nothing"** 🔥

*Developer experience that matches the enterprise vision!*
