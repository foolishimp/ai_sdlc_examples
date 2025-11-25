Complete the specified task and create a finished task document.

<!-- Implements: REQ-F-CMD-001 (Slash commands for workflow) -->

**Usage**: `/finish-task {task_id}`

**Instructions**:

1. **Read** `.ai-workspace/tasks/active/ACTIVE_TASKS.md` to find the task details
2. **Verify task is complete**:
   - All tests passing
   - Acceptance criteria met
   - Code reviewed and refactored
3. **Create finished task document**:
   - Copy template: `.ai-workspace/templates/FINISHED_TASK_TEMPLATE.md`
   - Name: `.ai-workspace/tasks/finished/{YYYYMMDD_HHMM}_{task_slug}.md`
   - Fill in all sections with actual information from the task:
     - Problem: What was the issue?
     - Investigation: What did you discover?
     - Solution: How did you solve it?
     - TDD Process: RED/GREEN/REFACTOR phases with timing
     - Files Modified: List all changed files
     - Test Coverage: Before/after metrics
     - Feature Flag: Status and rollout plan
     - Code Changes: Before/after examples
     - Testing: Commands and results
     - Result: Outcome and metrics
     - Side Effects: Positive and considerations
     - Future Considerations: Follow-up tasks
     - Lessons Learned: What we learned
     - Traceability: Requirements coverage
     - Metrics: Lines, tests, coverage, complexity
4. **Remove task from ACTIVE_TASKS.md**
5. **Update TODO_LIST.md** if this task was promoted from a todo
6. **Confirm**: "✅ Task #{id} finished. Document created at: {path}"

**Important**: The finished task document is valuable documentation. Fill it out thoroughly.
