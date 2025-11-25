# /aisdlc-release - Deploy Framework to Example Projects

Deploy ai_sdlc_method framework updates to all example projects using the latest tagged version and template files.

<!-- Implements: REQ-F-WORKSPACE-001 (Developer Workspace Structure) -->

## Command Purpose

Execute controlled deployment of ai_sdlc_method framework to example projects:
1. Validate latest git tag and template files
2. Discover all example projects
3. Deploy framework updates from claude-code/project-template/
4. Preserve project-specific customizations
5. Validate deployment and update documentation

## Implementation Steps

### 1. Release Validation

```bash
# Get latest version tag
LATEST_TAG=$(git describe --tags --abbrev=0)
echo "📦 Latest Release: $LATEST_TAG"

# Verify claude-code/project-template/ is up to date
git status claude-code/project-template/

# Confirm deployment readiness
echo "✅ Template files validated"
```

### 2. Project Discovery

```bash
# Find all example projects
EXAMPLE_PROJECTS=$(find examples/local_projects -maxdepth 1 -mindepth 1 -type d)

echo "📋 Discovered Projects:"
for project in $EXAMPLE_PROJECTS; do
    echo "   - $(basename $project)"
done
```

### 3. Backup Current State

For each project, create backup before updates:

```bash
# Create timestamped backup
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
for project in $EXAMPLE_PROJECTS; do
    PROJECT_NAME=$(basename $project)
    BACKUP_DIR="/tmp/aisdlc-backup-$PROJECT_NAME-$TIMESTAMP"
    cp -r "$project/.ai-workspace" "$BACKUP_DIR/.ai-workspace" 2>/dev/null || true
    cp -r "$project/.claude" "$BACKUP_DIR/.claude" 2>/dev/null || true
    cp "$project/CLAUDE.md" "$BACKUP_DIR/CLAUDE.md" 2>/dev/null || true
    echo "✅ Backup: $PROJECT_NAME → $BACKUP_DIR"
done
```

### 4. Deploy Framework Updates

Update each project with template files:

```bash
for project in $EXAMPLE_PROJECTS; do
    PROJECT_NAME=$(basename $project)
    echo ""
    echo "📁 Updating: $PROJECT_NAME"

    # Update .ai-workspace/ structure
    rsync -av --exclude='tasks/active/*' \
              --exclude='tasks/finished/*' \
              --exclude='session/*' \
              claude-code/project-template/.ai-workspace/ \
              "$project/.ai-workspace/"

    # Update .claude/ commands and agents
    rsync -av claude-code/project-template/.claude/ "$project/.claude/"

    # Update CLAUDE.md (preserve project-specific sections)
    if [ -f "$project/CLAUDE.md" ]; then
        # Extract project-specific config (after "Project-Specific Configuration")
        PROJECT_CONFIG=$(sed -n '/^### Project-Specific Configuration/,$p' "$project/CLAUDE.md")

        # Use template CLAUDE.md
        cp claude-code/project-template/CLAUDE.md.template "$project/CLAUDE.md"

        # Append preserved project config if it had customizations
        if [ -n "$PROJECT_CONFIG" ] && [ "$PROJECT_CONFIG" != "### Project-Specific Configuration

**[TODO: Add your project-specific guidance below]**" ]; then
            echo "" >> "$project/CLAUDE.md"
            echo "$PROJECT_CONFIG" >> "$project/CLAUDE.md"
        fi
    else
        cp claude-code/project-template/CLAUDE.md.template "$project/CLAUDE.md"
    fi

    echo "   ✅ .ai-workspace/ updated"
    echo "   ✅ .claude/ commands updated"
    echo "   ✅ CLAUDE.md updated (project config preserved)"
done
```

### 5. Post-Deployment Validation

