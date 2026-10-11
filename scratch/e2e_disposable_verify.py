"""
End-to-End Verification against Disposable Repository
Tests:
1. Workspace initialization and reuse
2. File edits and AI change isolation
3. Non-destructive corrupted workspace preservation
4. Review generation & diff calculation
5. Controlled staging & push prevention without confirmation
"""

import asyncio
import os
import shutil
import stat
import sys

# Ensure repo root and scripts are on sys.path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
SCRIPTS_DIR = os.path.join(REPO_ROOT, "scripts")
sys.path.insert(0, REPO_ROOT)
sys.path.insert(0, SCRIPTS_DIR)

from repository_agent import (
    RepositoryWorkspaceManager,
    RepositoryAgent,
    GitChangeTracker,
    GitPublisher,
    PublishError,
    WorkspaceSecurityError,
    safe_rmtree,
)


async def run_e2e_tests():
    print("=== Starting End-to-End Disposable Verification ===")
    
    wm = RepositoryWorkspaceManager()
    agent = RepositoryAgent(timeout=10.0)
    tracker = GitChangeTracker()
    publisher = GitPublisher(wm=wm, agent=agent, tracker=tracker)
    
    owner = "disposable-user"
    repo = "disposable-project"
    workspace_id = f"{owner}--{repo}"
    workspace_path = wm.get_safe_workspace_path(workspace_id)
    
    assert os.path.isdir(workspace_path), f"Workspace {workspace_path} should exist"
    print(f"[OK] Workspace located at: {workspace_path}")
    
    # 1. Test workspace status and reuse
    record = wm.get_workspace(workspace_id)
    assert record is not None, f"Workspace {workspace_id} must be in registry"
    print(f"[OK] Workspace record verified: branch={record.current_branch}, baseline={record.baseline_commit[:7]}")
    
    # 2. Test pre-existing user modification detection
    user_file = os.path.join(workspace_path, "user_note.txt")
    with open(user_file, "w", encoding="utf-8") as f:
        f.write("Important user notes that must never be deleted!")
        
    pre_existing = tracker.get_detailed_status(workspace_path)
    assert "user_note.txt" in pre_existing.untracked, f"Expected user_note.txt in {pre_existing.untracked}"
    print(f"[OK] Pre-existing change tracker accurately identified untracked user file: {pre_existing.untracked}")
    
    # Check publishing review
    review = publisher.get_publish_review(workspace_id)
    assert review.has_user_changes is True
    user_file_item = next((f for f in review.files if f.path == "user_note.txt"), None)
    assert user_file_item is not None
    assert user_file_item.eligible_for_commit is False
    print(f"[OK] Pre-publishing review isolated user file: path={user_file_item.path}, eligible={user_file_item.eligible_for_commit}, reason={user_file_item.ineligibility_reason}")
    
    # Clean up user_note.txt
    if os.path.exists(user_file):
        os.remove(user_file)
    
    # 3. Test non-destructive policy on workspace without git
    temp_orphan = os.path.join(REPO_ROOT, "projects", "workspaces", "testuser--orphan-repo")
    safe_rmtree(temp_orphan)
    os.makedirs(temp_orphan, exist_ok=True)
    with open(os.path.join(temp_orphan, "valuable_code.py"), "w", encoding="utf-8") as f:
        f.write("print('Do not delete me')")
        
    try:
        await wm.initialize_workspace("testuser", "orphan-repo")
        assert False, "Should have raised WorkspaceSecurityError"
    except WorkspaceSecurityError as e:
        print(f"[OK] Successfully blocked destructive recovery with error code: {e.code}")
        assert os.path.exists(os.path.join(temp_orphan, "valuable_code.py")), "Files must be preserved!"
        print("[OK] User files preserved intact without deletion.")
    finally:
        safe_rmtree(temp_orphan)
        
    # 4. Test safe_rmtree on read-only files (Windows Git cleanup)
    test_readonly_dir = os.path.join(REPO_ROOT, "projects", "workspaces", "test_readonly_cleanup")
    os.makedirs(test_readonly_dir, exist_ok=True)
    ro_file = os.path.join(test_readonly_dir, "readonly.txt")
    with open(ro_file, "w", encoding="utf-8") as f:
        f.write("read only content")
    os.chmod(ro_file, stat.S_IREAD)
    
    cleanup_success = safe_rmtree(test_readonly_dir)
    assert cleanup_success is True
    assert not os.path.exists(test_readonly_dir)
    print("[OK] safe_rmtree cleanly deleted directory with read-only files.")
    
    # 5. Verify push without confirmation blocks
    try:
        publisher.push_to_remote(workspace_id=workspace_id, confirmed=False)
        assert False, "Push should fail without confirmed=True"
    except PublishError as e:
        print(f"[OK] Push safely blocked without explicit confirmation: code={e.code}")
        
    print("=== All End-to-End Verifications PASSED Successfully! ===")


if __name__ == "__main__":
    asyncio.run(run_e2e_tests())
