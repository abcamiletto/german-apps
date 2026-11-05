"""Sets the password and signs every device out. Reads it from standard input, or asks for it.

Devices already signed in can keep going for a few more minutes, until the server stops remembering their session.

  WORTSCHATZ_PASSWORD_FILE=password uv run set_password.py
"""
import getpass
import os
import sys
from pathlib import Path

from sync_server import convex_from, hash_password


def main():
    password = getpass.getpass("New password: ") if sys.stdin.isatty() else sys.stdin.read().rstrip("\n")
    if len(password) < 8:
        raise SystemExit("Use at least 8 characters.")
    password_file = os.environ.get("WORTSCHATZ_PASSWORD_FILE", "password")
    store = convex_from(password_file)
    target = Path(password_file)
    target.touch(mode=0o600)
    target.chmod(0o600)
    target.write_text(hash_password(password))
    store.clear_sessions()


if __name__ == "__main__":
    main()
