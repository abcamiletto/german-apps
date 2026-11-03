"""The private home of Wortschatz: the website and the progress of its one learner, behind one password.

Only /health and the icon answer without signing in. The browser signs in with a form and keeps a cookie. The Android
app exchanges the same password for a token. Each row is one word's latest schedule: the most recent review wins,
so devices can sync in any order and sync the same rows twice.

  WORTSCHATZ_DB=sync.db WORTSCHATZ_PASSWORD_FILE=password WORTSCHATZ_SITE=site \
    uv run uvicorn sync_server:from_environment --factory --port 43130
"""
import base64
import hashlib
import hmac
import html
import mimetypes
import os
import secrets
import sqlite3
import time
from collections import defaultdict, deque
from contextlib import contextmanager
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, Form, Header, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse, RedirectResponse
from pydantic import BaseModel, Field, ValidationError

COOKIE = "wortschatz_session"
COOKIE_SECONDS = 365 * 86_400
MAX_BODY_BYTES = 2_000_000
ATTEMPTS_PER_MINUTE_PER_CLIENT = 5
ATTEMPTS_PER_HOUR = 30
LONG_LIVED = {"images", "audio", "composeResources"}
ICON = "icon.png"
SCRYPT = {"n": 2**15, "r": 8, "p": 1, "maxmem": 64 * 1024 * 1024}

mimetypes.add_type("application/wasm", ".wasm")
mimetypes.add_type("image/webp", ".webp")
mimetypes.add_type("audio/ogg", ".ogg")

SCHEMA = """
CREATE TABLE IF NOT EXISTS schedule (
    word_id TEXT PRIMARY KEY,
    stability REAL NOT NULL,
    difficulty REAL NOT NULL,
    due_at INTEGER NOT NULL,
    last_review_at INTEGER NOT NULL,
    reviews INTEGER NOT NULL,
    lapses INTEGER NOT NULL,
    seq INTEGER NOT NULL
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS schedule_changes ON schedule (seq);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL
) WITHOUT ROWID;
"""

MERGE = """
INSERT INTO schedule VALUES (:wordId, :stability, :difficulty, :dueAt, :lastReviewAt, :reviews, :lapses, :seq)
ON CONFLICT (word_id) DO UPDATE SET
    stability = excluded.stability, difficulty = excluded.difficulty, due_at = excluded.due_at,
    last_review_at = excluded.last_review_at, reviews = excluded.reviews, lapses = excluded.lapses, seq = excluded.seq
WHERE (excluded.last_review_at, excluded.reviews) > (schedule.last_review_at, schedule.reviews)
"""

