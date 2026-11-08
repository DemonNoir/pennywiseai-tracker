#!/usr/bin/env bash
# Exercise the real sync script against disposable local repositories; no network.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$SCRIPT_DIR/sync-upstream.sh" <<'PY'
import os
from pathlib import Path
import subprocess
import sys
import tempfile

script = Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix="upstream-sync-fixtures-") as temp:
    root = Path(temp)

    def run(args, cwd, **kwargs):
        return subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, **kwargs)

    source = root / "source"
    source.mkdir()

    def git(*args):
        result = run(["git", *args], source)
        assert result.returncode == 0, result.stderr
        return result.stdout.strip()

    git("init", "-b", "main")
    git("config", "user.name", "Fixture")
    git("config", "user.email", "fixture@example.invalid")
    git("config", "commit.gpgsign", "false")
    (source / "file").write_text("base\n")
    git("add", ".")
    git("commit", "-m", "base")
    base = git("rev-parse", "HEAD")
    (source / "new").write_text("upstream\n")
    git("add", ".")
    git("commit", "-m", "upstream")
    git("checkout", "-b", "custom", base)
    (source / "custom").write_text("custom\n")
    git("add", ".")
    git("commit", "-m", "custom")
    clone = root / "clone"
    result = run(["git", "clone", "--branch", "custom", str(source), str(clone)], root)
    assert result.returncode == 0, result.stderr
    env = os.environ.copy()
    env.pop("GITHUB_OUTPUT", None)
    env.update(SYNC_UPSTREAM_URL=str(source), SYNC_UPSTREAM_BRANCH="main",
               SYNC_REPORT_DIR=str(root / "reports"))

    def sync():
        return run(["bash", str(script)], clone, env=env)

    result = sync()
    assert result.returncode == 0, result.stderr
    assert "changed=true" in result.stdout
    assert (clone / "custom").is_file() and (clone / "new").is_file()
    print("PASS clean merge preserves custom files")
    candidate = run(["git", "rev-parse", "HEAD"], clone).stdout.strip()
    result = sync()
    assert result.returncode == 0, result.stderr
    assert "changed=false" in result.stdout
    print("PASS already integrated is a no-op")
    run(["git", "checkout", "custom"], clone, check=True)
    result = sync()
    assert result.returncode == 0, result.stderr
    assert run(["git", "rev-parse", "HEAD"], clone).stdout.strip() == candidate
    print("PASS retry of same base/upstream produces identical candidate SHA")
    (clone / "dirty").write_text("dirty")
    result = sync()
    assert result.returncode != 0 and "dirty checkout" in result.stderr
    print("PASS dirty checkout rejected")
    (clone / "dirty").unlink()
    git("checkout", "main")
    (source / "file").write_text("upstream conflict\n")
    git("add", ".")
    git("commit", "-m", "conflict upstream")
    run(["git", "checkout", "custom"], clone, check=True)
    run(["git", "config", "user.name", "Fixture"], clone, check=True)
    run(["git", "config", "user.email", "fixture@example.invalid"], clone, check=True)
    run(["git", "config", "commit.gpgsign", "false"], clone, check=True)
    (clone / "file").write_text("custom conflict\n")
    run(["git", "add", "."], clone, check=True)
    run(["git", "commit", "-m", "conflict custom"], clone, check=True)
    before = run(["git", "rev-parse", "HEAD"], clone).stdout.strip()
    result = sync()
    assert result.returncode != 0
    assert run(["git", "rev-parse", "HEAD"], clone).stdout.strip() == before
    assert not run(["git", "status", "--porcelain"], clone).stdout
    assert (root / "reports" / "conflicts.txt").read_text().strip() == "file"
    print("PASS conflict abort restores clean base and emits a conflict report")
PY
