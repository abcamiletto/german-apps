#!/bin/sh
# Run once on the Mac mini, after cloning into ~/german-apps. System daemons start at boot, with nobody logged in.
set -eu
cd "$(dirname "$0")"
mkdir -p "$HOME/Library/Application Support/Wortschatz"
for name in com.abcamiletto.wortschatz-sync com.abcamiletto.wortschatz-tunnel; do
    sudo launchctl bootout "system/$name" 2>/dev/null || true
    sudo install -o root -g wheel -m 644 "$name.plist" "/Library/LaunchDaemons/$name.plist"
    sudo launchctl bootstrap system "/Library/LaunchDaemons/$name.plist"
done
