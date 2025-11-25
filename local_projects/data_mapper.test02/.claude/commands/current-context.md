# Show Current Context

Display information about the currently loaded project context.

## Usage

```
/current-context
```

## Output

Shows:
- Project name
- Classification level
- Testing requirements
- Security requirements
- Key configuration values

## Example Output

```
Current Context: payment_gateway

Classification: restricted
PCI Compliant: Yes
Minimum Test Coverage: 95%

Security Requirements:
  • Encryption: AES-256
  • Fraud detection: Enabled
  • Audit logging: Mandatory

Testing Requirements:
  • Unit tests: Required
  • Integration tests: Required
  • Security tests: SAST, DAST, Penetration
```

## See Also

- `/load-context` - Load a different context
- `/switch-context` - Switch contexts
