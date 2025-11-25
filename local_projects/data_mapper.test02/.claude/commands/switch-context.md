# Switch Project Context

Switch from the current project context to a different one. Claude will detect and highlight any requirement changes.

## Usage

```
/switch-context <new-project-name>
```

## What Happens

When you switch contexts, Claude will:
1. Load the new project's requirements
2. Compare with previous context
3. Highlight changed requirements
4. Adapt code generation to new standards

## Examples

```bash
# Switch from payment_gateway to admin_dashboard
/switch-context admin_dashboard

# Claude will report changes like:
# • Classification: restricted → internal
# • Test coverage: 95% → 85%
# • Security requirements: relaxed
```

## See Also

- `/load-context` - Load initial context
- `/current-context` - Show active context
- `/list-projects` - See all available projects
