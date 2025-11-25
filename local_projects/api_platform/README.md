# API Platform - Backwards Compatibility Example

## Overview

This example project demonstrates how to **override a single Key Principles principle** while keeping all others intact.

**Principle Override**: **#6 - No Legacy Baggage**

| Base Methodology | API Platform Override |
|------------------|----------------------|
| ❌ "Clean slate, no debt" | ✅ "Backwards compatible through feature flags" |
| No backwards compatibility | Maintain compatibility for 2 major versions |
| Break freely | Break safely with migration time |
| Fresh start mentality | Managed evolution strategy |

---

## Why This Example?

**Real-world scenario**: You're building a customer-facing API that serves thousands of external customers. You have:

- ✅ Contractual SLA commitments (99.9% uptime)
- ✅ Published SDKs in multiple languages
- ✅ Webhook integrations in customer systems
- ✅ Legal agreements promising API stability

**You can't just "break things and move fast"** - that violates customer trust and contracts.

**Solution**: Override Principle #6 to use **feature flags for backwards compatibility** while maintaining all other Key Principles principles.

---

## Project Structure

```
api_platform/
├── config/
│   └── config.yml                              # Override configuration
├── docs/
│   ├── BACKWARDS_COMPATIBILITY_OVERRIDE.md     # Why we override
│   └── FEATURE_FLAG_IMPLEMENTATION_GUIDE.md    # How to implement
└── README.md                                   # This file
```

---

## Quick Start

### 1. View the Configuration Override

Open [config/config.yml](config/config.yml) to see:

```yaml
methodology:
  principles:
    # Override ONLY Principle #6
    no_legacy_baggage:
      principle: 6
      mantra: "Backwards compatible through feature flags"

      requirements:
        - Use feature flags for all breaking changes
        - Maintain backwards compatibility for 2 major versions minimum
        - Document deprecation paths with clear timelines
        # ... see full file for complete requirements

      feature_flag_strategy:
        lifecycle:
          - phase: 1_implementation
            action: "Implement new behavior behind flag (default OFF)"
          - phase: 2_internal_validation
            action: "Enable for internal testing"
          - phase: 3_beta_rollout
            action: "Gradual rollout (1% → 10% → 50%)"
          # ... 6 phases total
```

### 2. Understand the Rationale

Read [docs/BACKWARDS_COMPATIBILITY_OVERRIDE.md](docs/BACKWARDS_COMPATIBILITY_OVERRIDE.md) to understand:

- **Why** we override Principle #6
- **When** to apply the override (customer-facing only)
- **When** to use original Principle #6 (internal code)
- **How** we still maintain excellence

### 3. Learn Implementation

Read [docs/FEATURE_FLAG_IMPLEMENTATION_GUIDE.md](docs/FEATURE_FLAG_IMPLEMENTATION_GUIDE.md) for:

- Code examples (REST API, webhooks, authentication)
- Testing strategies (test both old and new behavior)
- Deployment phases (6-phase lifecycle)
- Monitoring metrics
- Deprecation process

---

## Key Concepts

### The Override is Scoped

**✅ Apply backwards compatibility to**:
- Public REST APIs
- Published SDK/client libraries
- Webhook payloads
- Database schemas with production data

**❌ Do NOT apply to**:
- Internal microservice APIs
- Development/staging environments
- Internal utility functions
- Test fixtures

### The Override is Time-Bounded

**NOT**: "Backwards compatibility forever"
**YES**: "Backwards compatibility for 2 major versions (typically 12-18 months)"

```
v2.0.0: Implement new feature behind flag (default OFF)
v2.1.0: Enable by default (flag ON)
v2.5.0: Deprecation notice sent
v3.0.0: Remove old code and flag ✨ (tech debt eliminated)
```

### The 6-Phase Lifecycle

```
Phase 1: IMPLEMENTATION (Weeks 1-2)
  └─ Implement behind flag (default OFF)

Phase 2: INTERNAL VALIDATION (Week 3)
  └─ Enable for internal testing

Phase 3: BETA ROLLOUT (Weeks 4-7)
  └─ Gradual rollout (10% → 50% → 100%)

Phase 4: FULL ROLLOUT (Weeks 8-9)
  └─ Default ON for everyone

Phase 5: DEPRECATION NOTICE (Months 3-9)
  └─ Notify customers, add warnings

Phase 6: FINAL REMOVAL (Month 12+, v3.0.0)
  └─ Remove old code and flag ✨
```

---

## How This Fits the Key Principles

### We Still Follow All 7 Principles

