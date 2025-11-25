# Backwards Compatibility Override - API Platform

## Why We Override Principle #6

The base **aisdlc-methodology** follows Principle #6: **"No Legacy Baggage"**

**Original Mantra**: "Clean slate, no debt"

**Original Requirements**:
- No backwards compatibility constraints
- No technical debt
- Replace completely if needed
- Fresh start mentality

### Why This Doesn't Work For Us

**API Platform Context**:
- Serves **10,000+ external customers**
- **Contractual SLA commitments** (99.9% uptime)
- **Published SDKs** in 5 languages (Python, JavaScript, Java, Go, Ruby)
- **Webhook integrations** in customer systems
- **Legal agreements** promise stability

**Business Impact of Breaking Changes**:
- Customer churn (lost revenue)
- Support ticket flood (increased costs)
- Reputation damage (trust erosion)
- Contract violations (legal risk)
- Emergency customer firefighting (team burnout)

### Our Override: "Backwards Compatible Through Feature Flags"

**Modified Mantra**: "Backwards compatible through feature flags"

**Modified Requirements**:
1. Use feature flags for all breaking changes
2. Maintain backwards compatibility for 2 major versions
3. Document deprecation paths with clear timelines
4. Provide comprehensive migration guides
5. Monitor usage before removal
6. Clean tech debt only after safe deprecation period

## The Principle is NOT Abandoned - It's Adapted

### What We Keep From Principle #6

✅ **We still hate technical debt** - We remove it systematically
✅ **We still want clean code** - We refactor aggressively
✅ **We still replace bad designs** - We just do it safely
✅ **We still avoid legacy baggage** - We just give customers time

### What We Change

❌ **NOT**: "Break everything, customers adapt"
✅ **YES**: "Break intentionally, customers migrate smoothly"

❌ **NOT**: "No backwards compatibility ever"
✅ **YES**: "Time-bounded backwards compatibility with clear sunset"

❌ **NOT**: "Tech debt lives forever"
✅ **YES**: "Tech debt has expiration date (2 major versions)"

## How It Works: The 6-Phase Lifecycle

### Example: Migrating from OAuth 2.0 → OAuth 2.1

```
├── Phase 1: IMPLEMENTATION (Weeks 1-2)
│   ├── Implement OAuth 2.1 behind feature flag
│   ├── Flag name: USE_NEW_AUTH_FLOW
│   ├── Default: OFF (everyone on OAuth 2.0)
│   └── Tests for both old and new paths
│
├── Phase 2: INTERNAL VALIDATION (Week 3)
│   ├── Enable flag for internal staging
│   ├── Platform team validates
│   └── Fix any bugs found
│
├── Phase 3: BETA ROLLOUT (Weeks 4-7)
│   ├── 1% beta customers (Week 4)
│   ├── 10% early adopters (Week 5)
│   ├── 50% progressive rollout (Week 6-7)
│   └── Monitor metrics, gather feedback
│
├── Phase 4: FULL ROLLOUT (Weeks 8-9)
│   ├── 100% traffic on OAuth 2.1
│   ├── Flag now ON by default
│   └── OAuth 2.0 still available via flag
│
├── Phase 5: DEPRECATION NOTICE (Months 3-9)
│   ├── Email all customers using OAuth 2.0
│   ├── Update docs with migration guide
│   ├── Add warning logs for old auth flow
│   ├── Dashboard shows "you're using deprecated auth"
│   └── Support team reaches out to stragglers
│
└── Phase 6: FINAL REMOVAL (Month 12, v3.0.0)
    ├── Remove OAuth 2.0 code
    ├── Remove USE_NEW_AUTH_FLOW flag
    ├── Clean up tests for old behavior
    └── Tech debt eliminated ✨
```

### Timeline: 1 Year from Implementation to Removal

**That's the compromise:**
- ✅ Customers get 12 months to migrate (happy customers)
- ✅ We remove tech debt after 2 major versions (clean code)
- ✅ No surprise breakage (trust maintained)
- ✅ Systematic debt removal (predictable cleanup)

## When This Override Applies

### ✅ Use Backwards Compatibility (Flag-Based Evolution)

**Public APIs:**
- REST API endpoints (GET /users, POST /payments)
- GraphQL schema changes (field removal, type changes)
- gRPC service definitions

**Published Interfaces:**
- SDK method signatures (Python SDK, JavaScript SDK)
- Webhook payload structures
- Configuration file formats (.env, config.yml)

**Customer-Visible Data:**
- Database schema changes affecting customer queries
- Report formats (CSV exports, PDF invoices)
- Email templates with customer-facing data

### ❌ Do NOT Use (Follow Original Principle #6)

**Internal Code:**
- Private utility functions
- Internal microservice APIs (not customer-facing)
- Worker job implementations
- Background task logic

**Non-Production:**
- Development environment code
- Staging environment experiments
- Test fixtures and mocks
- Local development scripts

**Experimental Features:**
- Beta features (marked "experimental")
- Alpha features (invite-only)
- Research prototypes
- Internal tools

