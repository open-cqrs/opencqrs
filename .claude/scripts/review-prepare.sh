#!/usr/bin/env bash
# Prepares a review: checks out the state under review into a worktree at
# .review/<slug>/src and writes diff artifacts to .review/<slug>/. Prints KEY=VALUE lines
# that the calling skill passes on as workflow args.
#
#   review-prepare.sh pr <number|url>
#   review-prepare.sh branch [<base-branch>]     (default base: main; reviews HEAD, uncommitted changes excluded)
set -euo pipefail

mode="${1:?usage: review-prepare.sh pr <nr|url> | branch [<base>]}"
root="$(git rev-parse --show-toplevel)"
cd "$root"

# Find the remote whose URL points to owner/repo.
remote_for() {
  local slug="$1"
  git remote -v | awk -v s="$slug" '$2 ~ s"(\\.git)?$" && $3 == "(fetch)" { print $1; exit }'
}

case "$mode" in
  pr)
    ref="${2:?PR number or URL missing}"
    json="$(gh pr view "$ref" --json number,title,author,url,baseRefName,headRefOid,commits)"
    nr="$(jq -r .number <<<"$json")"
    title="$(jq -r .title <<<"$json")"
    author="$(jq -r .author.login <<<"$json")"
    base_branch="$(jq -r .baseRefName <<<"$json")"
    head_sha="$(jq -r .headRefOid <<<"$json")"
    commits="$(jq -r '.commits | length' <<<"$json")"
    repo_slug="$(jq -r .url <<<"$json" | sed -E 's#https://github.com/([^/]+/[^/]+)/pull/.*#\1#')"
    remote="$(remote_for "$repo_slug")"
    [ -n "$remote" ] || { echo "No remote found for $repo_slug (git remote -v)" >&2; exit 1; }
    slug="pr-$nr"
    head_ref="refs/review/$slug"
    git fetch -q "$remote" "pull/$nr/head:$head_ref" "$base_branch"
    base_ref="$remote/$base_branch"
    label="PR #$nr — $title"
    ;;
  branch)
    base_branch="${2:-main}"
    branch="$(git rev-parse --abbrev-ref HEAD)"
    head_sha="$(git rev-parse HEAD)"
    author="$(git log -1 --format=%an)"
    # Base: explicit "<remote>/<branch>" or a branch name; for forks upstream is the reference, not origin.
    if [[ "$base_branch" == */* ]] && git remote | grep -qx "${base_branch%%/*}"; then
      remote="${base_branch%%/*}"; base_branch="${base_branch#*/}"
    elif git remote | grep -qx upstream; then
      remote=upstream
    else
      remote=origin
    fi
    git fetch -q "$remote" "$base_branch" || true
    base_ref="$remote/$base_branch"
    git rev-parse -q --verify "$base_ref" >/dev/null || base_ref="$base_branch"
    slug="branch-$(tr '/' '-' <<<"$branch")"
    head_ref="refs/review/$slug"
    git update-ref "$head_ref" "$head_sha"
    commits="$(git rev-list --count "$base_ref..$head_ref")"
    title="$branch"
    label="Branch $branch"
    [ -z "$(git status --porcelain --untracked-files=no)" ] || echo "WARN=Uncommitted changes are not part of the review" >&2
    ;;
  *) echo "Unknown mode: $mode" >&2; exit 1 ;;
esac

dir="$root/.review/$slug"
src="$dir/src"
if [ -d "$src" ]; then git worktree remove --force "$src" 2>/dev/null || rm -rf "$src"; fi
git worktree prune
# Keep the results of an earlier run: move them to archive/<old head> instead of overwriting them.
if [ -f "$dir/.head" ] && { [ -f "$dir/review.md" ] || [ -n "$(ls -A "$dir/reports" 2>/dev/null)" ]; }; then
  # Same head reviewed again: never overwrite an earlier archive, number the new one instead.
  old="$dir/archive/$(cut -c1-8 "$dir/.head")"
  if [ -e "$old" ]; then n=2; while [ -e "$old-$n" ]; do n=$((n + 1)); done; old="$old-$n"; fi
  mkdir -p "$old"
  for item in review.md reports; do
    [ -e "$dir/$item" ] && mv "$dir/$item" "$old/"
  done
fi
rm -rf "$dir/by-file"
mkdir -p "$dir/by-file" "$dir/reports"
git worktree add -q --detach "$src" "$head_ref"
echo "$head_sha" > "$dir/.head"

range="$base_ref...$head_ref"
git diff "$range" > "$dir/full.diff"
git diff --name-status "$range" > "$dir/files.txt"
while IFS=$'\t' read -r status path rest; do
  f="${rest:-$path}"                       # for renames (R100) the new path counts
  case "$f" in *.png|*.jpg|*.jar|*.lock|gradle/wrapper/*) continue ;; esac
  mkdir -p "$dir/by-file/$(dirname "$f")"
  git diff "$range" -- "$f" > "$dir/by-file/$f.patch"
done < "$dir/files.txt"

behind="$(git rev-list --count "$head_ref..$base_ref")"
files="$(wc -l < "$dir/files.txt" | tr -d ' ')"
stat="$(git diff --shortstat "$range" | sed 's/^ //')"

cat <<EOF
SLUG=$slug
REVIEW_DIR=$dir
REPO=$src
DIFF_FILES=$dir/files.txt
DIFF_BY_FILE_DIR=$dir/by-file
FULL_DIFF=$dir/full.diff
REPORTS_DIR=$dir/reports
REVIEW_FILE=$dir/review.md
HEAD_REF=$head_ref
HEAD_SHA=$head_sha
BASE_REF=$base_ref
BEHIND_BASE=$behind
COMMITS=$commits
FILES=$files
SHORTSTAT=$stat
AUTHOR=$author
TITLE=$title
LABEL=$label
EOF
