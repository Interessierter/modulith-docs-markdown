#!/usr/bin/env bash

VERSION=1.0.0

export JRELEASER_GITHUB_TOKEN=$(gh auth token)
export JRELEASER_GPG_PUBLIC_KEY="$(cat public.asc)"
export JRELEASER_GPG_SECRET_KEY="$(cat private.asc)"
export JRELEASER_PROJECT_VERSION="$VERSION"
source .env

./mvnw -B versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
./mvnw -B clean deploy
jreleaser config          # validates jreleaser.yml resolves and secrets are wired up
jreleaser deploy --dry-run