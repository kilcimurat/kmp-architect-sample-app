#!/usr/bin/env bash
#
# Build-isolation benchmarks.
#
# The architecture's central claim is that working on one feature does not mean building the whole
# app. This script measures that claim instead of asserting it, and it measures the cases that make
# it look bad as well as the ones that make it look good.
#
# Two metrics per scenario:
#
#   ms     wall clock. Honest, but specific to this machine -- a 128-core box hides serialisation
#          and flatters parallel module builds compared with a laptop.
#   tasks  Gradle tasks actually executed (excluding up-to-date and from-cache). Portable: it is a
#          property of the dependency graph, not of the hardware, so a reader can compare it with
#          their own project.
#
# Two families, labelled separately because they answer different questions:
#
#   cold.*    clean + --no-build-cache + --no-configuration-cache -- how much work the graph really
#             requires. Without this, "clean" measures build-cache restore throughput.
#   warm.*    daemon up, both caches on -- what a developer actually feels in the inner loop.
#
# Usage:  ./scripts/benchmark.sh [output-file]

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

out="${1:-build/benchmark-results.tsv}"
mkdir -p "$(dirname "$out")"
: > "$out"
printf 'scenario\tmedian_ms\tmedian_tasks\truns_ms\truns_tasks\n' >> "$out"

APP=":app:android:assembleDebug"
FEED_SAMPLE=":sample:feed:androidApp:assembleDebug"
BOOKMARKS_SAMPLE=":sample:bookmarks:androidApp:assembleDebug"

COLD_REPS=3
WARM_REPS=5

log() { printf '%s\n' "$*" >&2; }

now_ms() {
  python3 -c 'import time; print(time.time_ns() // 1_000_000)'
}

# Runs Gradle once, echoing "<milliseconds> <tasks-executed>".
run_once() {
  local log_file start end status executed
  log_file=$(mktemp)
  start=$(now_ms)
  ./gradlew "$@" --console=plain > "$log_file" 2>&1
  status=$?
  end=$(now_ms)

  # "82 actionable tasks: 24 executed, 58 up-to-date"
  executed=$(grep -oE '[0-9]+ executed' "$log_file" | tail -1 | grep -oE '^[0-9]+')
  rm -f "$log_file"

  if [ $status -ne 0 ]; then
    log "    !! FAILED: gradlew $*"
    echo "-1 -1"
    return
  fi
  echo "$(( end - start )) ${executed:-0}"
}

median_of() {
  python3 -c "
import statistics, sys
vals = [int(v) for v in sys.argv[1:] if int(v) >= 0]
print(int(statistics.median(vals)) if vals else -1)
" "$@"
}

# A content change that does not alter the module's ABI. The source is restored from a private
# backup rather than through Git, so the benchmark is safe in exported/non-Git workspaces too.
active_file=""
active_backup=""

restore_source() {
  if [ -n "$active_file" ] && [ -f "$active_backup" ]; then
    cp -p "$active_backup" "$active_file"
    rm -f "$active_backup"
  fi
  active_file=""
  active_backup=""
}

touch_source() {
  active_file="$1"
  active_backup=$(mktemp)
  cp -p "$active_file" "$active_backup"
  printf '\n// benchmark touch %s\n' "$(python3 -c 'import time; print(time.time_ns())')" >> "$active_file"
}

trap restore_source EXIT INT TERM

scenario() {
  local name="$1" reps="$2" kind="$3" target="$4" file="${5:-}"
  local -a ms=() tasks=()
  log "==> $name"

  for _ in $(seq 1 "$reps"); do
    local result
    case "$kind" in
      cold)
        ./gradlew clean --console=plain > /dev/null 2>&1
        result=$(run_once "$target" --no-build-cache --no-configuration-cache)
        ;;
      warm)
        result=$(run_once "$target")
        ;;
      edit)
        touch_source "$file"
        result=$(run_once "$target")
        restore_source
        # Re-settle so every repetition starts from the same state.
        ./gradlew "$target" --console=plain > /dev/null 2>&1
        ;;
      config)
        rm -rf .gradle/configuration-cache
        result=$(run_once help)
        ;;
      config-isolated)
        rm -rf .gradle/configuration-cache
        result=$(run_once help -Dorg.gradle.unsafe.isolated-projects=true)
        ;;
    esac
    ms+=("${result% *}")
    tasks+=("${result#* }")
  done

  local m t
  m=$(median_of "${ms[@]}")
  t=$(median_of "${tasks[@]}")
  log "    median ${m}ms, ${t} tasks   (ms: ${ms[*]})"
  printf '%s\t%s\t%s\t%s\t%s\n' "$name" "$m" "$t" "${ms[*]}" "${tasks[*]}" >> "$out"
}

