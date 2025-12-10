# ADR-010: Cross-Domain Fidelity Verification Architecture

**Status**: Accepted
**Date**: 2025-12-10
**Implements**: REQ-COV-01, REQ-COV-02, REQ-COV-03, REQ-COV-04, REQ-COV-05, REQ-COV-06, REQ-COV-07, REQ-COV-08

---

## Context

Enterprise data platforms operate multiple domains (Finance, Risk, Regulatory, Operations) that must maintain consistent views of the same business reality. Traditional approaches rely on:

1. **Manual reconciliation** - Expensive, error-prone, after-the-fact
2. **ETL validation rules** - Scattered, inconsistent, hard to audit
3. **Downstream monitoring** - Detects problems too late

CDME requires a formal system for:
- Defining cross-domain relationships as **contracts**
- Expressing consistency requirements as **mathematical invariants**
- **Proving** fidelity through cryptographic verification
- **Enforcing** invariants at execution time, not just detecting violations

### Options Considered

1. **Ad-hoc SQL reconciliation** - Custom queries per domain pair
2. **Great Expectations / dbt tests** - Data quality tool integration
3. **Formal covariance contracts** - Mathematical invariants with proofs
4. **Blockchain-based verification** - Distributed ledger for audit

---

## Decision

**CDME implements formal Covariance Contracts with Fidelity Invariants, Cryptographic Verification, and Contract Enforcement.**

### Why Formal Contracts

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| Ad-hoc SQL | Simple, flexible | No audit trail, inconsistent | Reject |
| GE/dbt | Ecosystem integration | Detection only, no proofs | Reject |
| **Formal Contracts** | Provable, auditable, enforceable | Complexity | **Accept** |
| Blockchain | Tamper-proof | Overkill, performance | Reject |

### Architectural Components

```
┌─────────────────────────────────────────────────────────────────┐
│                 CROSS-DOMAIN FIDELITY SYSTEM                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐   ┌──────────────────┐                   │
│  │ Contract Registry │   │ Invariant Engine │                   │
│  │                   │   │                   │                   │
│  │ - Contract YAML   │   │ - Evaluators      │                   │
│  │ - Version control │   │ - Comparators     │                   │
│  │ - Access control  │   │ - Aggregators     │                   │
│  └────────┬──────────┘   └────────┬──────────┘                   │
│           │                       │                              │
│           └───────────┬───────────┘                              │
│                       │                                          │
│           ┌───────────▼───────────┐                             │
│           │ Verification Service  │                             │
│           │                       │                             │
│           │ - Snapshot domains    │                             │
│           │ - Evaluate invariants │                             │
│           │ - Generate proofs     │                             │
│           └───────────┬───────────┘                             │
│                       │                                          │
│     ┌─────────────────┼─────────────────┐                       │
│     │                 │                 │                       │
│     ▼                 ▼                 ▼                       │
│ ┌────────┐     ┌──────────┐     ┌──────────────┐               │
│ │ Certs  │     │ Breaches │     │ Enforcement  │               │
│ │ Store  │     │ Handler  │     │   Engine     │               │
│ └────────┘     └──────────┘     └──────────────┘               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Contract Registry Design

### Contract Schema

```yaml
contract:
  id: string              # Unique identifier
  version: semver         # Semantic version
  status: DRAFT | ACTIVE | DEPRECATED

  domains:
    source:
      name: string        # Domain name (e.g., "finance")
      entity: string      # LDM entity name
      grain: GrainLevel   # ATOMIC | DAILY | MONTHLY | etc.
      grain_key: [string] # Key fields for grain

    target:
      name: string
      entity: string
      grain: GrainLevel
      grain_key: [string]

  mapping:
    type: COVARIANT | CONTRAVARIANT | BIDIRECTIONAL
    cardinality: "1:1" | "N:1" | "1:N" | "M:N"
    join_key: string | [string]

  grain_alignment:
    required: boolean
    cross_grain_morphism: string | null  # Required if grains differ

  invariants: [InvariantRef]

  enforcement:
    mode: STRICT | DEFERRED | ADVISORY
    on_violation: HALT | QUARANTINE | ALERT
    deferral_window: duration | null  # For DEFERRED mode
