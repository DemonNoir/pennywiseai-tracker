#!/usr/bin/env bash
# Prepare a candidate only. Publishing and CI are controlled by the trusted workflow.
set -euo pipefail

UPSTREAM_URL="${SYNC_UPSTREAM_URL:-https://github.com/sarim2000/pennywiseai-tracker.git}"
UPSTREAM_BRANCH="${SYNC_UPSTREAM_BRANCH:-main}"
CANDIDATE_BRANCH=auto/upstream-sync
REPORT_DIR="${SYNC_REPORT_DIR:-$(mktemp -d)}"
mkdir -p "$REPORT_DIR"
REPORT_DIR="$(cd "$REPORT_DIR" && pwd)"

# Require a disposable clean checkout; never modify an active developer checkout.
if [[ -n "$(git status --porcelain)" ]]; then
  echo 'Refusing to sync a dirty checkout. Use a clean, disposable clone.' >&2
  exit 1
fi
git check-ref-format --branch "$UPSTREAM_BRANCH" >/dev/null
BASE_SHA="$(git rev-parse HEAD)"
git -c core.hooksPath=/dev/null fetch --no-tags "$UPSTREAM_URL" "$UPSTREAM_BRANCH"
UPSTREAM_SHA="$(git rev-parse FETCH_HEAD)"

output() {
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
  fi
  printf '%s=%s\n' "$1" "$2"
}
output base_sha "$BASE_SHA"
output upstream_sha "$UPSTREAM_SHA"

{
  printf '# Upstream sync candidate\n\n'
  printf '* Custom base: `%s`\n* Upstream: `%s`\n\n' "$BASE_SHA" "$UPSTREAM_SHA"
} > "$REPORT_DIR/report.md"
if git merge-base --is-ancestor "$UPSTREAM_SHA" "$BASE_SHA"; then
  echo 'The custom branch already contains this upstream commit.' >> "$REPORT_DIR/report.md"
  output changed false
  exit 0
fi

git -c core.hooksPath=/dev/null checkout -B "$CANDIDATE_BRANCH" "$BASE_SHA"
MERGE_DATE="$(git show -s --format=%cI "$UPSTREAM_SHA")"
if ! GIT_AUTHOR_DATE="$MERGE_DATE" GIT_COMMITTER_DATE="$MERGE_DATE" \
  git -c core.hooksPath=/dev/null -c commit.gpgSign=false \
  -c user.name='github-actions[bot]' \
  -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
  merge --no-ff --no-edit "$UPSTREAM_SHA" -m "Merge upstream main into custom candidate"; then
  git diff --name-only --diff-filter=U > "$REPORT_DIR/conflicts.txt"
  {
    printf '## Merge failed\n\nNo candidate was published. Resolve these conflicts while preserving Custom behavior:\n\n```text\n'
    cat "$REPORT_DIR/conflicts.txt"
    printf '```\n'
  } >> "$REPORT_DIR/report.md"
  git -c core.hooksPath=/dev/null merge --abort || true
  exit 1
fi
CANDIDATE_SHA="$(git rev-parse HEAD)"
{
  printf 'Candidate: `%s`\n\n' "$CANDIDATE_SHA"
  printf '## Changed files\n\n```text\n'
  git diff --name-only "$BASE_SHA" "$CANDIDATE_SHA"
  printf '```\n\n'
  printf '## Verification\n\n'
  printf 'The Upstream Sync run invokes the Tests workflow against the exact candidate SHA.\n'
  printf 'Download APKs and test reports from that run. The PR remains a draft until reviewed.\n\n'
  printf 'Before release, check slip scanning, permissions, merchant learning/reset and backup restore on a device.\n'
  printf 'Database migrations additionally require the API 26 upgrade check.\n'
} >> "$REPORT_DIR/report.md"
output candidate_sha "$CANDIDATE_SHA"
output changed true
