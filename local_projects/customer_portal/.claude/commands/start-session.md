Start a new development session by running through the session starter checklist.

**Instructions**:

## Phase 1: Check Current State
1. Run `git status` to see current working directory state
2. Run `git log --oneline -5` to see recent commits
3. Read `.ai-workspace/tasks/active/ACTIVE_TASKS.md` to see active tasks
4. Check if tests are passing (run appropriate test command)

## Phase 2: Review Methodology (Quick Reminder)
Remind user of:
- The Key Principles (from `claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md`)
- TDD Workflow: RED → GREEN → REFACTOR → COMMIT
- Pair programming patterns

## Phase 3: Set Session Goals
1. Copy template: `cp .ai-workspace/templates/SESSION_TEMPLATE.md .ai-workspace/session/current_session.md`
2. Ask user for:
   - Primary goal (must complete)
   - Secondary goal (should complete)
   - Stretch goal (if time permits)
   - Working mode (TDD / Bug Fix / Exploration / Pair Programming)
   - Check-in frequency (15 or 30 minutes)

## Phase 4: Align with User
1. Restate the goals to confirm understanding
2. Propose roles (driver/navigator)
3. Confirm approach and check-in schedule
4. Ask: "Any questions before we start?"

## Phase 5: Ready to Begin
1. Update the session file with all collected information
2. Confirm: "✅ Session started! Current focus: [task]"

**Note**: The full checklist is in `.ai-workspace/templates/SESSION_STARTER.md`
