from pathlib import Path
from genesis_monitor.scanner import scan_roots

def test_repro_nested_runs(tmp_path: Path):
    # Create a project root
    proj = tmp_path / "my_project"
    (proj / ".ai-workspace").mkdir(parents=True)
    
    # Create an archived run inside it
    run = proj / "runs" / "run_001"
    (run / ".ai-workspace").mkdir(parents=True)
    
    # Current behavior finds BOTH
    results = scan_roots([tmp_path])
    paths = [str(r.relative_to(tmp_path)) for r in results]
    print(f"\n  [TEST] Found paths: {paths}")
    
    # We WANT it to only find the top-level project
    assert "my_project" in paths
    # This is the assertion that will FAIL if the issue is present
    # (Or rather, we want to prove it currently finds it so we can fix it)
    assert "my_project/runs/run_001" not in paths

if __name__ == "__main__":
    import sys
    import pytest
    # Add src to path
    sys.path.append(str(Path(__file__).parent.parent / "src"))
    pytest.main([__file__])
