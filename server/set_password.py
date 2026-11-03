"""Sets the password and signs every device out. Reads it from standard input, or asks for it.

  WORTSCHATZ_DB=sync.db WORTSCHATZ_PASSWORD_FILE=password uv run set_password.py
"""
import getpass
import os
import sqlite3
import sys
from pathlib import Path

from sync_server import SCHEMA, hash_password


def main():
    password = getpass.getpass("New password: ") if sys.stdin.isatty() else sys.stdin.read().rstrip("\n")
    if len(password) < 8:
        raise SystemExit("Use at least 8 characters.")
    target = Path(os.environ.get("WORTSCHATZ_PASSWORD_FILE", "password"))
    target.touch(mode=0o600)
    target.chmod(0o600)
    target.write_text(hash_password(password))
    with sqlite3.connect(os.environ.get("WORTSCHATZ_DB", "sync.db")) as db:
        db.executescript(SCHEMA)
        db.execute("DELETE FROM sessions")


if __name__ == "__main__":
    main()
