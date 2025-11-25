Display current task status from `.ai-workspace/tasks/`.

<!-- Implements: REQ-F-TODO-003 (List all TODOs) -->
<!-- Implements: REQ-F-CMD-001 (Slash commands for workflow) -->

## Instructions

Show a quick snapshot of the task queue:

1. **Read** `.ai-workspace/tasks/active/ACTIVE_TASKS.md`
   - Count active tasks (look for "## Task #" headers, exclude examples)
   - List active task titles

2. **List** recently finished tasks from `.ai-workspace/tasks/finished/`
   - Show last 5 finished task files (most recent first)

3. **Read** `.ai-workspace/tasks/todo/TODO_LIST.md`
   - Count TODO items (look for "**Todo**:" entries under "## Active Todos")
   - List TODO descriptions

4. **Display** in this simple format:

```
╔══════════════════════════════════════════════════════════════╗
║                    AI SDLC Task Status                       ║
╚══════════════════════════════════════════════════════════════╝

📋 Active Tasks: {count}
   {list task titles or "(No tasks in progress)"}

✅ Recently Finished: {count}
   {list last 5 finished tasks or "(No finished tasks yet)"}

📝 TODO List: {count} items
   {list todos or "(Empty)"}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

💡 Next: {suggestion based on state}
```

**Suggestions**:
- If no active tasks and todos exist: "Promote a todo to active with /start-session"
- If active tasks exist: "Continue working on active tasks"
- If no todos: "Add todos with /todo or use /start-session to begin new work"

---

**Note**: This command is read-only and just shows task queue status.
