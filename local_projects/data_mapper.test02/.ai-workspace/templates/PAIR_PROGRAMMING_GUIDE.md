# Pair Programming with AI - Quick Reference

**Purpose**: Maximize human-AI collaboration effectiveness
**Scope**: Claude Code and similar AI assistants

---

## 🎯 Core Concept

**Human-AI Pair Programming**:
- Human (Driver) + AI (Navigator) **OR** AI (Driver) + Human (Navigator)
- Roles based on task nature, not time
- Continuous validation (human approves AI proposals)
- AI never makes architectural decisions without approval

---

## 👥 Three Collaboration Modes

### Mode 1: Human Driver / AI Navigator (Most Common)

**When to Use**:
- Implementing business logic
- Making architectural decisions
- Writing sensitive code (security, payments)
- Learning new concepts

**Human**: Defines WHAT to build, makes strategic decisions, writes critical code
**AI**: Suggests HOW to implement, writes boilerplate, spots issues, offers alternatives

**Example**:
```
Human: "We need to validate credit card numbers"
AI: "I suggest: 1) Write tests first, 2) Use Luhn algorithm, 3) Use library (card-validator). Which?"
Human: "Option 3. Show me tests first"
AI: [writes failing tests]
Human: "Looks good, implement it"
```

---

### Mode 2: AI Driver / Human Navigator (Tactical)

**When to Use**:
- Writing repetitive code (tests, mocks, types)
- Refactoring for code quality
- Implementing well-defined patterns
- Documentation generation

**AI**: Writes code based on clear specifications, executes TDD autonomously
**Human**: Provides specifications, watches for drift, reviews for correctness

**Example**:
```
Human: "Refactor this auth module following our pattern. Tests must pass."
AI: "I'll: 1) Extract validation, 2) Update 3 modules, 3) Keep 42 tests passing. Proceeding..."
[AI writes code]
AI: "Done. All tests pass. Review?"
Human: "Looks good, commit it"
```

---

### Mode 3: Collaborative (Complex Problems)

**When to Use**:
- Debugging complex issues
- Designing new systems
- Resolving ambiguous requirements
- Learning together

**Both**: Think aloud, ask questions, challenge assumptions, explore alternatives

**Example**:
```
Human: "This auth bug is strange. Token validates but user can't access"
AI: "Could be race condition in role loading?"
Human: "Good point. Let's add logging"
[Together design concurrency test]
```

---

## 🗣️ Communication Patterns

### Pattern 1: Think Aloud
- Verbalize reasoning to prevent silent mistakes
- Creates audit trail
- Catches flawed assumptions early

### Pattern 2: Frequent Check-ins (Every 10-15 min)
- "Does this look right so far?"
- "Should we test this now or keep going?"
- "Any concerns with this approach?"

### Pattern 3: Clear Handoffs
```
Human: "I've set up the structure. Can you implement the tests?"
AI: "Tests ready: 15 unit tests (all failing). Please review before I implement."
```

### Pattern 4: Explicit Approval
**AI Must Ask Before**:
- Making architectural changes
- Introducing new dependencies
- Deleting significant code
- Changing public interfaces
- Committing to git

---

## 🔄 TDD Ping-Pong (Modified for AI)

1. Human describes test case → AI writes failing test
2. AI shows failing test → Human approves
3. AI implements → Human reviews
4. Both refactor together → Commit

**Example**:
```
Iteration 1:
Human: "Test user login with valid credentials"
AI: [writes test_login_valid() - FAILS]
Human: "Approved"
AI: [implements login() - PASSES]
Human: "Good, but extract validation logic"
AI: [refactors - STILL PASSES]

Iteration 2:
AI: "Should we test invalid credentials?"
Human: "Yes, also test missing fields"
...
```

---

## 🚫 Anti-Patterns to Avoid

### ❌ Silent Coding
**Bad**: AI writes 500 lines in one shot
**Good**: AI breaks into chunks, shows each for review