## Metrics We Track

### Before Removal Decision

```yaml
metrics_required:
  - flag_usage_by_customer:
      threshold: "<1% of active customers on old behavior"
      measured_by: "LaunchDarkly analytics"

  - error_rate_comparison:
      old_behavior: "Must not be hiding errors"
      new_behavior: "Must be stable"
      measured_by: "DataDog APM"

  - support_tickets:
      related_to_change: "<5 tickets in last 30 days"
      measured_by: "Zendesk tags"

  - customer_notification:
      emails_sent: "100% of affected customers"
      clicks_on_migration_guide: ">80%"
      measured_by: "SendGrid analytics"
```

### We Only Remove When

1. ✅ <1% customers using old behavior
2. ✅ 2+ major versions have passed
3. ✅ 6+ months deprecation notice given
4. ✅ Migration guide published and validated
5. ✅ Support team trained on migration
6. ✅ Product manager approval
7. ✅ Customer success team sign-off

## Real-World Example

### Flag: `ENABLE_PAGINATION_V2`

**Problem**: Offset-based pagination (`?page=5`) doesn't scale at 1M+ records

**Solution**: Cursor-based pagination (`?cursor=abc123`)

**Timeline**:
```
2024-05-01: Implement cursor pagination (flag OFF)
2024-06-01: Beta rollout (10% traffic)
2024-08-01: Full rollout (flag ON, 100% traffic)
2024-09-01: Deprecation notice sent
            "Offset pagination deprecated in v3.0.0"
2024-11-01: Warning logs added
            "You're using offset pagination (deprecated)"
2025-03-01: v3.0.0 released
            Remove offset pagination code
            Remove ENABLE_PAGINATION_V2 flag
            Tech debt eliminated
```

**Result**:
- ✅ Zero customer complaints
- ✅ Smooth migration (9 months notice)
- ✅ Tech debt removed on schedule
- ✅ Database performance improved 10x

## How to Use This Override

### 1. Check Scope

Is this change customer-facing?
- **YES** → Follow backwards compatibility lifecycle
- **NO** → Follow original Principle #6 (break freely)

### 2. Create Feature Flag

```python
# Example: LaunchDarkly flag
USE_NEW_PAYMENT_FLOW = ld_client.variation(
    "use-new-payment-flow",
    user,
    default=False  # Old behavior by default
)

if USE_NEW_PAYMENT_FLOW:
    return new_payment_processor.process(payment)
else:
    return legacy_payment_processor.process(payment)
```

### 3. Follow 6-Phase Lifecycle

See above for detailed phases.

### 4. Document Everything

**Required Documentation**:
- API changelog entry
- Migration guide (step-by-step)
- OpenAPI spec annotations
- Customer-facing release notes
- Internal runbook for support team

### 5. Monitor Metrics

Track flag usage, errors, and customer feedback.

### 6. Remove After 2 Major Versions

**Schedule removal in advance:**
- v2.3.0: Implement behind flag
- v2.5.0: Enable by default
- v2.8.0: Deprecation notice
- v3.0.0: Remove old code and flag ✨

## FAQ

### Q: Doesn't this violate the Sacred Seven?

**A**: No. It **adapts** Principle #6 to our business context.

The Sacred Seven are **principles**, not **dogma**. They guide us toward excellence, but we must apply them intelligently.

**Original Principle #6 is perfect for**:
- Internal tools
- Greenfield projects
- Research prototypes

**Our override is necessary for**:
- Customer-facing APIs
- Contractual obligations
- Trust-based relationships

### Q: Won't we accumulate tech debt forever?

**A**: No. We have a **time-bounded** deprecation policy.

**Hard Rule**: Tech debt must be removed within 2 major versions (typically 12-18 months).

If it's not removed, it's escalated to engineering leadership.

### Q: What if customers refuse to migrate?

**A**: We have an escalation process:

```
Month 1-3: Email notifications
Month 4-6: Dashboard warnings
Month 7-9: Personal outreach from customer success
Month 10: Final warning (30 days)
Month 11: Exec-level escalation (CEO → CEO)
Month 12: Remove anyway (with advance notice)
```

**We never hold the codebase hostage to one customer.**

### Q: Can I use this for internal changes?

**A**: **NO.** This override **only** applies to customer-facing changes.

Internal code follows **original Principle #6**: Break freely, refactor aggressively, no backwards compatibility.

## Summary

**We modified Principle #6 from**:
```
"Clean slate, no debt"
→ Break things freely
```

**To**:
```
"Backwards compatible through feature flags"
→ Break things safely with customer migration time
```

**This is still excellence:**
- ✅ We remove tech debt (just with a timeline)
- ✅ We refactor aggressively (just with flags)
- ✅ We ship quality (with stability guarantees)
- ✅ We respect our customers (while improving the product)

**The ultimate mantra still applies**: **"Excellence or nothing"** 🔥

We just define excellence as **"Reliable innovation"** instead of **"Move fast and break things"**.
