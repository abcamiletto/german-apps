#!/bin/sh
# Runs every few minutes on the Mac mini. Installs the latest release, with the server code it was built from.
set -eu
REPOSITORY=abcamiletto/german-apps
DATA="$HOME/Library/Application Support/Wortschatz"
tag=$(gh release view --repo "$REPOSITORY" --json tagName --jq .tagName)
[ "$(cat "$DATA/deployed" 2>/dev/null || true)" = "$tag" ] && exit 0
gh release view "$tag" --repo "$REPOSITORY" --json assets --jq '.assets[].name' | grep -q '^wortschatz-website-' || exit 0

work=$(mktemp -d "$DATA/deploy.XXXXXX")
trap 'rm -rf "$work"' EXIT
gh release download "$tag" --repo "$REPOSITORY" --pattern 'wortschatz-website-*' --dir "$work"
mkdir "$work/site"
tar -xzf "$work"/wortschatz-website-*.tar.gz -C "$work/site"

cd "$HOME/german-apps"
git fetch --quiet origin "$tag"
git checkout --quiet --detach FETCH_HEAD
uv sync --quiet --no-dev --directory server

mv "$work/site" "$DATA/site-$tag"
previous=$(readlink "$DATA/site" 2>/dev/null || true)
ln -sfn "site-$tag" "$DATA/site"
[ -n "$previous" ] && [ "$previous" != "site-$tag" ] && rm -rf "${DATA:?}/$previous"
# launchd starts it again straight away.
pkill -f 'uvicorn sync_server:from_environment' || true
echo "$tag" > "$DATA/deployed"
echo "Deployed $tag"