### ❌ Assumption Making
**Bad**: AI assumes PostgreSQL without asking
**Good**: AI checks `package.json`, asks which database to use

### ❌ Big Bang Implementation
**Bad**: Implement entire feature, then debug failing tests
**Good**: One test → minimal code → pass → refactor → next test

### ❌ Ignoring Feedback
**Bad**: Human says "simplify", AI continues with complex approach
**Good**: AI acknowledges, refactors, asks for review

### ❌ No Knowledge Transfer
**Bad**: AI implements complex algorithm without explanation
**Good**: AI explains approach, adds comments, helps human understand

---

## 💡 Best Practices

### 1. Take Breaks
- Break after completing a task
- Break when stuck (fresh perspective)
- Break every 60-90 minutes minimum

### 2. Celebrate Small Wins
```
AI: "All 37 tests passing! 🎉"
Human: "That null case test you suggested caught a real bug"
AI: "Your refactoring made the code much cleaner"
```

### 3. Share Knowledge Continuously
```
AI: "I'm using a Trie because: 1) Autocomplete needs prefix matching, 2) O(k) lookup. Would you like diagram?"
Human: "Yes, and note: users type 3+ chars before we search"
```

### 4. Stay Engaged
- Don't say "implement X" and disappear
- Watch what AI is doing
- Ask questions if unclear
- Provide feedback continuously

### 5. Respect Expertise
**Human expertise**: Business domain, UX, team conventions, strategic decisions
**AI expertise**: Syntax, patterns, libraries, code quality, documentation

---

## 🎮 Quick Commands

### Human Can Say
- "Let's pair on this" - Start session
- "You drive" - AI leads implementation
- "I'll drive" - Human leads implementation
- "Hold on" - Pause for review
- "Explain that" - Need clarification
- "Show me alternatives" - Want options
- "Ship it" - Ready to commit

### AI Should Say
- "Should I proceed with..." - Ask permission
- "I propose..." - Suggest approach
- "Alternative approaches are..." - Present options
- "I notice..." - Point out issues
- "Ready for review" - Completed chunk
- "Any concerns?" - Solicit feedback

---

## 📊 Track These Metrics

1. **Cycle Time**: Idea to working code (target: <30 min for small features)
2. **Defect Rate**: Bugs after completion (target: <5%)
3. **Rework Rate**: Time spent redoing work (target: <10%)
4. **Test Coverage**: Code covered by tests (target: ≥80%)
5. **Communication Clarity**: Misunderstandings per session (target: <2)

---

## 🏁 Session Structure

### Start (5-10 min)
1. Review context: `git status`, `git log`, check active tasks
2. Align on goals
3. Set parameters: mode, check-in frequency, roles
4. Create session file

### During (Active Work)
- Check-in every 15-30 min
- Think aloud continuously
- Document decisions
- Use TodoWrite for tracking

### End (10 min)
1. Complete or checkpoint task
2. Run full test suite
3. Update task status
4. Commit with proper message
5. Archive session
6. Session summary

---

## 🎯 Key Benefits

1. **Continuous Code Review** - Two perspectives on every line
2. **Knowledge Documentation** - Conversation becomes docs
3. **Reduced Cognitive Load** - Share mental burden
4. **24/7 Availability** - AI doesn't need sleep
5. **Learning Opportunity** - Both learn from each other
6. **Higher Quality** - Catch more issues
7. **Faster Development** - Parallel thinking
8. **Built-in Testing** - TDD becomes natural
9. **No Social Pressure** - Easy to admit "I don't understand"
10. **Consistency** - AI maintains discipline

---

## 📚 References

- Kent Beck - "Extreme Programming Explained"
- Laurie Williams - "Pair Programming Illuminated"
- This workspace: `/docs/DEVELOPER_WORKSPACE_INTEGRATION.md` (complete guide)

---

**Remember**: The goal is to amplify human capabilities through effective AI collaboration!
