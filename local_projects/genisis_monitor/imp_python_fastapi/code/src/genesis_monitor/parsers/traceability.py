# Implements: REQ-F-TRACE-001
"""Scan project files for REQ tag traceability (Implements/Validates tags)."""

import os
import re
from dataclasses import dataclass, field
from pathlib import Path

# Patterns for REQ key extraction
_IMPLEMENTS_RE = re.compile(r"#\s*Implements:\s*(REQ-[\w,-]+(?:\s*,\s*REQ-[\w,-]+)*)")
_VALIDATES_RE = re.compile(r"#\s*Validates:\s*(REQ-[\w,-]+(?:\s*,\s*REQ-[\w,-]+)*)")
_REQ_KEY_RE = re.compile(r"REQ-[\w-]+")

# File extensions to scan
_CODE_EXTENSIONS = {".py", ".ts", ".js", ".java", ".scala", ".rs", ".go", ".sh"}
_TEST_PATTERNS = {"test_", "_test.", "tests/", "test/", "spec/"}

# Directories to skip
_SKIP_DIRS = {
    ".git", "__pycache__", ".venv", "venv", "node_modules",
    ".tox", ".mypy_cache", ".pytest_cache", ".ai-workspace",
    ".claude", "dist", "build", "egg-info",
}


@dataclass
class ReqTagEntry:
    """A single REQ tag found in a file."""

    req_key: str
    file_path: str  # relative to project root
    line_number: int
    tag_type: str  # "implements" or "validates"


@dataclass
class TraceabilityReport:
    """Cross-referenced REQ key traceability across code and tests."""

    code_tags: list[ReqTagEntry] = field(default_factory=list)
    test_tags: list[ReqTagEntry] = field(default_factory=list)

    # Derived: REQ key → list of files
    code_coverage: dict[str, list[str]] = field(default_factory=dict)
    test_coverage: dict[str, list[str]] = field(default_factory=dict)

    # All unique REQ keys found
    all_req_keys: set[str] = field(default_factory=set)

    # Files scanned
    code_files_scanned: int = 0
    test_files_scanned: int = 0


def _is_test_file(rel_path: str) -> bool:
    """Heuristic: is this file a test file? Uses path relative to project root."""
    parts = rel_path.lower()
    name = rel_path.rsplit("/", 1)[-1] if "/" in rel_path else rel_path
    return (
        name.startswith("test_")
        or name.endswith("_test.py")
        or parts.startswith("tests/")
        or parts.startswith("test/")
        or parts.startswith("spec/")
        or "/tests/" in parts
        or "/test/" in parts
    )


def _should_skip_dir(dirname: str) -> bool:
    """Should this directory be skipped?"""
    return dirname in _SKIP_DIRS or dirname.startswith(".")


def _extract_req_tags(file_path: Path, project_root: Path) -> list[ReqTagEntry]:
    """Extract all REQ tags from a single file."""
    entries = []
    try:
        text = file_path.read_text(encoding="utf-8", errors="ignore")
    except (OSError, UnicodeDecodeError):
        return entries

    rel_path = str(file_path.relative_to(project_root))

    for line_no, line in enumerate(text.splitlines(), 1):
        # Check Implements tags
        for match in _IMPLEMENTS_RE.finditer(line):
            for req_key in _REQ_KEY_RE.findall(match.group(1)):
                entries.append(ReqTagEntry(
                    req_key=req_key,
                    file_path=rel_path,
                    line_number=line_no,
                    tag_type="implements",
                ))
        # Check Validates tags
        for match in _VALIDATES_RE.finditer(line):
            for req_key in _REQ_KEY_RE.findall(match.group(1)):
                entries.append(ReqTagEntry(
                    req_key=req_key,
                    file_path=rel_path,
                    line_number=line_no,
                    tag_type="validates",
                ))

    return entries


def parse_traceability(project_root: Path) -> TraceabilityReport:
    """Scan project files for REQ key traceability tags.

    Walks the project directory tree looking for code files with
    `# Implements: REQ-*` tags and test files with `# Validates: REQ-*` tags.
    Returns a cross-referenced report.
    """
    report = TraceabilityReport()
    code_files = 0
    test_files = 0

    for dirpath_str, dirnames, filenames in os.walk(project_root):
        # Prune directories in-place
        dirnames[:] = [d for d in dirnames if not _should_skip_dir(d)]

        dirpath = Path(dirpath_str)
        for filename in filenames:
            file_path = dirpath / filename
            if file_path.suffix not in _CODE_EXTENSIONS:
                continue

            rel_path = str(file_path.relative_to(project_root))
            is_test = _is_test_file(rel_path)
            if is_test:
                test_files += 1
            else:
                code_files += 1

            tags = _extract_req_tags(file_path, project_root)
            for tag in tags:
                report.all_req_keys.add(tag.req_key)
                if tag.tag_type == "validates" or (tag.tag_type == "implements" and is_test):
                    report.test_tags.append(tag)
                    report.test_coverage.setdefault(tag.req_key, [])
                    if tag.file_path not in report.test_coverage[tag.req_key]:
                        report.test_coverage[tag.req_key].append(tag.file_path)
                else:
                    report.code_tags.append(tag)
                    report.code_coverage.setdefault(tag.req_key, [])
                    if tag.file_path not in report.code_coverage[tag.req_key]:
                        report.code_coverage[tag.req_key].append(tag.file_path)

    report.code_files_scanned = code_files
    report.test_files_scanned = test_files
    return report
