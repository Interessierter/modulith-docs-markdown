#!/usr/bin/env bash
set -euo pipefail

# Generally, a release is simply
# git tag v2.3.4
# git push origin v2.3.4
# This script however, does more
# 1. git pull
# 2. Compare the pom.xml version with the latest release on github
#       If, the pom.xml version is <= the GitHub release -> abort
# 3. Asks, to tag and push latest release

git pull

# 1. Current project version
currentVersion=$(./mvnw help:evaluate \
    -Dexpression=project.version \
    -q \
    -DforceStdout 2>/dev/null)

echo "Current project.version is $currentVersion"

# Strip -SNAPSHOT (or any -suffix) for comparison purposes
versionCore="${currentVersion%%-*}"

# 2. Latest release version from GitHub
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed or not in PATH." >&2
    exit 1
fi

latestReleaseTag=$(gh release view --json tagName -q .tagName 2>/dev/null) || {
    echo "Error: could not fetch the latest release from GitHub. Does the repo have any releases?" >&2
    exit 1
}

# Strip leading 'v' if present (e.g. v1.4.0 -> 1.4.0)
latestReleaseVersion="${latestReleaseTag#v}"

echo "Latest GitHub release is $latestReleaseTag (version $latestReleaseVersion)"

# 3. Compare versionCore <= latestReleaseVersion -> error
smallestOrEqual=$(printf '%s\n%s\n' "$versionCore" "$latestReleaseVersion" | sort -V | head -n1)
if [[ "$smallestOrEqual" == "$versionCore" ]]; then
    echo "Error: current project.version ($versionCore) is not newer than the latest release ($latestReleaseVersion)." >&2
    exit 1
fi

# 4. Ask user for confirmation
echo
echo "Ready to release version: $versionCore"
read -rp "Proceed with tagging and pushing v$versionCore? [y/N] " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# 5. Tag and push
git tag "v$versionCore"
git push origin "v$versionCore"

echo "Released v$versionCore."
