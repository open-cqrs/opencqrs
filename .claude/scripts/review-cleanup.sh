#!/usr/bin/env bash
# Removes the worktree and review ref of a review. Reports under .review/<slug>/ are kept.
#   review-cleanup.sh <slug>
set -euo pipefail
slug="${1:?usage: review-cleanup.sh <slug>}"
root="$(git rev-parse --show-toplevel)"
cd "$root"
src="$root/.review/$slug/src"
[ -d "$src" ] && git worktree remove --force "$src"
git worktree prune
git update-ref -d "refs/review/$slug" 2>/dev/null || true
echo "CLEANED=$slug"
