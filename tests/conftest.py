"""Pytest configuration for IQOO Agent Architecture."""
import os
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SCRIPTS_DIR = os.path.join(REPO_ROOT, "scripts")

for p in (REPO_ROOT, SCRIPTS_DIR):
    if p not in sys.path:
        sys.path.insert(0, p)
