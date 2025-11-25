# Session Starter Checklist

Run this checklist at the start of EVERY session. Takes 5-10 minutes but saves hours.

---

## 🚀 Phase 1: Check Current State (2 min)

```bash
# Where are we?
git status
git log --oneline -5

# What's active?
cat .ai-workspace/tasks/active/ACTIVE_TASKS.md

# What was the last session?
ls -lt .ai-workspace/session/history/ | head -1

# Any failing tests?
npm test --silent  # or: pytest -q
```

**Output**: Clear understanding of project state

---

## 📚 Phase 2: Review Methodology (2 min)

```bash
# Review core principles
cat claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md | head -50

# Review TDD workflow
cat claude-code/claude-code/plugins/aisdlc-methodology/docs/processes/TDD_WORKFLOW.md | head -50
```

**Checklist**:
- [ ] I remember the Key Principles
- [ ] I remember TDD: RED → GREEN → REFACTOR
- [ ] I remember pair programming patterns

---

## 🎯 Phase 3: Set Session Goals (2 min)

```bash
# Create current session file
cp .ai-workspace/templates/SESSION_TEMPLATE.md \
   .ai-workspace/session/current_session.md

# Edit with today's goals
```

**Define**:
- [ ] Primary goal (must complete)
- [ ] Secondary goal (should complete)
- [ ] Stretch goal (if time permits)

---

## 🤝 Phase 4: Align with AI Assistant (1 min)

**Human says**:
```
"Let's align on today's session:
- Primary goal: [WHAT]
- I'm thinking we should [APPROACH]
- Let's do pair programming with [MODE]
- Check-ins every [15/30] minutes
- Any questions before we start?"
```

**Claude responds**:
```
"I understand. Let me confirm:
- Goal: [RESTATED]
- Approach: [RESTATED]
- I'll suggest [ROLE], you [ROLE]
- Ready to begin?"
```

**Checklist**:
- [ ] Goals aligned
- [ ] Approach agreed
- [ ] Roles clear
- [ ] Check-in schedule set

---

## 🛠️ Phase 5: Set Up Environment (2 min)

```bash
# Start dev server (if needed)
npm run dev  # or: python app.py

# Start test watcher for TDD
npm test -- --watch  # or: pytest-watch

# Open relevant files in editor
code .ai-workspace/tasks/active/ACTIVE_TASKS.md
code .ai-workspace/session/current_session.md
```

**Checklist**:
- [ ] Dev server running
- [ ] Test watcher running
- [ ] Key files open
- [ ] Ready to code

---

## 🧭 Phase 6: Choose Working Mode (1 min)

**Select ONE**:

### 🔴 TDD Mode (RED → GREEN → REFACTOR)
- [ ] Write failing test FIRST
- [ ] Minimal code to pass
- [ ] Refactor for quality
- [ ] Commit with test metrics

### 🐛 Bug Fix Mode
- [ ] Reproduce bug
- [ ] Write failing test that exposes bug
- [ ] Fix bug to make test pass
- [ ] Verify no regression

### 🔍 Exploration Mode
- [ ] Research and investigate
- [ ] Document findings
- [ ] Create todos for follow-up
- [ ] No code commits (research only)

### 🤝 Pair Programming Mode
- [ ] Roles clear (driver/navigator)
- [ ] Check-ins every 15 min
- [ ] TodoWrite tracking active
- [ ] Communication continuous

---

## ✅ Ready to Begin!

**Final Checklist**:
- [ ] Current state checked
- [ ] Methodology reviewed
- [ ] Goals set
- [ ] Aligned with Claude
- [ ] Environment ready
- [ ] Working mode chosen

**Time Taken**: ~10 minutes
**Time Saved**: Hours of confusion and rework

---

## 🚨 Quick Recovery (If Context Lost)

If you get lost mid-session, run:

```bash
# What was I doing?
git status
git diff

# What's the current task?
cat .ai-workspace/session/current_session.md

# What's the methodology?
cat claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md | head -20

# Recent work?
ls -lt .ai-workspace/tasks/finished/ | head -3
```

Then ask Claude:
```
"I lost context. Can you help me understand:
1. What task are we working on?
2. What phase of TDD are we in?
3. What should I do next?"
```

---

**Remember**: 10 minutes at session start saves hours of confusion!
