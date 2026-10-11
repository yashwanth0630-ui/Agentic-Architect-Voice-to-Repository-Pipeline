import os
import shutil
import stat
import subprocess
import json
from pathlib import Path
from typing import Union

def safe_rmtree(target_path: Union[str, Path]) -> bool:
    """
    Safely removes a directory tree on Windows and POSIX systems.
    Clears read-only attributes (e.g. Git pack files, objects) so Windows doesn't fail.
    """
    p = str(target_path)
    if not os.path.exists(p):
        return True

    def _remove_readonly(func, path, exc_info):
        try:
            os.chmod(path, stat.S_IWRITE)
            func(path)
        except Exception:
            pass

    try:
        shutil.rmtree(p, onexc=_remove_readonly)
    except TypeError:
        shutil.rmtree(p, onerror=_remove_readonly)
    except Exception:
        pass

    return not os.path.exists(p)

def setup_disposable():
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent
    base_dir = repo_root / "projects" / "workspaces"
    base_dir.mkdir(parents=True, exist_ok=True)
    
    bare_dir = base_dir / "disposable-remote.git"
    ws_dir = base_dir / "disposable-user--disposable-project"
    
    if bare_dir.exists():
        safe_rmtree(bare_dir)
    if ws_dir.exists():
        safe_rmtree(ws_dir)
        
    # 1. Init bare git repo
    subprocess.run(["git", "init", "--bare", str(bare_dir)], check=True)
    
    # 2. Clone from bare
    subprocess.run(["git", "clone", str(bare_dir), str(ws_dir)], check=True)
    
    # 3. Configure local git
    subprocess.run(["git", "config", "user.name", "IdeaBeacon Test"], cwd=str(ws_dir), check=True)
    subprocess.run(["git", "config", "user.email", "test@ideabeacon.local"], cwd=str(ws_dir), check=True)
    
    # 4. Add initial files
    (ws_dir / "README.md").write_text("# Disposable Project\nInitial documentation.\n", encoding="utf-8")
    (ws_dir / "index.html").write_text("<!DOCTYPE html>\n<html>\n<head><title>Test</title></head>\n<body><h1>Hello World</h1></body>\n</html>\n", encoding="utf-8")
    
    subprocess.run(["git", "add", "."], cwd=str(ws_dir), check=True)
    subprocess.run(["git", "commit", "-m", "Initial commit"], cwd=str(ws_dir), check=True)
    subprocess.run(["git", "branch", "-M", "main"], cwd=str(ws_dir), check=True)
    subprocess.run(["git", "push", "-u", "origin", "main"], cwd=str(ws_dir), check=True)
    
    # Get baseline commit
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(ws_dir), capture_output=True, text=True, check=True)
    baseline_commit = result.stdout.strip()
    
    # Register in workspaces.json
    manifest_path = base_dir / "workspaces.json"
    manifests = {}
    if manifest_path.exists():
        try:
            manifests = json.loads(manifest_path.read_text(encoding="utf-8"))
        except Exception:
            manifests = {}
            
    manifests["disposable-user--disposable-project"] = {
        "workspace_id": "disposable-user--disposable-project",
        "owner": "disposable-user",
        "repo": "disposable-project",
        "clone_url": str(bare_dir.as_uri()),
        "default_branch": "main",
        "workspace_path": str(ws_dir),
        "baseline_commit": baseline_commit,
        "current_branch": "main",
        "created_at": "2026-10-09T08:25:00+00:00",
        "updated_at": "2026-10-09T08:25:00+00:00",
        "status": "ready",
        "preview_port": None,
        "preview_url": None,
        "last_instruction": None
    }
    
    manifest_path.write_text(json.dumps(manifests, indent=2), encoding="utf-8")
    print(f"Disposable repo created successfully! Baseline commit: {baseline_commit}")
    print(f"Bare remote: {bare_dir}")
    print(f"Workspace: {ws_dir}")

if __name__ == "__main__":
    setup_disposable()
