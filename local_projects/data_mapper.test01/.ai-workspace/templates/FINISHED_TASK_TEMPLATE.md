# Task: {TITLE}

**Status**: Completed
**Date**: {DATE}
**Time**: {TIME}
**Actual Time**: X hours (Estimated: Y hours)

**Task ID**: #{ID}
**Requirements**: REQ-F-XXX-001, REQ-NFR-XXX-002

---

## Problem

What was the issue or requirement that needed to be addressed?

---

## Investigation

What did you discover during analysis?

1. Analyzed...
2. Reviewed...
3. Found...

---

## Solution

**Architectural Changes**:
- Created...
- Implemented...
- Added...

**TDD Process**:
1. **RED Phase** (X min):
   - Wrote N failing tests for...
2. **GREEN Phase** (X min):
   - Implemented... to pass tests
3. **REFACTOR Phase** (X min):
   - Extracted...
   - Improved...
   - Optimized...

---

## Files Modified

- `/path/to/file1.ext` - NEW (description)
- `/path/to/file2.ext` - Modified (description)
- `/path/to/file3.ext` - Refactored (description)

---

## Test Coverage

**Before**: X% (N tests)
**After**: Y% (M tests)

**Test Breakdown**:
- **Unit Tests**: N tests
- **Integration Tests**: M tests
- **Performance Tests**: P tests

**Coverage Details**:
- `module1.ext`: 100%
- `module2.ext`: 95%

---

## Feature Flag

**Flag Name**: `task-{id}-{description}`
**Status**: Enabled in production ({DATE})
**Rollout Plan**:
- Phase 1: Enabled in dev
- Phase 2: Enabled in staging
- Phase 3: Enabled for 10% prod traffic
- Phase 4: Enabled for 100% prod traffic
- Phase 5: Remove flag and old code

---

## Code Changes

**Before**:
```language
// Old code example
```

**After**:
```language
// New code example
```

---

## Testing

**Manual Testing**:
```bash
# Commands to test the feature
npm test
```

**Results**:
- All N tests passing ✅
- Coverage: Y% (target: ≥X%) ✅
- Performance: Zms avg (target: <Wms) ✅
- Zero regressions ✅

---

## Result

✅ **Task completed successfully**
- Outcome 1
- Outcome 2
- Outcome 3

**Production Metrics** (if applicable):
- Metric 1: Value
- Metric 2: Value

---

## Side Effects

**Positive**:
- Benefit 1
- Benefit 2

**Considerations**:
- Consideration 1
- Consideration 2

---

## Future Considerations

1. Future task 1
2. Future task 2
3. Future task 3

---

## Lessons Learned

1. **Lesson 1**: What we learned
2. **Lesson 2**: What we learned
3. **Lesson 3**: What we learned

---

## Traceability

**Requirements Coverage**:
- REQ-F-XXX-001: ✅ Tests in `test_file.ext::test_function`
- REQ-NFR-XXX-002: ✅ Tests in `test_file.ext::test_function`

**Upstream Traceability**:
- Intent: INT-XXX "Description"
- Epic: PROJ-XXX "Description"
- Story: PROJ-XXX "Description"

**Downstream Traceability**:
- Commit: `hash` "Commit message"
- Release: vX.Y.Z
- Deployment: env-YYYY-MM-DD

---

## Metrics

- **Lines Added**: N
- **Lines Removed**: M (net: +/- X)
- **Tests Added**: N
- **Test Coverage**: X% → Y% (+Z%)
- **Complexity**: Before → After
- **Performance**: Xms → Yms (Z% improvement)

---

## Related

- **Promoted From**: Todo on YYYY-MM-DD HH:MM
- **Related Tasks**: Task #N depends on this
- **Related Issues**: GitHub #N, Jira PROJ-N
- **Documentation**: Updated `/docs/file.md`
- **References**: Links to relevant documentation
