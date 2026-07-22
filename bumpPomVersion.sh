#!/usr/bin/env bash
set -euo pipefail

currentVersion=$(./mvnw help:evaluate \
    -Dexpression=project.version \
    -q \
    -DforceStdout 2>/dev/null)

echo "Current Version is $currentVersion"

# Split off suffix like -SNAPSHOT (if present)
versionCore="${currentVersion%%-*}"
suffix=""
if [[ "$currentVersion" == *-* ]]; then
    suffix="-${currentVersion#*-}"
fi

IFS='.' read -r major minor patch <<< "$versionCore"

echo "Select version bump:"
select choice in "Bump Major" "Bump Minor" "Bump Patch" "Free Text"; do
    case $choice in
        "Bump Major")
            newVersion="$((major + 1)).0.0${suffix}"
            break
            ;;
        "Bump Minor")
            newVersion="${major}.$((minor + 1)).0${suffix}"
            break
            ;;
        "Bump Patch")
            newVersion="${major}.${minor}.$((patch + 1))${suffix}"
            break
            ;;
        "Free Text")
            read -rp "Enter new version: " newVersion
            break
            ;;
        *)
            echo "Invalid option. Please select 1-4."
            ;;
    esac
done

echo "New version will be: $newVersion"
read -rp "Proceed? [y/N] " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

./setPomVersions.sh "$newVersion"

commitMessage="build: prepare release $newVersion"

if git diff --quiet -- '*pom.xml'; then
    echo "No pom.xml changes detected. Nothing to commit."
    exit 0
fi

echo
echo "===== Diff of all pom.xml files ====="
git diff -- '*pom.xml'
echo "======================================"
echo
echo "Commit message will be:"
echo "  $commitMessage"
echo

read -rp "Stage and commit these pom.xml changes? [y/N] " doCommit
if [[ "$doCommit" =~ ^[Yy]$ ]]; then
    git add -- '*pom.xml'
    git commit -m "$commitMessage"
    echo "Committed."
else
    echo "Skipped commit. Changes are left unstaged in the working tree."
fi
