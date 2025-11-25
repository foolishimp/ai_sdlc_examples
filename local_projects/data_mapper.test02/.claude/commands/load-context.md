# Load Project Context

Load a project context to apply its specific requirements and standards. When a context is loaded, all code generation and review will follow that project's guidelines.

## Usage

```
/load-context <project-name>
```

## Available Projects

Run `/list-projects` to see all available projects.

Common projects:
- `payment_gateway` - PCI-compliant payment processing (95% test coverage, AES-256 encryption)
- `admin_dashboard` - Internal administration tools (85% test coverage, basic auth)

## What Happens

When you load a context, Claude will:
1. Load all project-specific requirements
2. Apply configuration priorities (Corporate → Methodology → Project)
3. Enforce project standards (testing, security, documentation)
4. Generate code appropriate for that project

## Examples

```bash
# Load payment gateway context
/load-context payment_gateway

# Now all code generation will be PCI-compliant with strict security
```

## See Also

- `/switch-context` - Switch to a different context
- `/current-context` - Show current context
- `/list-projects` - List all available projects