SIGN_IN_PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<meta name="theme-color" content="#F6F7FB">
<title>Wortschatz</title>
<link rel="icon" href="/icon.png" type="image/png">
<style>
  body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #F6F7FB; color: #14213D;
         font: 17px/1.4 ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif; }
  form { width: min(100% - 40px, 340px); display: grid; gap: 12px; }
  img { width: 96px; height: 96px; justify-self: center; }
  h1 { font-size: 28px; letter-spacing: -0.5px; margin: 0 0 12px; text-align: center; }
  input, button { font: inherit; height: 54px; border-radius: 14px; box-sizing: border-box; }
  input { padding: 0 16px; border: 1px solid #DDE1EA; background: #fff; color: inherit; }
  input:focus-visible, button:focus-visible { outline: 2px solid #2F5BEA; outline-offset: 2px; }
  button { border: 0; background: #14213D; color: #fff; font-weight: 600; cursor: pointer; }
  p { margin: 0; color: #B3213F; font-size: 15px; }
</style>
</head>
<body>
<form method="post" action="/login">
  <img src="/icon.png" width="96" height="96" alt="">
  <h1>Wortschatz</h1>
  <input name="password" type="password" placeholder="Password" aria-label="Password"
         autocomplete="current-password" autofocus required>
  {message}
  <button>Sign in</button>
</form>
</body>
</html>
"""


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, **SCRYPT)
    return f"{base64.b64encode(salt).decode()}${base64.b64encode(digest).decode()}"


def password_matches(password: str, stored: str) -> bool:
    salt, expected = (base64.b64decode(part) for part in stored.strip().split("$"))
    return hmac.compare_digest(hashlib.scrypt(password.encode(), salt=salt, **SCRYPT), expected)


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


class Row(BaseModel):
    wordId: Annotated[str, Field(pattern=r"^[a-z0-9_]{1,80}$")]
    stability: float
    difficulty: float
    dueAt: int
    lastReviewAt: int
    reviews: Annotated[int, Field(ge=0)]
    lapses: Annotated[int, Field(ge=0)]


class SyncRequest(BaseModel):
    since: Annotated[int, Field(ge=0)]
    rows: list[Row]


class SignIn(BaseModel):
    password: Annotated[str, Field(max_length=1000)]


class Attempts:
    """Password guesses are limited per client, and overall because a client's address can change."""

    def __init__(self):
        self.by_client: dict[str, deque[float]] = defaultdict(deque)
        self.everyone: deque[float] = deque()

    def allow(self, client: str) -> bool:
        now = time.monotonic()
        if len(self.by_client) > 10_000:
            self.by_client.clear()
        mine = self.by_client[client]
        while mine and now - mine[0] > 60:
            mine.popleft()
        while self.everyone and now - self.everyone[0] > 3600:
            self.everyone.popleft()
        if len(mine) >= ATTEMPTS_PER_MINUTE_PER_CLIENT or len(self.everyone) >= ATTEMPTS_PER_HOUR:
            return False
        mine.append(now)
        self.everyone.append(now)
        return True


@contextmanager
def transaction(path: str):
    connection = sqlite3.connect(path, timeout=10, isolation_level=None)
    try:
        connection.execute("BEGIN IMMEDIATE")
        yield connection
        connection.execute("COMMIT")
    except BaseException:
        if connection.in_transaction:
            connection.execute("ROLLBACK")
        raise
    finally:
        connection.close()


async def read_body(request: Request) -> bytes:
    body = bytearray()
    async for chunk in request.stream():
        body += chunk
        if len(body) > MAX_BODY_BYTES:
            raise HTTPException(413, "Too much data")
    return bytes(body)


def sign_in_page(status: int = 401, message: str = "") -> HTMLResponse:
    paragraph = f"<p>{html.escape(message)}</p>" if message else ""
    return HTMLResponse(
        SIGN_IN_PAGE.replace("{message}", paragraph),
        status_code=status,
        headers={"Cache-Control": "no-store", "X-Robots-Tag": "noindex"},
    )


def create_app(database: str, password_file: str, site: str) -> FastAPI:
    app = FastAPI(title="Wortschatz", docs_url=None, redoc_url=None, openapi_url=None)
    attempts = Attempts()
    root = Path(site).resolve()
    with sqlite3.connect(database) as db:
        db.executescript(SCHEMA)

    def client_of(request: Request) -> str:
        # Only the tunnel reaches this server, and Cloudflare always sets this header.
        return request.headers.get("cf-connecting-ip") or request.client.host

    def correct(password: str) -> bool:
        path = Path(password_file)
        return path.exists() and password_matches(password, path.read_text())

    def new_session() -> str:
        token = secrets.token_urlsafe(32)
        with transaction(database) as db:
            db.execute("INSERT INTO sessions VALUES (?, ?)", (token_hash(token), int(time.time())))
        return token

    def signed_in(request: Request, authorization: str | None = None) -> bool:
        token = (authorization or "").removeprefix("Bearer ") or request.cookies.get(COOKIE, "")
        if not token:
            return False
        with sqlite3.connect(database) as db:
            return db.execute("SELECT 1 FROM sessions WHERE token_hash = ?", (token_hash(token),)).fetchone() is not None

    @app.get("/health")
    def health():
        return {"ok": True}

    @app.get(f"/{ICON}")
    def icon():
        # The sign-in page shows it, so it has to answer before signing in.
        file = root / ICON
        if not file.is_file():
            raise HTTPException(404, "Not found")
        return FileResponse(file, headers={"Cache-Control": "public, max-age=86400"})

    @app.post("/login")
    def login(request: Request, password: Annotated[str, Form(max_length=1000)]):
        if not attempts.allow(client_of(request)):
            return sign_in_page(429, "Too many attempts. Try again in a few minutes.")
        if not correct(password):
            return sign_in_page(401, "Wrong password")
        response = RedirectResponse("/", status_code=303)
        response.set_cookie(COOKIE, new_session(), max_age=COOKIE_SECONDS, httponly=True, secure=True, samesite="lax")
        return response

    @app.post("/v1/login")
    def login_for_app(request: Request, body: SignIn):
        if not attempts.allow(client_of(request)):
            raise HTTPException(429, "Too many attempts")
        if not correct(body.password):
            raise HTTPException(401, "Wrong password")
        return {"token": new_session()}

    @app.post("/v1/sync")
    async def sync(request: Request, authorization: Annotated[str | None, Header()] = None):
        if not signed_in(request, authorization):
            raise HTTPException(401, "Sign in first")
        try:
            payload = SyncRequest.model_validate_json(await read_body(request))
        except ValidationError as error:
            raise HTTPException(422, "Invalid sync request") from error
        with transaction(database) as db:
            seq = db.execute("SELECT COALESCE(MAX(seq), 0) FROM schedule").fetchone()[0] + 1
            db.executemany(MERGE, [{**row.model_dump(), "seq": seq} for row in payload.rows])
            cursor = db.execute("SELECT COALESCE(MAX(seq), 0) FROM schedule").fetchone()[0]
            changes = db.execute(
                "SELECT word_id, stability, difficulty, due_at, last_review_at, reviews, lapses FROM schedule WHERE seq > ?",
                (payload.since,),
            ).fetchall()
        fields = ("wordId", "stability", "difficulty", "dueAt", "lastReviewAt", "reviews", "lapses")
        return {"cursor": cursor, "rows": [dict(zip(fields, change)) for change in changes]}

    @app.get("/{path:path}")
    def website(path: str, request: Request):
        if not signed_in(request):
            return sign_in_page()
        file = (root / (path or "index.html")).resolve()
        if not file.is_relative_to(root) or not file.is_file():
            raise HTTPException(404, "Not found")
        # Cloudflare caches images and scripts unless told otherwise, and it would serve them without signing in.
        lifetime = 604_800 if file.relative_to(root).parts[0] in LONG_LIVED or file.suffix == ".wasm" else 0
        return FileResponse(file, headers={"Cache-Control": f"private, max-age={lifetime}", "X-Robots-Tag": "noindex"})

    return app


def from_environment() -> FastAPI:
    return create_app(
        os.environ.get("WORTSCHATZ_DB", "sync.db"),
        os.environ.get("WORTSCHATZ_PASSWORD_FILE", "password"),
        os.environ.get("WORTSCHATZ_SITE", "site"),
    )
