import gzip
import json

import httpx
import pytest
from fastapi.testclient import TestClient

import sync_server
from sync_server import COOKIE, ConvexStore, StoreUnavailable, create_app, hash_password

PASSWORD = "correct horse battery"


class FakeStore:
    """Convex's own behaviour is tested in database/convex/progress.test.ts."""

    def __init__(self):
        self.rows: dict[str, dict] = {}
        self.sessions: set[str] = set()
        self.lookups = 0
        self.down = False

    def check(self):
        if self.down:
            raise StoreUnavailable("down")

    def sync(self, since, rows):
        self.check()
        self.rows.update({row["wordId"]: row for row in rows})
        return {"cursor": len(self.rows), "rows": list(self.rows.values()) if since < len(self.rows) else []}

    def add_session(self, token_hash, created_at):
        self.check()
        self.sessions.add(token_hash)

    def has_session(self, token_hash):
        self.check()
        self.lookups += 1
        return token_hash in self.sessions

    def clear_sessions(self):
        self.sessions.clear()


def row(word="die_zeit", reviewed=100, reviews=1):
    return {
        "wordId": word, "stability": 3.0, "difficulty": 5.0, "dueAt": reviewed + 10,
        "lastReviewAt": reviewed, "reviews": reviews, "lapses": 0,
    }


@pytest.fixture
def store():
    return FakeStore()


@pytest.fixture
def app(tmp_path, store):
    (tmp_path / "password").write_text(hash_password(PASSWORD))
    site = tmp_path / "site"
    (site / "images").mkdir(parents=True)
    (site / "index.html").write_text("<title>the app</title>")
    (site / "images" / "die_zeit.webp").write_bytes(b"picture")
    (site / "app.wasm").write_bytes(b"code")
    (site / "icon.png").write_bytes(b"icon")
    (tmp_path / "secret.txt").write_text("outside the site")
    return create_app(store, str(tmp_path / "password"), str(site))


@pytest.fixture
def visitor(app):
    return TestClient(app, base_url="https://testserver", follow_redirects=False)


@pytest.fixture
def browser(app):
    client = TestClient(app, base_url="https://testserver", follow_redirects=False)
    assert client.post("/login", data={"password": PASSWORD}).status_code == 303
    return client


