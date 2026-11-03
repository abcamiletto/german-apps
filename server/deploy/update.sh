#!/bin/sh
# Run on the Mac mini: fetches the latest server code and restarts it. The database lives outside the checkout.
set -eu
cd "$HOME/german-apps"
git pull --ff-only
launchctl kickstart -k "gui/$(id -u)/com.abcamiletto.wortschatz-sync"
sleep 2
curl -fsS http://127.0.0.1:43130/health
