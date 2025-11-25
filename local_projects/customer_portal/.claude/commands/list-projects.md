# List Available Projects

Show all available project contexts in the repository.

## Usage

```
/list-projects
```

## Output

Lists all projects with:
- Project name
- Project type (base, methodology, custom, merged)
- Base projects inherited from
- Description

## Example Output

```
Available Projects:

• corporate_base (base)
  Company-wide defaults

• python_methodology (methodology)
  Base: corporate_base
  Python-specific standards

• payment_gateway (custom)
  Base: corporate_base, python_methodology
  PCI-compliant payment processing

• admin_dashboard (custom)
  Base: corporate_base, python_methodology
  Internal administration interface
```

## See Also

- `/load-context` - Load a specific project context
- `/switch-context` - Switch between contexts
