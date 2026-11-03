"""The sync backend: one small table of review schedules, keyed by a device-generated sync code.

There are no accounts. A sync code is 80 random bits, and whoever holds it can read and write its progress.
Only a hash of the code is stored, so the database alone can't be used to read or overwrite anyone's progress.
Each row is one word's latest schedule: the most recent review wins, so devices can sync in any order and
sync the same rows twice.

  WORTSCHATZ_DB=sync.db uv run uvicorn sync_server:app --port 8099
"""
import hashlib
import os
import re
import sqlite3
import time
from collections import defaultdict, deque
from contextlib import contextmanager
from typing import Annotated

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from pydantic import BaseModel, Field, ValidationError

SITE = "https://abcamiletto.github.io/german-apps/"
CODE = re.compile(r"[0-9A-HJKMNP-TV-Z]{16}")
MAX_BODY_BYTES = 2_000_000
MAX_ROWS_PER_CODE = 5_000
MAX_CODES = 2_000
REQUESTS_PER_MINUTE = 120

SCHEMA = """
CREATE TABLE IF NOT EXISTS progress (
    owner TEXT NOT NULL,
    word_id TEXT NOT NULL,
    stability REAL NOT NULL,
    difficulty REAL NOT NULL,
    due_at INTEGER NOT NULL,
    last_review_at INTEGER NOT NULL,
    reviews INTEGER NOT NULL,
    lapses INTEGER NOT NULL,
    seq INTEGER NOT NULL,
    PRIMARY KEY (owner, word_id)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS progress_changes ON progress (owner, seq);
"""

MERGE = """
INSERT INTO progress VALUES (:owner, :wordId, :stability, :difficulty, :dueAt, :lastReviewAt, :reviews, :lapses, :seq)
ON CONFLICT (owner, word_id) DO UPDATE SET
    stability = excluded.stability, difficulty = excluded.difficulty, due_at = excluded.due_at,
    last_review_at = excluded.last_review_at, reviews = excluded.reviews, lapses = excluded.lapses, seq = excluded.seq
WHERE (excluded.last_review_at, excluded.reviews) > (progress.last_review_at, progress.reviews)
"""

CHANGES = """
SELECT word_id, stability, difficulty, due_at, last_review_at, reviews, lapses
FROM progress WHERE owner = ? AND seq > ?
"""


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
    rows: Annotated[list[Row], Field(max_length=MAX_ROWS_PER_CODE)]


class RateLimit:
    def __init__(self):
        self.recent: dict[str, deque[float]] = defaultdict(deque)

    def check(self, client: str):
        now = time.monotonic()
        if len(self.recent) > 10_000:
            self.recent.clear()
        times = self.recent[client]
        while times and now - times[0] > 60:
            times.popleft()
        if len(times) >= REQUESTS_PER_MINUTE:
            raise HTTPException(429, "Too many requests")
        times.append(now)


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


def owner_of(authorization: str | None) -> str:
    code = (authorization or "").removeprefix("Bearer ")
    if not CODE.fullmatch(code):
        raise HTTPException(401, "A sync code is required")
    return hashlib.sha256(code.encode()).hexdigest()


def check_new_code(db: sqlite3.Connection, owner: str):
    if db.execute("SELECT 1 FROM progress WHERE owner = ? LIMIT 1", (owner,)).fetchone():
        return
    if db.execute("SELECT COUNT(DISTINCT owner) FROM progress").fetchone()[0] >= MAX_CODES:
        raise HTTPException(507, "The server is full")


async def read_body(request: Request) -> bytes:
    body = bytearray()
    async for chunk in request.stream():
        body += chunk
        if len(body) > MAX_BODY_BYTES:
            raise HTTPException(413, "Too much data")
    return bytes(body)


def create_app(path: str) -> FastAPI:
    app = FastAPI(title="Wortschatz sync", docs_url=None, redoc_url=None, openapi_url=None)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["https://abcamiletto.github.io"],
        allow_origin_regex=r"http://localhost(:\d+)?",
        allow_methods=["POST"],
        allow_headers=["Authorization", "Content-Type"],
        max_age=86_400,
    )
    limits = RateLimit()
    with sqlite3.connect(path) as db:
        db.executescript(SCHEMA)

    @app.get("/", include_in_schema=False)
    def home():
        return RedirectResponse(SITE)

    @app.get("/health")
    def health():
        return {"ok": True}

    @app.post("/v1/sync")
    async def sync(request: Request, authorization: Annotated[str | None, Header()] = None):
        limits.check(request.headers.get("cf-connecting-ip") or request.client.host)
        owner = owner_of(authorization)
        try:
            payload = SyncRequest.model_validate_json(await read_body(request))
        except ValidationError as error:
            raise HTTPException(422, "Invalid sync request") from error
        with transaction(path) as db:
            check_new_code(db, owner)
            latest = db.execute("SELECT COALESCE(MAX(seq), 0) FROM progress WHERE owner = ?", (owner,)).fetchone()[0]
            seq = latest + 1
            db.executemany(MERGE, [{**row.model_dump(), "owner": owner, "seq": seq} for row in payload.rows])
            held, cursor = db.execute("SELECT COUNT(*), COALESCE(MAX(seq), 0) FROM progress WHERE owner = ?", (owner,)).fetchone()
            if held > MAX_ROWS_PER_CODE:
                raise HTTPException(413, "Too many rows")
            changes = db.execute(CHANGES, (owner, payload.since)).fetchall()
        rows = [
            {"wordId": c[0], "stability": c[1], "difficulty": c[2], "dueAt": c[3], "lastReviewAt": c[4], "reviews": c[5], "lapses": c[6]}
            for c in changes
        ]
        return {"cursor": cursor, "rows": rows}

    return app


app = create_app(os.environ.get("WORTSCHATZ_DB", "sync.db"))
