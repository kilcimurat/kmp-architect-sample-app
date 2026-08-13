#!/usr/bin/env bash
#
# Builds one iOS scheme and runs it on a simulator — the iOS counterpart of
# `:sample:<feature>:androidApp:installDebug`.
#
# The Xcode projects are generated artifacts (see generate-xcode-projects.sh), so an IDE run
# configuration cannot point at a scheme directly and stay reproducible. It points here instead:
# this script regenerates the project when it is missing, builds, installs and launches, then
# checks that the process is still alive — `simctl launch` succeeds even for an app that crashes
# on the first frame.
#
# Usage:
#   scripts/run-ios-simulator.sh <scheme> [simulator name or UDID]
#
# Schemes: KmpArchitectSampleApp | FeedSample | ArticleSample | BookmarksSample

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

scheme="${1:-}"
requested_device="${2:-}"

case "$scheme" in
  KmpArchitectSampleApp) spec_dir="$repo_root/iosApp";     project_name="KmpArchitectSampleApp" ;;
  FeedSample|ArticleSample|BookmarksSample)
                         spec_dir="$repo_root/iosSamples"; project_name="KmpArchitectSamples" ;;
  *)
    echo "Usage: $(basename "$0") <scheme> [simulator]" >&2
    echo "Schemes: KmpArchitectSampleApp | FeedSample | ArticleSample | BookmarksSample" >&2
    exit 2
    ;;
esac

project="$spec_dir/$project_name.xcodeproj"

if [ ! -d "$project" ]; then
  echo "==> $project_name.xcodeproj is missing, generating it"
  "$repo_root/scripts/generate-xcode-projects.sh"
fi

# Prefer a simulator that is already booted; the developer usually has the one they want open.
udid=""
if [ -n "$requested_device" ]; then
  udid="$(xcrun simctl list devices available -j \
    | python3 -c 'import json,sys;q=sys.argv[1];print(next((d["udid"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if q in (d["name"], d["udid"])), ""))' \
    "$requested_device")"
  if [ -z "$udid" ]; then
    echo "No available simulator named '$requested_device'." >&2
    exit 1
  fi
else
  udid="$(xcrun simctl list devices available -j \
    | python3 -c 'import json,sys
devices=[d for ds in json.load(sys.stdin)["devices"].values() for d in ds]
booted=[d for d in devices if d["state"]=="Booted"]
iphones=[d for d in devices if d["name"].startswith("iPhone")]
pick=(booted or iphones or devices)
print(pick[0]["udid"] if pick else "")')"
  if [ -z "$udid" ]; then
    echo "No available iOS simulator found. Create one in Xcode > Settings > Components." >&2
    exit 1
  fi
fi

device_name="$(xcrun simctl list devices -j \
  | python3 -c 'import json,sys;u=sys.argv[1];print(next((d["name"] for ds in json.load(sys.stdin)["devices"].values() for d in ds if d["udid"]==u), u))' "$udid")"

echo "==> scheme $scheme on $device_name ($udid)"

xcrun simctl boot "$udid" 2>/dev/null || true
open -a Simulator --args -CurrentDeviceUDID "$udid" || true
xcrun simctl bootstatus "$udid" -b >/dev/null

echo "==> building"
xcodebuild -project "$project" \
  -scheme "$scheme" \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$udid" \
  CODE_SIGNING_ALLOWED=NO \
  build

settings="$(xcodebuild -project "$project" -scheme "$scheme" -configuration Debug \
  -destination "platform=iOS Simulator,id=$udid" \
  CODE_SIGNING_ALLOWED=NO -showBuildSettings 2>/dev/null)"

products_dir="$(printf '%s\n' "$settings" | awk -F' = ' '/ BUILT_PRODUCTS_DIR = /{print $2; exit}')"
product_name="$(printf '%s\n' "$settings" | awk -F' = ' '/ FULL_PRODUCT_NAME = /{print $2; exit}')"
bundle_id="$(printf '%s\n' "$settings" | awk -F' = ' '/ PRODUCT_BUNDLE_IDENTIFIER = /{print $2; exit}')"
app_path="$products_dir/$product_name"

if [ ! -d "$app_path" ]; then
  echo "Built product not found at $app_path" >&2
  exit 1
fi

echo "==> installing $bundle_id"
xcrun simctl install "$udid" "$app_path"

echo "==> launching"
pid="$(xcrun simctl launch --terminate-running-process "$udid" "$bundle_id" | awk -F': ' '{print $2}')"

# `simctl launch` reports success for an app that dies during startup, so confirm survival.
sleep 3
if ! kill -0 "$pid" 2>/dev/null; then
  echo "$scheme launched (pid $pid) but is no longer running — check the simulator log:" >&2
  echo "  xcrun simctl spawn $udid log show --last 2m --predicate 'process == \"$scheme\"'" >&2
  exit 1
fi

echo "==> $scheme is running on $device_name (pid $pid)"