@pytest.fixture
def phone(app):
    client = TestClient(app, base_url="https://testserver")
    token = client.post("/v1/login", json={"password": PASSWORD}).json()["token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


def sync(client, rows=(), since=0):
    response = client.post("/v1/sync", json={"since": since, "rows": list(rows)})
    assert response.status_code == 200, response.text
    return response.json()


@pytest.mark.parametrize("path", ["/", "/index.html", "/images/die_zeit.webp", "/app.wasm", "/words.json", "/anything"])
def test_a_visitor_only_gets_the_sign_in_page(visitor, path):
    response = visitor.get(path)
    assert response.status_code == 401
    assert 'type="password"' in response.text
    assert "the app" not in response.text


def test_the_sign_in_page_can_show_the_icon(visitor):
    assert visitor.get("/icon.png").content == b"icon"


def test_a_visitor_cannot_sync(visitor):
    assert visitor.post("/v1/sync", json={"since": 0, "rows": []}).status_code == 401


def test_a_made_up_session_gets_nothing(visitor):
    visitor.cookies.set(COOKIE, "guess")
    assert visitor.get("/").status_code == 401
    response = visitor.post("/v1/sync", json={"since": 0, "rows": []}, headers={"Authorization": "Bearer guess"})
    assert response.status_code == 401


def test_a_wrong_password_is_refused(visitor):
    response = visitor.post("/login", data={"password": "nope"})
    assert response.status_code == 401
    assert "Wrong password" in response.text
    assert COOKIE not in response.cookies
    assert visitor.post("/v1/login", json={"password": "nope"}).status_code == 401


def test_without_a_password_set_nobody_can_sign_in(tmp_path):
    app = create_app(FakeStore(), str(tmp_path / "missing"), str(tmp_path))
    client = TestClient(app, base_url="https://testserver")
    assert client.post("/v1/login", json={"password": ""}).status_code == 401


def test_guessing_is_limited(visitor):
    for _ in range(sync_server.ATTEMPTS_PER_MINUTE_PER_CLIENT):
        assert visitor.post("/v1/login", json={"password": "nope"}).status_code == 401
    assert visitor.post("/v1/login", json={"password": PASSWORD}).status_code == 429
    assert visitor.post("/login", data={"password": PASSWORD}).status_code == 429


def test_guesses_from_many_addresses_are_limited_too(visitor, monkeypatch):
    monkeypatch.setattr(sync_server, "ATTEMPTS_PER_HOUR", 2)
    for address in ("1.1.1.1", "2.2.2.2"):
        response = visitor.post("/v1/login", json={"password": "nope"}, headers={"CF-Connecting-IP": address})
        assert response.status_code == 401
    response = visitor.post("/v1/login", json={"password": PASSWORD}, headers={"CF-Connecting-IP": "3.3.3.3"})
    assert response.status_code == 429


def test_the_cookie_is_only_for_this_site_over_https(visitor):
    header = visitor.post("/login", data={"password": PASSWORD}).headers["set-cookie"].lower()
    assert "httponly" in header and "secure" in header and "samesite=lax" in header


def test_a_signed_in_browser_gets_the_site(browser):
    assert "the app" in browser.get("/").text
    assert browser.get("/images/die_zeit.webp").content == b"picture"
    assert browser.get("/app.wasm").headers["content-type"] == "application/wasm"
    assert browser.get("/missing").status_code == 404


def test_a_compressed_copy_is_sent_when_the_browser_accepts_it(browser, tmp_path):
    (tmp_path / "site" / "app.wasm.gz").write_bytes(gzip.compress(b"code"))
    response = browser.get("/app.wasm", headers={"Accept-Encoding": "gzip"})
    assert response.headers["content-encoding"] == "gzip"
    assert response.headers["content-type"] == "application/wasm"
    assert response.content == b"code"
    plain = browser.get("/app.wasm", headers={"Accept-Encoding": "identity"})
    assert "content-encoding" not in plain.headers
    assert plain.content == b"code"


def test_nothing_private_can_be_cached_by_cloudflare(browser, visitor):
    for client, path in ((browser, "/"), (browser, "/images/die_zeit.webp"), (browser, "/app.wasm"), (visitor, "/")):
        control = client.get(path).headers["cache-control"]
        assert "private" in control or "no-store" in control


def test_files_outside_the_site_stay_private(browser):
    for path in ("/../secret.txt", "/%2e%2e/secret.txt", "/images/../../secret.txt", "/..%2fsecret.txt"):
        assert "outside the site" not in browser.get(path).text


def test_a_review_reaches_the_other_device(browser, phone):
    assert sync(phone, [row()])["cursor"] == 1
    assert sync(browser)["rows"] == [row()]


def test_invalid_rows_are_refused(phone):
    assert phone.post("/v1/sync", json={"since": 0, "rows": [{**row(), "wordId": "../../etc"}]}).status_code == 422


def test_neither_the_password_nor_a_token_is_stored(tmp_path, store, phone):
    token = phone.headers["Authorization"].removeprefix("Bearer ")
    stored = " ".join(store.sessions) + (tmp_path / "password").read_text()
    assert store.sessions
    assert token not in stored
    assert PASSWORD not in stored


def test_a_session_is_only_looked_up_once_for_all_the_images(store, browser):
    for _ in range(5):
        assert browser.get("/images/die_zeit.webp").status_code == 200
    assert store.lookups == 1


def test_a_session_is_checked_again_after_a_while(store, browser, monkeypatch):
    monkeypatch.setattr(sync_server, "SESSION_MEMORY_SECONDS", -1)
    browser.get("/")
    browser.get("/")
    assert store.lookups == 2


def test_an_outage_does_not_sign_anyone_out(app, store, phone):
    store.down = True
    # The session isn't remembered yet, because this phone has only signed in.
    assert phone.post("/v1/sync", json={"since": 0, "rows": []}).status_code == 503
    assert TestClient(app, base_url="https://testserver").post("/v1/login", json={"password": PASSWORD}).status_code == 503
    returning = TestClient(app, base_url="https://testserver")
    returning.cookies.set(COOKIE, "x")
    assert returning.get("/").status_code == 503


def test_a_remembered_session_keeps_the_site_up_during_an_outage(store, browser):
    browser.get("/")
    store.down = True
    assert browser.get("/").status_code == 200


def convex(respond):
    return ConvexStore("https://example.convex.cloud", "the secret", httpx.MockTransport(respond))


def test_convex_gets_the_secret_and_gives_back_whole_numbers():
    seen = {}

    def respond(request):
        seen.update(path=request.url.path, body=json.loads(request.content))
        stored = {**row(), "dueAt": 110.0, "lastReviewAt": 100.0, "reviews": 1.0, "lapses": 0.0}
        return httpx.Response(200, json={"status": "success", "value": {"cursor": 4.0, "rows": [stored]}})

    result = convex(respond).sync(2, [row()])
    assert seen == {
        "path": "/api/mutation",
        "body": {"path": "progress:sync", "args": {"secret": "the secret", "since": 2, "rows": [row()]}, "format": "json"},
    }
    # The app can't read "4.0" as an integer.
    assert json.dumps(result) == json.dumps({"cursor": 4, "rows": [row()]})


@pytest.mark.parametrize("respond", [
    lambda _: httpx.Response(200, json={"status": "error", "errorMessage": "Server Error"}),
    lambda _: httpx.Response(500, text="oops"),
    lambda _: httpx.Response(200, text="not json"),
])
def test_a_bad_answer_from_convex_counts_as_unavailable(respond):
    with pytest.raises(StoreUnavailable):
        convex(respond).has_session("x")


def test_no_connection_to_convex_counts_as_unavailable():
    def respond(request):
        raise httpx.ConnectError("no route", request=request)

    with pytest.raises(StoreUnavailable):
        convex(respond).has_session("x")
