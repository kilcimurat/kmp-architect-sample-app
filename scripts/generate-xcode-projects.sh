#!/usr/bin/env bash
#
# Generates both Xcode projects from their YAML specs.
#
# The specs are the source of truth, not the generated `.xcodeproj`. A pbxproj is a
# merge-conflict machine full of generated UUIDs; a spec is a file a reviewer can actually read,
# and three near-identical sample targets stay near-identical because they share a template.
#
# Requires XcodeGen:  brew install xcodegen

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen not found. Install it with:  brew install xcodegen" >&2
  exit 1
fi

for spec_dir in "$repo_root/iosApp" "$repo_root/iosSamples"; do
  echo "==> generating $(basename "$spec_dir")"
  (cd "$spec_dir" && xcodegen generate)
done

echo
echo "Done. Open one of:"
echo "  $repo_root/iosApp/KmpArchitectSampleApp.xcodeproj"
echo "  $repo_root/iosSamples/KmpArchitectSamples.xcodeproj   (schemes: FeedSample, ArticleSample, BookmarksSample)"