```

### Invariant Types

| Type | Expression Pattern | Tolerance Model |
|------|-------------------|-----------------|
| CONSERVATION | `sum(A.field) == sum(B.field)` | Absolute or percentage |
| COVERAGE | `count(B) >= count(A)` | Absolute count |
| ALIGNMENT | `A.field == B.field` | None (exact) or date tolerance |
| CONTAINMENT | `keys(A) ⊆ keys(B)` | None (exact) |

---

## Verification Service Design

### Verification Flow

```python
class FidelityVerificationService:

    def verify(
        self,
        contract_id: str,
        epoch: str,
        mode: VerificationMode = EXHAUSTIVE
    ) -> FidelityCertificate:

        # 1. Load contract
        contract = self.contract_registry.get(contract_id)

        # 2. Acquire domain snapshots (hash-bound)
        source_snapshot = self.snapshot_service.snapshot(
            domain=contract.domains.source,
            epoch=epoch
        )
        target_snapshot = self.snapshot_service.snapshot(
            domain=contract.domains.target,
            epoch=epoch
        )

        # 3. Evaluate invariants
        results = []
        for invariant_ref in contract.invariants:
            invariant = self.invariant_registry.get(invariant_ref)
            result = self.invariant_engine.evaluate(
                invariant=invariant,
                source=source_snapshot,
                target=target_snapshot,
                mode=mode
            )
            results.append(result)

        # 4. Compute overall status
        overall_status = self._compute_status(results)

        # 5. Generate certificate
        certificate = FidelityCertificate(
            id=self._generate_id(),
            contract_id=contract_id,
            contract_version=contract.version,
            epoch=epoch,
            timestamp=utc_now(),
            source_hash=source_snapshot.hash,
            target_hash=target_snapshot.hash,
            results=results,
            status=overall_status,
            previous_cert=self._get_previous(contract_id),
            signature=self._sign(...)
        )

        # 6. Store and return
        self.certificate_store.store(certificate)
        return certificate
```

### Verification Modes

| Mode | Records Checked | Use Case | Performance |
|------|----------------|----------|-------------|
| EXHAUSTIVE | All records | Regulatory proof | O(n) |
| STATISTICAL | Sample (configurable) | Continuous monitoring | O(sample_size) |
| INCREMENTAL | Changed since last cert | Frequent verification | O(delta) |

---

## Certificate Chain Design

### Certificate Structure

```yaml
certificate:
  id: string                    # Unique certificate ID
  contract_id: string
  contract_version: semver
  epoch: string

  # Timing
  timestamp: ISO8601
  verification_duration_ms: int

  # Domain binding
  source_snapshot:
    hash: sha256
    record_count: int
    epoch_boundaries: [date, date]
  target_snapshot:
    hash: sha256
    record_count: int
    epoch_boundaries: [date, date]

  # Results
  invariant_results:
    - invariant_id: string
      status: PASS | BREACH_IMMATERIAL | BREACH_MATERIAL | BREACH_CRITICAL
      expected: any
      actual: any
      difference: any
      threshold: any
      within_threshold: boolean

  overall_status: PASS | BREACH

  # Chain linkage
  previous_certificate:
    id: string
    hash: sha256
  certificate_hash: sha256

  # Cryptographic proof
  signature:
    algorithm: ECDSA-P256 | RSA-2048
    value: base64
    signer: string
