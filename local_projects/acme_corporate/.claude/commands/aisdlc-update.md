# /aisdlc-update - Update AI SDLC Framework from GitHub

Pull the latest tagged version of ai_sdlc_method from GitHub and update local project with the latest framework files.

<!-- Implements: REQ-F-WORKSPACE-003 (Framework Updates) -->

**Usage**: `/aisdlc-update [--tag <version>] [--dry-run]`

## Command Purpose

Update this project's AI SDLC framework files from the official GitHub repository:
1. Fetch the latest tagged release (or specified tag)
2. Download framework template files
3. Update local `.ai-workspace/` and `.claude/` directories
4. Preserve project-specific customizations and active work
5. Validate the update

## Instructions

### Phase 1: Determine Target Version

```bash
# Check what version to fetch
# Default: latest tag from GitHub
# Override: use --tag argument if provided

# Get latest release info from GitHub API
curl -s https://api.github.com/repos/foolishimp/ai_sdlc_method/releases/latest | grep '"tag_name"'

# Or list recent tags
curl -s https://api.github.com/repos/foolishimp/ai_sdlc_method/tags | head -20
```

**Report current state:**
- Current framework version (if recorded in .ai-workspace/config/)
- Target version to install
- Ask user to confirm before proceeding

### Phase 2: Create Backup

Before any changes, backup current framework files:

```bash
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROJECT_NAME=$(basename $(pwd))
BACKUP_DIR="/tmp/aisdlc-backup-$PROJECT_NAME-$TIMESTAMP"

mkdir -p "$BACKUP_DIR"

# Backup framework directories
cp -r .ai-workspace "$BACKUP_DIR/" 2>/dev/null || true
cp -r .claude "$BACKUP_DIR/" 2>/dev/null || true
cp CLAUDE.md "$BACKUP_DIR/" 2>/dev/null || true

echo "Backup created: $BACKUP_DIR"
```

### Phase 3: Fetch Framework Files

Download the framework template from GitHub:

```bash
TARGET_TAG="<determined version>"
TEMP_DIR=$(mktemp -d)

# Clone specific tag (shallow clone for speed)
git clone --depth 1 --branch "$TARGET_TAG" \
    https://github.com/foolishimp/ai_sdlc_method.git \
    "$TEMP_DIR/ai_sdlc_method"

# Verify template directory exists
ls "$TEMP_DIR/ai_sdlc_method/claude-code/project-template/"
```

### Phase 4: Update Framework Files

Apply updates while preserving project-specific content:

**Update .ai-workspace/ (preserve active work):**
```bash
# Update templates
rsync -av "$TEMP_DIR/ai_sdlc_method/claude-code/project-template/.ai-workspace/templates/" \
      .ai-workspace/templates/

# Update config (merge, don't overwrite)
rsync -av "$TEMP_DIR/ai_sdlc_method/claude-code/project-template/.ai-workspace/config/" \
      .ai-workspace/config/

# Update README
cp "$TEMP_DIR/ai_sdlc_method/claude-code/project-template/.ai-workspace/README.md" \
   .ai-workspace/README.md

# DO NOT touch:
# - .ai-workspace/tasks/active/ACTIVE_TASKS.md
# - .ai-workspace/tasks/finished/*
# - .ai-workspace/session/*
```

**Update .claude/ (commands and agents):**
```bash
# Update commands
rsync -av "$TEMP_DIR/ai_sdlc_method/claude-code/project-template/.claude/commands/" \
      .claude/commands/

# Update agents
rsync -av "$TEMP_DIR/ai_sdlc_method/claude-code/project-template/.claude/agents/" \
      .claude/agents/

# Preserve settings.local.json
```

**Update CLAUDE.md (preserve project-specific sections):**

1. Read current CLAUDE.md
2. Extract content after "### Project-Specific Configuration" marker
3. Copy new template CLAUDE.md
4. Append preserved project-specific content
5. If no marker found, append old content under new "### Preserved Configuration" section

### Phase 5: Cleanup

```bash
# Remove temporary directory
rm -rf "$TEMP_DIR"
```

### Phase 6: Validation

Verify update was successful:

```bash
# Check critical files exist
[ -f "CLAUDE.md" ] && echo "CLAUDE.md exists" || echo "ERROR: CLAUDE.md missing"
[ -d ".ai-workspace/tasks" ] && echo ".ai-workspace/tasks/ exists" || echo "ERROR: tasks/ missing"
[ -d ".ai-workspace/templates" ] && echo ".ai-workspace/templates/ exists" || echo "ERROR: templates/ missing"
[ -d ".claude/commands" ] && echo ".claude/commands/ exists" || echo "ERROR: commands/ missing"
[ -f ".claude/commands/aisdlc-checkpoint-tasks.md" ] && echo "checkpoint command exists" || echo "ERROR: checkpoint missing"
```

### Phase 7: Report

Provide summary:

```
====================================================
       AI SDLC Framework Update Complete
====================================================

Version: <old version> -> <new version>
Source: https://github.com/foolishimp/ai_sdlc_method
Tag: <target tag>

Updated:
  - .ai-workspace/templates/
  - .ai-workspace/config/
  - .ai-workspace/README.md
  - .claude/commands/
  - .claude/agents/
  - CLAUDE.md (framework sections)

Preserved:
  - .ai-workspace/tasks/active/ACTIVE_TASKS.md
  - .ai-workspace/tasks/finished/*
  - Project-specific CLAUDE.md sections
  - config/config.yml (project configuration)

Backup Location: /tmp/aisdlc-backup-<project>-<timestamp>

Next Steps:
  1. Review changes: git diff
  2. Test slash commands: /aisdlc-status
  3. Commit if satisfied: git add -A && git commit -m "Update AI SDLC to <version>"
====================================================
```

## Command Options

| Option | Description | Default |
|--------|-------------|---------|
| `--tag <version>` | Specific version to install | Latest release |
| `--dry-run` | Show what would be updated without making changes | false |
| `--force` | Overwrite project-specific sections | false |
| `--no-backup` | Skip backup (not recommended) | false |

## Examples

```bash
# Update to latest release
/aisdlc-update

# Update to specific version
/aisdlc-update --tag v0.1.5

# Preview changes without applying
/aisdlc-update --dry-run

# Force update (overwrites project sections)
/aisdlc-update --force
```

## What Gets Updated

| Component | Action |
|-----------|--------|
| `.ai-workspace/templates/*` | Replaced with latest |
| `.ai-workspace/config/*` | Merged (new files added) |
| `.ai-workspace/README.md` | Replaced |
| `.claude/commands/*` | Replaced with latest |
| `.claude/agents/*` | Replaced with latest |
| `CLAUDE.md` | Framework sections updated, project sections preserved |

## What Is Preserved

| Component | Reason |
|-----------|--------|
| `.ai-workspace/tasks/active/ACTIVE_TASKS.md` | Active work |
| `.ai-workspace/tasks/finished/*` | Completed work history |
| `.ai-workspace/session/*` | Session state |
| `config/config.yml` | Project-specific AI SDLC config |
| `.claude/settings.local.json` | Local Claude settings |
| Project source code | Not framework files |
| Project-specific CLAUDE.md sections | Custom documentation |

## Rollback

If the update causes issues:

```bash
# Find your backup
ls -la /tmp/aisdlc-backup-*/

# Restore from backup
BACKUP="/tmp/aisdlc-backup-<project>-<timestamp>"
cp -r "$BACKUP/.ai-workspace" .
cp -r "$BACKUP/.claude" .
cp "$BACKUP/CLAUDE.md" .

echo "Rollback complete"
```

## Troubleshooting

**Error: Cannot fetch from GitHub**
- Check internet connection
- Verify repository URL: https://github.com/foolishimp/ai_sdlc_method
- Try: `curl -s https://api.github.com/repos/foolishimp/ai_sdlc_method/releases/latest`

**Error: Tag not found**
- List available tags: `curl -s https://api.github.com/repos/foolishimp/ai_sdlc_method/tags`
- Use exact tag name (e.g., `v0.1.4` not `0.1.4`)

**Error: Permission denied**
- Check write permissions on project directory
- Ensure .claude/ and .ai-workspace/ are writable

**Warning: Project sections not found**
- CLAUDE.md may not have "### Project-Specific Configuration" marker
- Old content will be preserved under "### Preserved Configuration"

---

**Source Repository**: https://github.com/foolishimp/ai_sdlc_method