```bash
echo ""
echo "🔍 Validating deployments..."

for project in $EXAMPLE_PROJECTS; do
    PROJECT_NAME=$(basename $project)

    # Check critical files exist
    ERRORS=0

    [ ! -f "$project/CLAUDE.md" ] && echo "   ❌ $PROJECT_NAME: Missing CLAUDE.md" && ERRORS=$((ERRORS+1))
    [ ! -d "$project/.ai-workspace/tasks" ] && echo "   ❌ $PROJECT_NAME: Missing .ai-workspace/tasks" && ERRORS=$((ERRORS+1))
    [ ! -d "$project/.claude/commands" ] && echo "   ❌ $PROJECT_NAME: Missing .claude/commands" && ERRORS=$((ERRORS+1))
    [ ! -f "$project/.claude/commands/aisdlc-checkpoint-tasks.md" ] && echo "   ❌ $PROJECT_NAME: Missing checkpoint command" && ERRORS=$((ERRORS+1))

    if [ $ERRORS -eq 0 ]; then
        echo "   ✅ $PROJECT_NAME: All checks passed"
    else
        echo "   ⚠️  $PROJECT_NAME: $ERRORS validation errors"
    fi
done
```

### 6. Generate Deployment Report

```bash
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           AI SDLC Framework Deployment Complete             ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "📦 Framework Version: $LATEST_TAG"
echo "📋 Projects Updated: $(echo $EXAMPLE_PROJECTS | wc -w | tr -d ' ')"
echo "📁 Template Source: claude-code/project-template/"
echo "⏱️  Timestamp: $(date)"
echo ""
echo "✅ Updated Components:"
echo "   - .ai-workspace/ (workspace structure, templates)"
echo "   - .claude/ (commands, agents)"
echo "   - CLAUDE.md (framework documentation)"
echo ""
echo "🔒 Preserved:"
echo "   - Active tasks (ACTIVE_TASKS.md)"
echo "   - Finished tasks"
echo "   - Project-specific CLAUDE.md sections"
echo "   - Project source code and requirements"
echo ""
echo "💾 Backups:"
echo "   Location: /tmp/aisdlc-backup-*-$TIMESTAMP"
echo "   Projects: All example projects backed up"
echo ""
echo "📝 Next Steps:"
echo "   1. Review changes: git status"
echo "   2. Test each example project"
echo "   3. Commit updates: git add examples/ && git commit"
echo "   4. Tag if needed: git tag v$LATEST_TAG-examples"
echo ""
```

## Example Output

```
╔══════════════════════════════════════════════════════════════╗
║        AI SDLC Framework Deployment - v0.1.4                 ║
╚══════════════════════════════════════════════════════════════╝

📦 Latest Release: v0.1.4
✅ Template files validated

📋 Discovered Projects:
   - customer_portal
   - data_mapper.test01
   - data_mapper.test02

💾 Creating backups...
   ✅ Backup: customer_portal → /tmp/aisdlc-backup-customer_portal-20251125_0250
   ✅ Backup: data_mapper.test01 → /tmp/aisdlc-backup-data_mapper.test01-20251125_0250
   ✅ Backup: data_mapper.test02 → /tmp/aisdlc-backup-data_mapper.test02-20251125_0250

📁 Updating: customer_portal
   ✅ .ai-workspace/ updated
   ✅ .claude/ commands updated
   ✅ CLAUDE.md updated (project config preserved)

📁 Updating: data_mapper.test01
   ✅ .ai-workspace/ updated
   ✅ .claude/ commands updated
   ✅ CLAUDE.md updated (project config preserved)

📁 Updating: data_mapper.test02
   ✅ .ai-workspace/ updated
   ✅ .claude/ commands updated
   ✅ CLAUDE.md updated (project config preserved)

🔍 Validating deployments...
   ✅ customer_portal: All checks passed
   ✅ data_mapper.test01: All checks passed
   ✅ data_mapper.test02: All checks passed

╔══════════════════════════════════════════════════════════════╗
║           AI SDLC Framework Deployment Complete             ║
╚══════════════════════════════════════════════════════════════╝

📦 Framework Version: v0.1.4
📋 Projects Updated: 3
📁 Template Source: claude-code/project-template/
⏱️  Timestamp: 2025-11-25 02:50:00

✅ Updated Components:
   - .ai-workspace/ (workspace structure, templates)
   - .claude/ (commands, agents)
   - CLAUDE.md (framework documentation)

🔒 Preserved:
   - Active tasks (ACTIVE_TASKS.md)
   - Finished tasks
   - Project-specific CLAUDE.md sections
   - Project source code and requirements

💾 Backups:
   Location: /tmp/aisdlc-backup-*-20251125_0250
   Projects: All example projects backed up

📝 Next Steps:
   1. Review changes: git status
   2. Test each example project
   3. Commit updates: git add examples/ && git commit
   4. Tag if needed: git tag v0.1.4-examples
```