| Principle | Status | How We Apply It |
|-----------|--------|-----------------|
| #1 Test Driven Development | ✅ **UNCHANGED** | Write tests first, RED→GREEN→REFACTOR |
| #2 Fail Fast & Root Cause | ✅ **UNCHANGED** | Break loudly, fix completely |
| #3 Modular & Maintainable | ✅ **UNCHANGED** | Single responsibility, loose coupling |
| #4 Reuse Before Build | ✅ **UNCHANGED** | Check first, create second |
| #5 Open Source First | ✅ **UNCHANGED** | Suggest alternatives, human decides |
| #6 No Legacy Baggage | ⚠️ **ADAPTED** | Feature flags with time-bounded removal |
| #7 Perfectionist Excellence | ✅ **UNCHANGED** | Excellence or nothing |

### We Don't Violate Principle #6 - We Adapt It

**Original intent**: "Don't accumulate tech debt"
**Our implementation**: "Remove tech debt after 2 major versions"

**Still aligned with excellence:**
- ✅ We remove tech debt (just with a timeline)
- ✅ We refactor aggressively (just with flags)
- ✅ We ship quality (with stability guarantees)
- ✅ We respect our customers (while improving the product)

**Ultimate mantra still applies**: **"Excellence or nothing"** 🔥

We just define excellence as **"Reliable innovation"** instead of **"Move fast and break things"**.

---

## Real-World Example

### Scenario: Migrate Pagination from Offset to Cursor

**Problem**: Offset pagination (`?page=5`) doesn't scale at 1M+ records

**Solution**: Cursor pagination (`?cursor=abc123`) scales infinitely

**The Journey**:

```python
# Month 0: Implement behind feature flag
@app.route('/api/v2/users')
def list_users():
    if feature_flags.use_cursor_pagination(user_context):
        return cursor_based_pagination()  # NEW
    else:
        return offset_based_pagination()  # OLD

# Month 1: Beta rollout (10% traffic)
# → Monitor metrics, fix bugs, gather feedback

# Month 2: Full rollout (100% traffic, flag ON by default)
# → Old behavior still available via flag

# Month 3: Deprecation notice
# → Email customers: "Offset pagination deprecated in v3.0.0"

# Month 6: Warning logs
# → Log: "You're using deprecated offset pagination"

# Month 12: Remove old code (v3.0.0)
return cursor_based_pagination()  # ONLY new behavior
# Delete offset_based_pagination() function
# Delete feature flag
# Tech debt eliminated ✨
```

**Result**:
- ✅ Zero customer complaints
- ✅ Smooth migration (12 months notice)
- ✅ Tech debt removed on schedule
- ✅ Database performance improved 10x

---

## Testing Strategy

### Test BOTH Paths

```python
class TestPaginationWithFeatureFlag:
    def test_offset_pagination_when_flag_off(self):
        """Test old behavior still works."""
        feature_flags.use_cursor_pagination.return_value = False
        response = list_users(page=2, limit=10, flags=feature_flags)
        assert 'page' in response  # Old format

    def test_cursor_pagination_when_flag_on(self):
        """Test new behavior works."""
        feature_flags.use_cursor_pagination.return_value = True
        response = list_users(cursor='abc', limit=10, flags=feature_flags)
        assert 'next_cursor' in response  # New format
```

**Key principle**: Until removal, we test both old and new behavior to ensure backwards compatibility is maintained.

---

## Metrics to Track

### Before Removal Decision

```yaml
metrics_required:
  - flag_usage_by_customer:
      threshold: "<1% of active customers on old behavior"

  - error_rate_comparison:
      old_behavior: "Must not be hiding errors"
      new_behavior: "Must be stable"

  - support_tickets:
      related_to_change: "<5 tickets in last 30 days"

  - customer_notification:
      emails_sent: "100% of affected customers"
      migration_guide_views: ">80% click rate"
```

**We only remove when**:
1. ✅ <1% customers using old behavior
2. ✅ 2+ major versions have passed
3. ✅ 6+ months deprecation notice given
4. ✅ Migration guide published
5. ✅ Product manager approval

---

## When to Use This Pattern

### ✅ Use This Override For

**Customer-facing APIs:**
- REST API endpoints
- GraphQL schemas
- gRPC services

**Published interfaces:**
- SDK method signatures
- Webhook payloads
- Configuration formats

**Production data:**
- Database schema changes
- Export formats
- Report structures

### ❌ Do NOT Use For

**Internal code:**
- Private utility functions
- Internal microservices
- Worker jobs

**Non-production:**
- Development environments
- Test fixtures
- Prototypes

