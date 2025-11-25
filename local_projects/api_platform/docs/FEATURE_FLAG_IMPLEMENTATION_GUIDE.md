# Feature Flag Implementation Guide

## For API Platform - Backwards Compatibility Strategy

This guide shows how to implement feature flags to maintain backwards compatibility while evolving your API.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [When to Use Feature Flags](#when-to-use-feature-flags)
3. [Implementation Examples](#implementation-examples)
4. [Testing Strategy](#testing-strategy)
5. [Deployment & Rollout](#deployment--rollout)
6. [Monitoring & Metrics](#monitoring--metrics)
7. [Deprecation & Removal](#deprecation--removal)

---

## Quick Start

### Step 1: Is This Change Breaking?

Use this decision tree:

```
Does this change affect customers?
├── NO → Deploy directly (no flag needed)
└── YES → Does it break existing behavior?
    ├── NO → Deploy directly (backwards compatible)
    └── YES → REQUIRES FEATURE FLAG ⚠️
```

### Step 2: Create Feature Flag

```python
# In feature_flags.py
from launchdarkly import LDClient
from typing import Any, Dict

class FeatureFlags:
    """Centralized feature flag management."""

    def __init__(self, ld_client: LDClient):
        self.client = ld_client

    def use_new_auth_flow(self, user: Dict[str, Any]) -> bool:
        """
        Feature: New OAuth 2.1 authentication flow

        Default: False (use OAuth 2.0 legacy)
        Rollout: 2024-06-01
        Deprecation: 2024-12-01
        Removal: v3.0.0 (2025-06-01)

        Migration Guide: /docs/migration/oauth-2.1.md
        """
        return self.client.variation(
            "use-new-auth-flow",
            user,
            default=False  # Safe default
        )
```

### Step 3: Use Flag in Code

```python
# In authentication.py
def authenticate(request, feature_flags: FeatureFlags):
    user_context = get_user_context(request)

    if feature_flags.use_new_auth_flow(user_context):
        # New behavior
        return oauth21_authenticate(request)
    else:
        # Old behavior (backwards compatible)
        return oauth20_authenticate(request)
```

---

## When to Use Feature Flags

### ✅ REQUIRED for Breaking Changes

**API Changes:**
- Removing or renaming endpoints
- Changing request/response schemas
- Modifying authentication mechanisms
- Altering rate limits (downward)
- Changing error response formats

**Data Changes:**
- Database schema migrations affecting queries
- Webhook payload structure changes
- Export format changes (CSV, JSON)
- Date/time format changes

**Behavior Changes:**
- Default value changes
- Calculation logic changes (pricing, scoring)
- Workflow order changes
- Validation rule changes (more strict)

### ❌ NOT REQUIRED for Additive Changes

**Safe to Deploy Directly:**
- Adding new endpoints (without removing old)
- Adding optional fields to responses
- Adding new query parameters (with defaults)
- Performance improvements (no behavior change)
- Bug fixes (restoring documented behavior)
- Internal refactoring (no API changes)

---

## Implementation Examples

### Example 1: REST API Endpoint Change

**Scenario**: Changing pagination from offset-based to cursor-based

#### Before (v2.x):
```python
# GET /api/v2/users?page=5&limit=20
@app.route('/api/v2/users')
def list_users():
    page = request.args.get('page', 1, type=int)
    limit = request.args.get('limit', 20, type=int)
    offset = (page - 1) * limit

    users = db.query(User).offset(offset).limit(limit).all()
    return jsonify({
        'users': users,
        'page': page,
        'total_pages': (total_users // limit) + 1
    })
```

#### After (with Feature Flag):
```python
# GET /api/v2/users?cursor=abc123&limit=20
@app.route('/api/v2/users')
def list_users():
    user_context = get_user_context(request)

    if feature_flags.use_cursor_pagination(user_context):
        # NEW: Cursor-based pagination
        cursor = request.args.get('cursor', None)
        limit = request.args.get('limit', 20, type=int)

        users, next_cursor = db.query_with_cursor(User, cursor, limit)
        return jsonify({
            'users': users,
            'next_cursor': next_cursor,
            'has_more': next_cursor is not None
        })
    else:
        # OLD: Offset-based pagination (backwards compatible)
        page = request.args.get('page', 1, type=int)
        limit = request.args.get('limit', 20, type=int)
        offset = (page - 1) * limit

        users = db.query(User).offset(offset).limit(limit).all()
        return jsonify({
            'users': users,
            'page': page,
            'total_pages': (total_users // limit) + 1
        })
```

#### Feature Flag Configuration:
```yaml
# LaunchDarkly configuration
flag_key: use-cursor-pagination
name: "Use Cursor-Based Pagination"
description: "Migrate from offset to cursor pagination for scalability"

default_value: false

targeting_rules:
  - description: "Beta customers"
    clauses:
      - attribute: "customer_tier"
        operator: "in"
        values: ["beta", "enterprise"]
    variation: true

  - description: "Gradual rollout"
    rollout:
      variations:
        - variation: true
          weight: 0  # Start at 0%, increase gradually

tags:
  - "api"
  - "pagination"
  - "v3.0.0"

metadata:
  created: "2024-05-01"
  owner: "platform-team"
  deprecation_date: "2025-01-01"
  removal_version: "v3.0.0"
```

---

### Example 2: Webhook Payload Change

**Scenario**: Adding `user_id` to payment webhook (breaking change for strict parsers)

#### Old Webhook Payload:
```json
{
  "event": "payment.completed",
  "payment_id": "pay_123",
  "amount": 1000,
  "currency": "USD",
  "timestamp": "2024-05-01T10:00:00Z"
}
```

#### New Webhook Payload:
```json
{
  "event": "payment.completed",
  "payment_id": "pay_123",
  "user_id": "user_456",  // NEW FIELD
  "amount": 1000,
  "currency": "USD",
  "timestamp": "2024-05-01T10:00:00Z"
}
```

#### Implementation:
```python
def send_payment_webhook(payment: Payment, customer: Customer):
    """Send payment completed webhook to customer's endpoint."""

    # Base payload (always included)
    payload = {
        "event": "payment.completed",
        "payment_id": payment.id,
        "amount": payment.amount,
        "currency": payment.currency,
        "timestamp": payment.completed_at.isoformat()
    }

    # Feature flag: Include user_id in payload
    if feature_flags.include_user_in_webhook(customer):
        payload["user_id"] = payment.user_id

    # Send webhook
    send_webhook(customer.webhook_url, payload)
```

#### Gradual Rollout Strategy:
```python
# 1. Default OFF (old payload)
# 2. Enable for internal testing
# 3. Enable for beta customers who opt-in
# 4. Gradual rollout (10% → 50% → 100%)
# 5. Default ON (new payload for everyone)
# 6. Remove flag after 2 major versions
```

---

### Example 3: Authentication Mechanism Change

**Scenario**: Migrating from API keys to OAuth 2.1

#### Implementation:
```python
class AuthenticationService:
    def __init__(self, feature_flags: FeatureFlags):
        self.flags = feature_flags

    def authenticate(self, request) -> User:
        """Authenticate user with backwards compatibility."""

        user_context = self._get_user_context(request)

        if self.flags.use_oauth21(user_context):
            # NEW: OAuth 2.1 authentication
            token = self._extract_bearer_token(request)
            return self._verify_oauth21_token(token)
        else:
            # OLD: API key authentication (backwards compatible)
            api_key = self._extract_api_key(request)
            return self._verify_api_key(api_key)

    def _get_user_context(self, request) -> dict:
        """Build LaunchDarkly user context for targeting."""
        # Try OAuth token first
        if 'Authorization' in request.headers:
            token = self._extract_bearer_token(request)
            user_id = self._decode_user_id_from_token(token)
        # Fall back to API key
        elif 'X-API-Key' in request.headers:
            api_key = self._extract_api_key(request)
            user_id = self._get_user_id_from_api_key(api_key)
        else:
            user_id = "anonymous"

        return {
            "key": user_id,
            "custom": {
                "auth_method": "oauth" if 'Authorization' in request.headers else "api_key"
            }
        }
```

---

## Testing Strategy

### Principle: Test BOTH Paths

When using feature flags, you must test:
1. ✅ Old behavior (flag OFF)
2. ✅ New behavior (flag ON)
3. ✅ Flag toggle (switching between states)

### Unit Tests

```python
import pytest
from unittest.mock import Mock

class TestPaginationWithFeatureFlag:
    """Test both old and new pagination behavior."""

    def test_offset_pagination_when_flag_off(self):
        """Test old behavior: offset-based pagination."""
        # GIVEN: Feature flag is OFF
        feature_flags = Mock()
        feature_flags.use_cursor_pagination.return_value = False

        # WHEN: Request with page parameter
        response = list_users(page=2, limit=10, flags=feature_flags)

        # THEN: Returns offset-based pagination
        assert 'page' in response
        assert 'total_pages' in response
        assert 'cursor' not in response

    def test_cursor_pagination_when_flag_on(self):
        """Test new behavior: cursor-based pagination."""
        # GIVEN: Feature flag is ON
        feature_flags = Mock()
        feature_flags.use_cursor_pagination.return_value = True

        # WHEN: Request with cursor parameter
        response = list_users(cursor='abc123', limit=10, flags=feature_flags)

        # THEN: Returns cursor-based pagination
        assert 'next_cursor' in response
        assert 'has_more' in response
        assert 'page' not in response
```

### Integration Tests

```python
class TestAuthenticationIntegration:
    """Integration tests for authentication with feature flag."""

    @pytest.fixture
    def feature_flags(self):
        return FeatureFlags(ld_client=Mock())

    def test_api_key_auth_when_flag_off(self, feature_flags):
        """Test old auth method still works."""
        feature_flags.use_oauth21.return_value = False

        request = create_request_with_api_key("key_123")
        user = authenticate(request, feature_flags)

        assert user.id == "user_from_api_key"

    def test_oauth21_auth_when_flag_on(self, feature_flags):
        """Test new auth method works."""
        feature_flags.use_oauth21.return_value = True

        request = create_request_with_oauth_token("token_abc")
        user = authenticate(request, feature_flags)

        assert user.id == "user_from_oauth"

    def test_graceful_fallback_on_flag_toggle(self, feature_flags):
        """Test switching flag mid-flight doesn't break."""
        # Start with flag OFF
        feature_flags.use_oauth21.return_value = False
        request1 = create_request_with_api_key("key_123")
        user1 = authenticate(request1, feature_flags)

        # Toggle flag ON
        feature_flags.use_oauth21.return_value = True
        request2 = create_request_with_oauth_token("token_abc")
        user2 = authenticate(request2, feature_flags)

        # Both should succeed
        assert user1 is not None
        assert user2 is not None
```

### Contract Tests (Backwards Compatibility)

```python
import pact

class TestAPIContractWithFeatureFlag:
    """Ensure API contract is maintained across flag states."""

    def test_users_endpoint_contract_flag_off(self):
        """Old pagination contract must be honored."""
        pact = Pact(
            consumer="mobile_app_v2",
            provider="api_platform"
        )

        (pact
         .given("users exist")
         .upon_receiving("request for users page 2")
         .with_request("GET", "/api/v2/users", query={"page": 2})
         .will_respond_with(200, body={
             "users": pact.each_like({
                 "id": "user_123",
                 "name": "Test User"
             }),
             "page": 2,
             "total_pages": 10
         }))

        with pact:
            response = requests.get(
                "http://localhost/api/v2/users?page=2",
                headers={"X-Feature-Flag-Override": "use-cursor-pagination=false"}
            )
            assert response.status_code == 200
```

---

## Deployment & Rollout

### Phase-by-Phase Rollout

#### Phase 1: Implementation (Week 1-2)
```yaml
flag_state: OFF for everyone
default_value: false
targeting: []

actions:
  - Merge feature flag code to main
  - Deploy to production (flag OFF)
  - Verify old behavior still works
  - Monitor for any errors
```

#### Phase 2: Internal Testing (Week 3)
```yaml
flag_state: ON for internal users only
default_value: false
targeting:
  - rule: "Internal employees"
    attribute: email
    operator: ends_with
    value: "@company.com"
    variation: true

actions:
  - Enable for internal staging
  - Team validates new behavior
  - Fix any bugs found
  - Performance testing
```

#### Phase 3: Beta Rollout (Week 4-7)
```yaml
flag_state: Gradual rollout to customers
default_value: false

targeting:
  - rule: "Beta customers (opted in)"
    attribute: customer_tier
    operator: in
    values: ["beta", "early_adopter"]
    variation: true

  - rule: "Progressive rollout"
    rollout:
      - variation: true
        weight: 10  # 10% of remaining traffic

actions:
  - Week 4: 10% rollout
  - Week 5: 25% rollout
  - Week 6: 50% rollout
  - Week 7: 100% rollout
  - Monitor metrics at each step
```

#### Phase 4: Full Rollout (Week 8-9)
```yaml
flag_state: ON for everyone
default_value: true  # FLIPPED!

actions:
  - Change default to true
  - 100% of traffic on new behavior
  - Old behavior still available (flag can be set to false)
  - Monitor for issues
```

#### Phase 5: Deprecation Notice (Month 3-9)
```yaml
flag_state: ON, but old behavior available
default_value: true

actions:
  - Send email to customers still using old behavior
  - Add warning logs when old behavior used
  - Update docs with "DEPRECATED" banner
  - Customer success outreach
```

#### Phase 6: Removal (Month 12+)
```yaml
flag_state: REMOVED
code_state: Old behavior deleted

actions:
  - Remove feature flag from LaunchDarkly
  - Remove old code path
  - Remove feature flag checks
  - Update tests (only test new behavior)
  - Celebrate tech debt removal! 🎉
```

---

## Monitoring & Metrics

### Key Metrics to Track

#### 1. Flag Usage by Customer
```python
# Track which customers are on old vs new behavior
@app.before_request
def track_feature_flag_usage():
    if request.endpoint == 'list_users':
        user_context = get_user_context(request)
        flag_value = feature_flags.use_cursor_pagination(user_context)

        metrics.increment(
            'feature_flag.usage',
            tags=[
                f'flag:use-cursor-pagination',
                f'value:{flag_value}',
                f'customer_id:{user_context["key"]}'
            ]
        )
```

#### 2. Error Rates (Old vs New)
```python
# Compare error rates between behaviors
try:
    if feature_flags.use_new_auth_flow(user_context):
        result = oauth21_authenticate(request)
        metrics.increment('auth.success', tags=['method:oauth21'])
    else:
        result = oauth20_authenticate(request)
        metrics.increment('auth.success', tags=['method:oauth20'])
except AuthenticationError as e:
    flag_state = 'on' if feature_flags.use_new_auth_flow(user_context) else 'off'
    metrics.increment(
        'auth.error',
        tags=[f'flag:use-new-auth-flow={flag_state}', f'error:{e.code}']
    )
    raise
```

#### 3. Performance Impact
```python
# Measure latency difference
with metrics.timer('api.latency', tags=['endpoint:list_users', 'flag:cursor_pagination']):
    if feature_flags.use_cursor_pagination(user_context):
        return cursor_based_pagination()
    else:
        return offset_based_pagination()
```

### Dashboard Example

```
Feature Flag Dashboard: use-cursor-pagination
================================================

Current State: 50% rollout (Week 6)

Usage:
  - Old behavior (offset): 5,234 customers (50%)
  - New behavior (cursor): 5,127 customers (50%)

Error Rates:
  - Old behavior: 0.12% error rate
  - New behavior: 0.08% error rate ✅

Performance:
  - Old behavior: p95 = 450ms
  - New behavior: p95 = 180ms ✅ (60% faster!)

Customer Feedback:
  - Support tickets: 2 (both resolved)
  - Positive feedback: 12 customers

Recommendation: ✅ Proceed to 100% rollout
```

---

## Deprecation & Removal

### Deprecation Checklist

Before deprecating old behavior:

- [ ] New behavior stable for 30+ days
- [ ] Error rate <0.1%
- [ ] Performance acceptable
- [ ] Customer feedback neutral/positive
- [ ] Migration guide published
- [ ] Support team trained
- [ ] Product manager approval

### Deprecation Communication

#### Email Template
```
Subject: [ACTION REQUIRED] Pagination API Deprecation - Migrate by March 2025

Hello [Customer Name],

We're writing to inform you about an upcoming change to the API Platform.

WHAT'S CHANGING:
- Offset-based pagination (?page=5) will be deprecated
- Cursor-based pagination (?cursor=abc) is the new method
- Old pagination will be removed in v3.0.0 (March 1, 2025)

WHY:
- 60% faster performance
- Scales to millions of records
- More reliable at high volumes

ACTION REQUIRED:
1. Read migration guide: https://docs.example.com/migration/cursor-pagination
2. Update your code by February 1, 2025
3. Test in staging environment
4. Deploy to production

TIMELINE:
- Today: Announcement
- January 15, 2025: Warning logs enabled
- February 1, 2025: Final reminder
- March 1, 2025: Old pagination removed

NEED HELP?
- Migration guide: https://docs.example.com/migration/cursor-pagination
- Support: support@example.com
- Office hours: Fridays 10am-12pm PST

Thank you,
The API Platform Team
```

### Removal Checklist

Before removing old behavior:

- [ ] 6+ months since deprecation notice
- [ ] <1% customers using old behavior
- [ ] 2+ major versions have passed
- [ ] Final reminder sent (30 days before)
- [ ] Exec approval for any remaining holdouts

### Removal Process

```python
# Step 1: Remove feature flag check (v3.0.0)
# BEFORE:
if feature_flags.use_cursor_pagination(user_context):
    return cursor_based_pagination()
else:
    return offset_based_pagination()

# AFTER:
return cursor_based_pagination()  # Only new behavior


# Step 2: Delete old code
# Delete offset_based_pagination() function
# Delete related tests
# Delete documentation for old method


# Step 3: Remove feature flag from LaunchDarkly
# Via LaunchDarkly UI or API:
ld_client.delete_feature_flag("use-cursor-pagination")


# Step 4: Update tests
# Remove tests for old behavior
# Keep only new behavior tests


# Step 5: Update documentation
# Remove "DEPRECATED" banners
# Update examples to only show new method
```

---

## Summary

**Feature flags enable backwards compatibility without accumulating permanent tech debt.**

### Key Principles

1. ✅ Use flags for breaking changes only
2. ✅ Test both old and new behavior
3. ✅ Gradual rollout (10% → 50% → 100%)
4. ✅ Monitor metrics closely
5. ✅ Communicate deprecation clearly
6. ✅ Remove after 2 major versions

### Timeline at a Glance

```
Month 0: Implement behind flag (default OFF)
Month 1: Beta rollout (10-50%)
Month 2: Full rollout (100%, default ON)
Month 3: Deprecation notice sent
Month 6: Warning logs enabled
Month 12: Remove code and flag (v3.0.0)
```

**Result**: Happy customers + clean codebase ✨

---

## Additional Resources

- [Backwards Compatibility Override Documentation](BACKWARDS_COMPATIBILITY_OVERRIDE.md)
- [LaunchDarkly Best Practices](https://docs.launchdarkly.com/home/flags/best-practices)
- [Semantic Versioning](https://semver.org/)
- [API Versioning Strategies](https://restfulapi.net/versioning/)
