Refresh Claude's context with AI SDLC methodology and workspace structure.

<!-- Implements: REQ-F-CMD-001 (Slash commands for workflow) -->

**Usage**: `/aisdlc-refresh-context`

**Instructions**:

## Phase 1: Load Method Reference

1. **Read** `.ai-workspace/templates/AISDLC_METHOD_REFERENCE.md`
   - This contains the core workflow, structure, and violation warnings

## Phase 2: Confirm Current Context

1. **Check** current working directory
   ```bash
   pwd
   ```

2. **Check** workspace structure exists
   ```bash
   ls .ai-workspace/tasks/
   # Should show: todo/, active/, finished/
   ```

3. **Check** active tasks
   ```bash
   cat .ai-workspace/tasks/active/ACTIVE_TASKS.md | head -20
   ```

## Phase 3: Remind Claude of Critical Rules

**Output to user:**

```
╔══════════════════════════════════════════════════════════════╗
║         AI SDLC Context Refreshed - Ready to Work            ║
╚══════════════════════════════════════════════════════════════╝

✅ Workspace Structure Loaded
   - Todos: .ai-workspace/tasks/todo/
   - Active: .ai-workspace/tasks/active/
   - Finished: .ai-workspace/tasks/finished/

✅ Workflow Loaded
   1. /aisdlc-start-session (begin)
   2. Use TodoWrite tool (track progress)
   3. /aisdlc-checkpoint-tasks (after work)
   4. /aisdlc-commit-task (commit)

✅ Critical Rules Loaded
   - NEVER put finished tasks outside .ai-workspace/
   - ALWAYS use TodoWrite tool during work
   - ALWAYS checkpoint after completing work
   - ASK if unsure about anything

✅ 7-Stage SDLC Loaded
   Current stage: {detect from context or ask}

📚 Full Method Reference:
   .ai-workspace/templates/AISDLC_METHOD_REFERENCE.md

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🚀 Ready to Begin!

What would you like to work on?
```

## Phase 4: Check for Active Tasks

If `ACTIVE_TASKS.md` has tasks:
- List them with status
- Ask which task to work on

If `ACTIVE_TASKS.md` is empty:
- Suggest running `/aisdlc-start-session`
- Or ask what user wants to accomplish

---

**When to use this command:**
- Start of every session
- After context loss (long conversations)
- After making methodology violations
- When feeling unsure about workflow
- Before starting any significant work

**What this does:**
- Loads AI SDLC methodology into Claude's context
- Reminds Claude of workspace structure
- Refreshes critical rules (violations to avoid)
- Shows current active tasks
- Prepares Claude to work correctly

**Note:** This is a "context refresh" command, not a "session start" command.
Use `/aisdlc-start-session` to actually begin a work session with goals and planning.
