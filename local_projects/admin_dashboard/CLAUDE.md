# CLAUDE.md

This file provides guidance to Claude Code when working with this project.

## AI SDLC Method

This project uses the **AI SDLC Method** - a comprehensive framework for AI-augmented software development.

### Context Auto-Loading

**IMPORTANT**: This project uses an **implicit session model** where context loads automatically when Claude Code starts.

**What Auto-Loads:**
1. This file (CLAUDE.md) - Project guide
2. Method Reference - `.ai-workspace/templates/AISDLC_METHOD_REFERENCE.md`
3. Active Tasks - `.ai-workspace/tasks/active/ACTIVE_TASKS.md`

**One File. That's It.**

### No Explicit Session Start Needed

- **Session = Context** - Your "session" is simply your current working context
- **Context persists** - Automatically saved via `/aisdlc-checkpoint-tasks`
- **No ceremony** - Just open Claude and start working
- **One file** - ACTIVE_TASKS.md tracks all your work

### Quick Recovery

```bash
cat .ai-workspace/tasks/active/ACTIVE_TASKS.md  # Everything is here
git status                                       # Current git state
```

### Directory Structure

```
.ai-workspace/
├── config/              # Workspace configuration
├── tasks/
│   ├── todo/           # Quick capture list
│   ├── active/         # Current formal tasks
│   ├── finished/       # Completed task documentation
│   └── archive/        # Old tasks
├── session/            # Session tracking (git-ignored)
└── templates/          # Task and session templates

.claude/
└── commands/           # Slash commands for workflow
```

### Key Principles

1. **Test-Driven Development** - No code without tests
2. **Fail Fast & Root Cause** - Fix problems at source, no workarounds
3. **Modular & Maintainable** - Single responsibility principle
4. **Reuse Before Build** - Check existing solutions first
5. **Open Source First** - Leverage community solutions
6. **No Legacy Baggage** - Start clean, avoid technical debt
7. **Perfectionist Excellence** - Build best-of-breed only

### Development Workflow

**TDD Cycle:**
```
RED → GREEN → REFACTOR → COMMIT
```

**Simple Workflow:**
1. Open Claude (context auto-loads)
2. Work on tasks from ACTIVE_TASKS.md
3. Follow TDD for all code
4. `/aisdlc-checkpoint-tasks` - Save progress
5. `/aisdlc-commit-task <id>` - Commit completed work

### Available Commands

Run these slash commands in Claude Code:

- `/aisdlc-checkpoint-tasks` - Save progress and update task status
- `/aisdlc-finish-task <id>` - Complete task with full documentation
- `/aisdlc-commit-task <id>` - Generate proper commit message
- `/aisdlc-status` - Show current task queue status
- `/apply-persona <name>` - Apply development persona
- `/list-personas` - List available personas

View all commands: `ls .claude/commands/`

### Pair Programming with AI

See [.ai-workspace/templates/PAIR_PROGRAMMING_GUIDE.md](.ai-workspace/templates/PAIR_PROGRAMMING_GUIDE.md) for collaboration patterns.

**Three modes:**
1. **Human Driver / AI Navigator** - Human codes, AI suggests
2. **AI Driver / Human Navigator** - AI codes, human reviews
3. **Collaborative** - Both work together on complex problems

**Key practices:**
- Think aloud continuously
- Check-in every 10-15 minutes
- Clear handoffs between roles
- Explicit approval for major changes

### Task Management

**Simple system:**

**Active Tasks**
- File: `.ai-workspace/tasks/active/ACTIVE_TASKS.md`
- Contains: All current work with status, priority, acceptance criteria
- Requirements: Follow TDD (RED → GREEN → REFACTOR)
- Feature flags: `task-N-description` (optional)
- Updated automatically by `/aisdlc-checkpoint-tasks`

**Finished Tasks** (Documentation)
- Directory: `.ai-workspace/tasks/finished/`
- Format: `YYYYMMDD_HHMM_task_name.md`
- Contains: Problem, solution, tests, lessons learned
- Created by `/aisdlc-finish-task <id>`

### Testing Requirements

**Minimum standards:**
- Test coverage ≥ 80%
- All acceptance criteria met
- Feature flags tested (enabled/disabled)
- No failing tests in main branch

**Test types:**
- Unit tests (fast, isolated)
- Integration tests (system components)
- End-to-end tests (user scenarios)

### Integration with Full AI SDLC

This workspace can work **standalone** or integrate with the **7-stage enterprise AI SDLC**:

**Standalone Mode:**
- Works independently
- No external dependencies
- File-based only

**Integrated Mode:**
- Tasks link to REQ keys from Requirements Stage
- Finished tasks feed Runtime Feedback Stage
- Full traceability: Intent → Code → Runtime

### Plugins Available

If plugins are installed, additional capabilities include:

**Core:**
- `aisdlc-core` - Core methodology and framework
- `aisdlc-methodology` - Complete 7-stage SDLC process

**Skills:**
- `testing-skills` - Advanced testing patterns
- `code-skills` - Code quality and refactoring
- `design-skills` - Architecture and design patterns
- `requirements-skills` - Requirements engineering
- `runtime-skills` - Deployment and monitoring

**Standards:**
- `python-standards` - Python best practices
- `principles-key` - Core development principles

**Bundles:**
- `startup` - Essential plugins for getting started
- `datascience` - Data science and ML focus
- `qa` - Quality assurance focus
- `enterprise` - Full enterprise suite

### Resources

**Documentation:**
- [Developer Workspace Guide](.ai-workspace/README.md)
- [AI SDLC Method Overview](docs/ai_sdlc_method.md) - If available
- [TDD Workflow](claude-code/claude-code/plugins/aisdlc-methodology/docs/processes/TDD_WORKFLOW.md) - If plugins installed
- [Key Principles](claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md) - If plugins installed

**Templates:**
- Task Template: `.ai-workspace/templates/TASK_TEMPLATE.md`
- Finished Task: `.ai-workspace/templates/FINISHED_TASK_TEMPLATE.md`
- Pair Programming: `.ai-workspace/templates/PAIR_PROGRAMMING_GUIDE.md`
- Method Reference: `.ai-workspace/templates/AISDLC_METHOD_REFERENCE.md`

### Project-Specific Configuration

**[TODO: Add your project-specific guidance below]**

#### Technology Stack
- Language/Framework:
- Dependencies:
- Build system:

#### Project Structure
```
[Add your project structure here]
```

#### Development Commands
```bash
# Install dependencies
[your command]

# Run tests
[your command]

# Build
[your command]

# Deploy
[your command]
```

#### Team Conventions
- Coding standards:
- Branch naming:
- Commit message format:
- Review process:

#### Domain Knowledge
- Business context:
- Key concepts:
- External integrations:
- Known issues/gotchas:

---

**Version:** 1.0
**AI SDLC Method:** https://github.com/foolishimp/ai_sdlc_method

*"Excellence or nothing"* 🔥