```

### Chain Properties

1. **Immutability**: Certificates stored with S3 Object Lock (WORM)
2. **Tamper-evidence**: Hash chain detects modifications
3. **Gap detection**: Missing certificates in chain are visible
4. **Branching**: Multiple chains per contract (different grain levels)

---

## Enforcement Engine Design

### Pre-flight Validation

```python
class ContractEnforcementEngine:

    def can_apply_changes(
        self,
        contract_id: str,
        proposed_changes: List[DomainChange]
    ) -> EnforcementResult:

        contract = self.contract_registry.get(contract_id)

        if contract.enforcement.mode == ADVISORY:
            return EnforcementResult(allowed=True, advisory_only=True)

        # Simulate changes
        simulated_state = self.simulator.apply(
            current_state=self.domain_service.current_state(contract.domains.source),
            changes=proposed_changes
        )

        # Check invariants against simulated state
        violations = []
        for invariant_ref in contract.invariants:
            invariant = self.invariant_registry.get(invariant_ref)
            result = self.invariant_engine.evaluate(
                invariant=invariant,
                source=simulated_state,
                target=self.domain_service.current_state(contract.domains.target)
            )
            if result.status != PASS:
                violations.append(result)

        if violations:
            if contract.enforcement.mode == STRICT:
                return EnforcementResult(
                    allowed=False,
                    violations=violations,
                    remediation=self._suggest_remediation(violations)
                )
            elif contract.enforcement.mode == DEFERRED:
                return EnforcementResult(
                    allowed=True,
                    violations=violations,
                    deferral_deadline=utc_now() + contract.enforcement.deferral_window,
                    requires_remediation=True
                )

        return EnforcementResult(allowed=True)
```

### Transactional Commit

For bidirectional contracts, both domains must succeed or both fail:

```python
def commit_cross_domain(
    source_changes: List[Change],
    target_changes: List[Change],
    contract: CovarianceContract
) -> CommitResult:

    with TransactionCoordinator() as tx:
        try:
            # Phase 1: Prepare
            source_prepared = tx.prepare(source_changes)
            target_prepared = tx.prepare(target_changes)

            # Phase 2: Verify fidelity will hold
            verification = verify_fidelity_simulated(
                source=source_prepared.simulated_state,
                target=target_prepared.simulated_state,
                contract=contract
            )

            if verification.status != PASS:
                tx.rollback()
                return CommitResult(success=False, reason=verification)

            # Phase 3: Commit
            tx.commit()
            return CommitResult(success=True)

        except Exception as e:
            tx.rollback()
            raise
```

---

## Consequences

### Positive

- **Provable compliance** - Certificates provide audit evidence
- **Early detection** - Enforcement prevents violations
- **Cross-domain consistency** - Formal contracts bridge siloed domains
- **Regulatory alignment** - BCBS 239, FRTB, SOX requirements met

### Negative

- **Complexity** - Formal contracts require careful design
- **Performance overhead** - Verification adds latency
- **Operational burden** - Certificate chain must be maintained

### Neutral

- Solution-specific implementations define concrete invariants
- AWS services provide storage and compute infrastructure

---

## AWS Implementation Mapping

| Component | AWS Service | Notes |
|-----------|------------|-------|
| Contract Registry | DynamoDB + S3 | Metadata in DDB, YAML in S3 |
| Verification Service | Step Functions + Glue/EMR | Orchestration + compute |
| Certificate Store | S3 (Object Lock) + DynamoDB | Immutable storage + index |
| Enforcement Engine | Lambda + Step Functions | Sync validation |
| Scheduling | EventBridge | Periodic verification |
| Alerting | SNS + CloudWatch | Breach notifications |

---

## Requirements Addressed

- **REQ-COV-01**: Cross-domain covariance contracts ✓
- **REQ-COV-02**: Fidelity invariants with materiality thresholds ✓
- **REQ-COV-03**: Fidelity verification with cryptographic certificates ✓
- **REQ-COV-04**: Contract breach detection and classification ✓
- **REQ-COV-05**: Covariant propagation (via bidirectional contracts) ✓
- **REQ-COV-06**: Multi-grain fidelity (via grain_alignment) ✓
- **REQ-COV-07**: Contract enforcement (pre-flight + transactional) ✓
- **REQ-COV-08**: Fidelity certificate chain ✓