FEED_PRESENTATION_FILE="presentation/feed/src/commonMain/kotlin/com/mkilci/kmparchitect/presentation/feed/viewmodel/FeedViewModel.kt"
FEED_DATA_FILE="data/feed/src/commonMain/kotlin/com/mkilci/kmparchitect/data/feed/DefaultFeedRepository.kt"
BOOKMARKS_PRESENTATION_FILE="presentation/bookmarks/src/commonMain/kotlin/com/mkilci/kmparchitect/presentation/bookmarks/viewmodel/BookmarksViewModel.kt"
BOOKMARKS_DATA_FILE="data/bookmarks/src/commonMain/kotlin/com/mkilci/kmparchitect/data/bookmarks/DefaultBookmarkRepository.kt"
DESIGN_FILE="core/designsystem/src/commonMain/kotlin/com/mkilci/kmparchitect/core/designsystem/AppTheme.kt"

if command -v sysctl >/dev/null 2>&1; then
  cores=$(sysctl -n hw.logicalcpu 2>/dev/null || printf '?')
  memory_bytes=$(sysctl -n hw.memsize 2>/dev/null || printf '0')
  memory_gb=$((memory_bytes / 1024 / 1024 / 1024))
else
  cores=$(nproc 2>/dev/null || printf '?')
  memory_gb=$(free -g 2>/dev/null | awk '/^Mem:/{print $2}')
fi
log "os=$(uname -s) cores=$cores memory=${memory_gb:-?}GB projects=$(grep -c '^include(":' settings.gradle.kts)"
log "warming up"
./gradlew "$APP" "$FEED_SAMPLE" "$BOOKMARKS_SAMPLE" --console=plain > /dev/null 2>&1

scenario "cold.app"                 "$COLD_REPS" cold   "$APP"
scenario "cold.feed-sample"         "$COLD_REPS" cold   "$FEED_SAMPLE"
scenario "cold.bookmarks-sample"    "$COLD_REPS" cold   "$BOOKMARKS_SAMPLE"
scenario "warm.nochange.app"        "$WARM_REPS" warm   "$APP"
scenario "warm.nochange.feed-sample" "$WARM_REPS" warm  "$FEED_SAMPLE"
scenario "warm.nochange.bookmarks-sample" "$WARM_REPS" warm "$BOOKMARKS_SAMPLE"
scenario "edit-feed-presentation.app" "$WARM_REPS" edit "$APP" "$FEED_PRESENTATION_FILE"
scenario "edit-feed-presentation.sample" "$WARM_REPS" edit "$FEED_SAMPLE" "$FEED_PRESENTATION_FILE"
scenario "edit-bookmarks-presentation.app" "$WARM_REPS" edit "$APP" "$BOOKMARKS_PRESENTATION_FILE"
scenario "edit-bookmarks-presentation.sample" "$WARM_REPS" edit "$BOOKMARKS_SAMPLE" "$BOOKMARKS_PRESENTATION_FILE"
scenario "edit-feed-data.app"       "$WARM_REPS" edit   "$APP" "$FEED_DATA_FILE"
scenario "edit-feed-data.sample"    "$WARM_REPS" edit   "$FEED_SAMPLE" "$FEED_DATA_FILE"
scenario "edit-bookmarks-data.app"  "$WARM_REPS" edit   "$APP" "$BOOKMARKS_DATA_FILE"
scenario "edit-bookmarks-data.sample" "$WARM_REPS" edit "$BOOKMARKS_SAMPLE" "$BOOKMARKS_DATA_FILE"
scenario "edit-designsystem.app"    "$WARM_REPS" edit   "$APP"    "$DESIGN_FILE"
scenario "edit-designsystem.feed-sample" "$WARM_REPS" edit "$FEED_SAMPLE" "$DESIGN_FILE"
scenario "edit-designsystem.bookmarks-sample" "$WARM_REPS" edit "$BOOKMARKS_SAMPLE" "$DESIGN_FILE"
scenario "config.cold"              "$WARM_REPS" config help
scenario "config.cold.isolated"     "$WARM_REPS" config-isolated help

log ""
if command -v column >/dev/null 2>&1; then
  column -t -s$'\t' "$out" >&2
else
  sed 's/\t/  /g' "$out" >&2
fi
