Checkpoint active tasks against current conversation context and update ACTIVE_TASKS.md.

<!-- Implements: REQ-F-WORKSPACE-002 (Task Management Templates) -->
<!-- Implements: REQ-NFR-CONTEXT-001 (Persistent Context Across Sessions) -->

**Usage**: `/aisdlc-checkpoint-tasks`

**Instructions**:

## Phase 1: Analyze Current Context
1. **Review conversation history** to identify:
   - What work has been completed recently
   - What files were modified
   - What tests were run and their results
   - What commits were made
   - Any side tasks or tangential work done

## Phase 2: Evaluate Active Tasks
1. **Read** `.ai-workspace/tasks/active/ACTIVE_TASKS.md`
2. **For each active task**, determine:
   - **Completed**: All acceptance criteria met, tests passing, documented
   - **In Progress**: Work has started, some acceptance criteria met
   - **Blocked**: Dependencies not met or blockers encountered
   - **Not Started**: No evidence of work in current context
   - **Partially Relevant**: Side work relates to this task

## Phase 3: Update ACTIVE_TASKS.md

For each task:

**For COMPLETED tasks**:
1. **Read** `.ai-workspace/templates/FINISHED_TASK_TEMPLATE.md`
2. **Create** finished task document:
   - Path: `.ai-workspace/tasks/finished/{YYYYMMDD_HHMM}_{task_slug}.md`
   - Fill all sections based on conversation context
3. **Remove** completed task from ACTIVE_TASKS.md
4. **Add** to "Recently Completed" in Summary section
5. **Update** timestamp at top of file
6. Log: "✅ Task #{id} completed and archived"

**For IN PROGRESS tasks**:
1. **Update** status field to "In Progress"
2. Add progress notes (if any)
3. Update acceptance criteria checkboxes
4. Add any blockers discovered
5. **Update** timestamp at top of file
6. Log: "🔄 Task #{id} status updated to In Progress"

**For BLOCKED tasks**:
1. **Update** status to "Blocked"
2. Document blocker reason in task Notes
3. **Update** timestamp at top of file
4. Log: "🚫 Task #{id} marked as Blocked"

**For NOT STARTED tasks**:
1. **No changes** - leave as is
2. Log: "⏸️  Task #{id} not started (no changes)"

## Phase 4: Summary Report
Provide a summary in this format:

```
╔══════════════════════════════════════════════════════════════╗
║              Task Checkpoint - {YYYY-MM-DD HH:MM}            ║
╚══════════════════════════════════════════════════════════════╝

📊 Evaluation Summary:
   ✅ Completed: {count} task(s)
   🔄 In Progress: {count} task(s)
   🚫 Blocked: {count} task(s)
   ⏸️  Not Started: {count} task(s)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Completed Tasks:
   {list completed task IDs and titles with archive paths}

🔄 In Progress:
   {list in-progress task IDs, titles, and what was done}

🚫 Blocked:
   {list blocked task IDs, titles, and blocker reasons}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

💾 Files Updated:
   - .ai-workspace/tasks/active/ACTIVE_TASKS.md
   {- .ai-workspace/tasks/finished/YYYYMMDD_HHMM_task_name.md (for each completed)}

💡 Next Steps:
   {suggestion based on remaining active tasks}
```

---

**Notes**:
- This command is particularly useful after side tasks or long work sessions
- It helps maintain accurate task state based on actual work done
- Use before `/aisdlc-status` to get accurate status
- The command uses conversation context, so ensure relevant work is in current context
- For ambiguous cases, ask the user for clarification before marking as completed

**Example Use Cases**:
- After working on a side task, checkpoint to see if any active tasks were incidentally completed
- At end of work session to update all task statuses
- Before context switch to ensure task state is accurate
- After a series of commits to update task progress
