# AI SDLC Method — Example Projects

Example projects demonstrating the **Asset Graph Model** methodology (v2.8).

**Method repo**: [foolishimp/ai_sdlc_method](https://github.com/foolishimp/ai_sdlc_method)

---

## Install (one command)

From any project directory:

```bash
curl -sL https://raw.githubusercontent.com/foolishimp/ai_sdlc_method/main/imp_claude/code/installers/gen-setup.py | python3 -
```

Restart Claude Code. Done.

**Verify**:
```bash
curl -sL https://raw.githubusercontent.com/foolishimp/ai_sdlc_method/main/imp_claude/code/installers/gen-setup.py | python3 - verify
```

**What gets created**:
```
your-project/
├── .claude/
│   ├── settings.json              # Plugin config + hook wiring
│   └── hooks/                     # 4 hook scripts (self-contained)
├── .ai-workspace/                 # Workspace state
│   ├── events/events.jsonl        # Event log (source of truth)
│   ├── features/                  # Feature vectors
│   ├── graph/graph_topology.yml   # Asset graph topology
│   ├── context/project_constraints.yml
│   └── tasks/                     # Task tracking
├── specification/INTENT.md        # Intent template
└── CLAUDE.md                      # Genesis Bootloader
```

---

## Two Commands

```
/gen-start     # Detects state, selects feature/edge, iterates — "Go."
/gen-status    # Project-wide state, progress, signals — "Where am I?"
```

All 13 commands use the `gen-` prefix:

| Command | Purpose |
|---------|---------|
| `/gen-start` | State-driven routing entry point |
| `/gen-status` | Show feature vector progress |
| `/gen-iterate` | Advance an asset along a specific edge |
| `/gen-spawn` | Spawn a new feature/spike/hotfix vector |
| `/gen-trace` | Show feature vector trajectory |
| `/gen-gaps` | Traceability validation |
| `/gen-checkpoint` | Save session snapshot |
| `/gen-review` | Human evaluator review point |
| `/gen-spec-review` | Gradient check at spec boundaries |
| `/gen-escalate` | Escalation queue management |
| `/gen-zoom` | Graph edge zoom |
| `/gen-release` | Create release with REQ coverage |
| `/gen-init` | Initialize workspace (called by /gen-start) |

---

## Example Projects

```
local_projects/
├── data_mapper.test01/     # Simple data mapping — first install test
├── data_mapper.test02/     # Category theory data mapper (dogfooding)
├── data_mapper.test04/     # CDME — full methodology dogfood
├── data_mapper.test05/     # Background agent test (v2.3)
├── data_mapper.test07/     # Background agent test (v3.0)
├── data_mapper.test08/     # Clean install validation
├── customer_portal/        # 7-stage example with homeostatic control
├── api_platform/           # Public API with backwards compatibility
├── admin_dashboard/        # Dashboard example
└── genisis_monitor/        # Real-time methodology event monitor
```

### genisis_monitor/

Real-time FastAPI + WebSocket monitor that consumes `events.jsonl` and displays methodology progress. 28 event types, live graph visualization.

### data_mapper.test08/

Clean install test target — verified 18/18 checks passing via `gen-setup.py verify`.

---

## Installation Options

```bash
# Full setup (plugin + workspace + hooks) — DEFAULT
curl -sL https://raw.githubusercontent.com/foolishimp/ai_sdlc_method/main/imp_claude/code/installers/gen-setup.py | python3 -

# Plugin only (no workspace)
curl -sL https://raw.githubusercontent.com/foolishimp/ai_sdlc_method/main/imp_claude/code/installers/gen-setup.py | python3 - --no-workspace

# Preview changes
curl -sL https://raw.githubusercontent.com/foolishimp/ai_sdlc_method/main/imp_claude/code/installers/gen-setup.py | python3 - --dry-run

# Install to specific directory
python gen-setup.py --target /path/to/project
```

Safe to re-run — existing files (events, features, tasks) are preserved.

---

## Manual Installation (Air-Gapped)

Add to your project's `.claude/settings.json`:

```json
{
  "extraKnownMarketplaces": {
    "aisdlc": {
      "source": {
        "source": "github",
        "repo": "foolishimp/ai_sdlc_method"
      }
    }
  },
  "enabledPlugins": {
    "gen-methodology-v2@aisdlc": true
  }
}
```

> **Note**: The marketplace delivers commands and agents but **not hooks**. For full observability (session health, artifact tracking, protocol enforcement), use the installer.

---

## Links

- [AI SDLC Method](https://github.com/foolishimp/ai_sdlc_method) — Methodology repo
- [Quick Start](https://github.com/foolishimp/ai_sdlc_method/blob/main/QUICKSTART.md) — One-page guide
- [Asset Graph Model](https://github.com/foolishimp/ai_sdlc_method/blob/main/specification/AI_SDLC_ASSET_GRAPH_MODEL.md) — Formal system
- [Foundation](https://github.com/foolishimp/constraint_emergence_ontology) — Constraint-Emergence Ontology