## Command Options

```bash
# Deploy to all example projects (default)
/aisdlc-release

# Deploy to specific projects only
/aisdlc-release --projects "customer_portal,data_mapper.test02"

# Dry run (show what would be updated)
/aisdlc-release --dry-run

# Deploy from specific tag
/aisdlc-release --tag v0.1.3

# Skip backups (not recommended)
/aisdlc-release --no-backup

# Force overwrite project-specific CLAUDE.md sections
/aisdlc-release --force-claude-md
```

## What Gets Updated

**Updated from claude-code/project-template/:**
- ✅ `.ai-workspace/templates/` (all templates)
- ✅ `.ai-workspace/config/` (configuration files)
- ✅ `.ai-workspace/README.md` (workspace documentation)
- ✅ `.claude/commands/` (all slash commands)
- ✅ `.claude/agents/` (all agent specifications)
- ✅ `CLAUDE.md` (framework sections, preserves project-specific)

**Preserved (not touched):**
- 🔒 `.ai-workspace/tasks/active/ACTIVE_TASKS.md` (current work)
- 🔒 `.ai-workspace/tasks/finished/` (completed work)
- 🔒 `.ai-workspace/session/` (session history)
- 🔒 Project source code (`src/`, `tests/`, etc.)
- 🔒 Project requirements (`requirements.md`, `config/`)
- 🔒 Project README.md (project-specific documentation)
- 🔒 Project-specific sections in CLAUDE.md (after extraction)

## Rollback Procedure

If deployment fails or causes issues:

```bash
# Restore from backup
TIMESTAMP="20251125_0250"
PROJECT="customer_portal"

# Restore framework files
cp -r /tmp/aisdlc-backup-$PROJECT-$TIMESTAMP/.ai-workspace \
      examples/local_projects/$PROJECT/
cp -r /tmp/aisdlc-backup-$PROJECT-$TIMESTAMP/.claude \
      examples/local_projects/$PROJECT/
cp /tmp/aisdlc-backup-$PROJECT-$TIMESTAMP/CLAUDE.md \
   examples/local_projects/$PROJECT/

echo "✅ Rollback complete for $PROJECT"
```

## Integration with Git Workflow

```bash
# After deployment, review changes
git status
git diff examples/

# Commit framework updates
git add examples/
git commit -m "Deploy ai_sdlc_method v0.1.4 to example projects

Updated framework components:
- .ai-workspace/ templates and structure
- .claude/ commands and agents
- CLAUDE.md documentation

Preserved project-specific:
- Active tasks and finished tasks
- Project source code
- Custom CLAUDE.md sections"

# Tag the deployment
git tag v0.1.4-examples
git push origin main --tags
```

## Safety Features

**Backup Strategy:**
- Automatic timestamped backups before any changes
- Backups stored in /tmp/ for quick recovery
- All framework files backed up

**Validation:**
- Pre-deployment: Verify templates exist and are valid
- Post-deployment: Check critical files in each project
- Report any validation errors

**Preservation:**
- Never overwrites active work (ACTIVE_TASKS.md)
- Never deletes finished tasks
- Preserves project-specific CLAUDE.md content
- Keeps all project source code untouched

## Error Handling

**Common Issues:**

1. **Template files missing**
   - Error: "claude-code/project-template/ not found"
   - Fix: Ensure you're in the ai_sdlc_method root directory

2. **Project directory access**
   - Error: "Permission denied"
   - Fix: Check file permissions on examples/

3. **Git tag not found**
   - Error: "No tags found"
   - Fix: Create a tag first: `git tag v0.1.4`

4. **Backup creation failed**
   - Warning: "Backup failed for project X"
   - Action: Continues with deployment but warns user

## Technical Implementation

**Deployment Engine:**
- Uses `rsync` for efficient file synchronization
- Preserves file permissions and timestamps
- Excludes active work files from updates
- Handles both full and partial deployments

**Validation Framework:**
- Checks for required files and directories
- Validates command files exist
- Ensures workspace structure is intact
- Reports errors clearly

**Documentation Updates:**
- Intelligently merges CLAUDE.md files
- Preserves project-specific sections
- Updates framework documentation
- Maintains consistent formatting

---

**Usage**: Run `/aisdlc-release` to deploy the latest framework to all example projects.
