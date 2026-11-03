#!/bin/sh
# Changes the password and signs every device out.
set -eu
DATA="$HOME/Library/Application Support/Wortschatz"
cd "$(dirname "$0")/.."
WORTSCHATZ_DB="$DATA/sync.db" WORTSCHATZ_PASSWORD_FILE="$DATA/password" uv run --no-dev set_password.py
