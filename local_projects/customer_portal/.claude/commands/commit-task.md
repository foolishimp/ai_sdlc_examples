Commit a completed task using information from the finished task document.

**Usage**: `/commit-task {task_id}`

**Instructions**:

1. **Find the finished task document** in `.ai-workspace/tasks/finished/`
   - Look for the most recent file matching the task ID
2. **Read the finished task document** to gather commit information
3. **Generate commit message** using this format:
   ```
   Task #{id}: {title}

   {Brief summary of problem}

   {Brief summary of solution}

   Tests: {test_summary}
   TDD: RED → GREEN → REFACTOR

   Implements: {REQ keys if present}

   🤖 Generated with [Claude Code](https://claude.ai/code)
   Co-Authored-By: Claude <noreply@anthropic.com>
   ```
4. **Show the commit message** to the user and ask for approval
5. **If approved**, run: `git add -A && git commit -m "$(cat <<'EOF'
   {commit_message}
   EOF
   )"`
6. **Update session file** if active session exists
7. **Confirm**: "✅ Committed: {commit_hash}"

**Important**:
- Always show the commit message before committing
- Include requirement keys if present in the task
- Keep commit message concise but informative