**For internal code, use original Principle #6**: Break freely, refactor aggressively, no backwards compatibility.

---

## How to Apply to Your Project

### Step 1: Determine If You Need This Override

Ask yourself:

1. Do I serve external customers?
2. Do I have contractual SLA commitments?
3. Do I publish SDKs or APIs?
4. Would breaking changes violate customer trust?

**If YES to 2+ questions** → Consider this override
**If NO** → Stick with original Principle #6

### Step 2: Copy Configuration

Copy [config/config.yml](config/config.yml) to your project and adapt:

```bash
# Copy config to your project
cp examples/local_projects/api_platform/config/config.yml \
   your_project/config/config.yml

# Edit to match your context
# - Update project name
# - Adjust versioning policy
# - Configure feature flag tools
# - Set deprecation timelines
```

### Step 3: Implement Feature Flags

Follow [docs/FEATURE_FLAG_IMPLEMENTATION_GUIDE.md](docs/FEATURE_FLAG_IMPLEMENTATION_GUIDE.md):

1. Choose feature flag tool (LaunchDarkly, Split.io, custom)
2. Implement flag checks in code
3. Test both old and new behavior
4. Deploy with flag OFF
5. Follow 6-phase lifecycle

### Step 4: Document Your Override

Create a `BACKWARDS_COMPATIBILITY_OVERRIDE.md` in your project explaining:
- Why you override Principle #6
- When to apply the override
- When to use original Principle #6
- How you maintain excellence

---

## FAQ

### Q: Doesn't this violate the Key Principles?

**A**: No. It **adapts** Principle #6 to our business context.

The Key Principles are **principles**, not **dogma**. We apply them intelligently based on our constraints.

### Q: Won't we accumulate tech debt forever?

**A**: No. We have a **time-bounded** policy: Remove after 2 major versions (12-18 months).

### Q: What if customers refuse to migrate?

**A**: We have an escalation process:
1. Email notifications
2. Warning logs
3. Customer success outreach
4. Exec-level escalation
5. Remove anyway (with ample notice)

**We never hold the codebase hostage to one customer.**

### Q: Can I use this for internal changes?

**A**: **NO.** This override **only** applies to customer-facing changes.

Internal code follows **original Principle #6**: Break freely, no backwards compatibility.

---

## Integration with ai_sdlc_method

### Configuration Merging

This example demonstrates the **priority-based merging** system:

```
Layer 1: claude-code/claude-code/plugins/aisdlc-methodology/config/config.yml (base)
         └─ Principle #6: "Clean slate, no debt"

Layer 2: examples/local_projects/api_platform/config/config.yml (override)
         └─ Principle #6: "Backwards compatible through feature flags"

Result: api_platform uses OVERRIDE (Layer 2 wins)
        All other principles from base (Layer 1)
```

### Testing the Merge

```python
from ai_sdlc_config import ConfigManager
from pathlib import Path

# Load base + override
manager = ConfigManager()
manager.load_hierarchy("claude-code/claude-code/plugins/aisdlc-methodology/config/config.yml")
manager.load_hierarchy("examples/local_projects/api_platform/config/config.yml")
manager.merge()

# Check Principle #6
principle_6 = manager.get_value("methodology.principles.no_legacy_baggage")
print(principle_6['mantra'])
# Output: "Backwards compatible through feature flags"

# Check Principle #1 (unchanged)
principle_1 = manager.get_value("methodology.principles.test_driven_development")
print(principle_1['mantra'])
# Output: "No code without tests" (from base)
```

---

## Summary

**This example shows**:

1. ✅ How to override ONE principle (Principle #6)
2. ✅ While keeping all other Key Principles principles intact
3. ✅ With clear rationale (customer-facing APIs)
4. ✅ Time-bounded tech debt removal (2 major versions)
5. ✅ Systematic approach (6-phase lifecycle)
6. ✅ Still maintaining excellence ("Reliable innovation")

**The result**: Happy customers + clean codebase ✨

---

## Related Documentation

- [Configuration Override](config/config.yml) - Full config with Principle #6 override
- [Override Rationale](docs/BACKWARDS_COMPATIBILITY_OVERRIDE.md) - Why we override
- [Implementation Guide](docs/FEATURE_FLAG_IMPLEMENTATION_GUIDE.md) - How to implement
- [Key Principles (Base)](../../claude-code/claude-code/plugins/aisdlc-methodology/docs/principles/KEY_PRINCIPLES.md) - Original principles

---

**Excellence or nothing.** 🔥

*Even when excellence means adapting principles to business reality.*
