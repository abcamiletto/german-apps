import gzip

import pytest
from fastapi.testclient import TestClient

import sync_server
from sync_server import COOKIE, create_app, hash_password

PASSWORD = "correct horse battery"


def row(word="die_zeit", reviewed=100, reviews=1):
    return {
        "wordId": word, "stability": 3.0, "difficulty": 5.0, "dueAt": reviewed + 10,
        "lastReviewAt": reviewed, "reviews": reviews, "lapses": 0,
    }


@pytest.fixture
def app(tmp_path):
    (tmp_path / "password").write_text(hash_password(PASSWORD))
    site = tmp_path / "site"
    (site / "images").mkdir(parents=True)
    (site / "index.html").write_text("<title>the app</title>")
    (site / "images" / "die_zeit.webp").write_bytes(b"picture")
    (site / "app.wasm").write_bytes(b"code")
    (site / "icon.png").write_bytes(b"icon")
    (tmp_path / "secret.txt").write_text("outside the site")
    return create_app(str(tmp_path / "sync.db"), str(tmp_path / "password"), str(site))


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
    app = create_app(str(tmp_path / "sync.db"), str(tmp_path / "missing"), str(tmp_path))
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


def test_the_most_recent_review_wins_in_either_order(phone):
    sync(phone, [row(reviewed=200, reviews=2)])
    sync(phone, [row(reviewed=100, reviews=1)])
    assert sync(phone)["rows"] == [row(reviewed=200, reviews=2)]
    sync(phone, [row(reviewed=300, reviews=3)])
    assert sync(phone)["rows"] == [row(reviewed=300, reviews=3)]


def test_only_changes_after_the_cursor_come_back(phone):
    cursor = sync(phone, [row("die_zeit")])["cursor"]
    assert sync(phone, since=cursor)["rows"] == []
    sync(phone, [row("der_tag")])
    assert [r["wordId"] for r in sync(phone, since=cursor)["rows"]] == ["der_tag"]


def test_a_stale_row_does_not_advance_the_cursor(phone):
    cursor = sync(phone, [row(reviewed=200, reviews=2)])["cursor"]
    assert sync(phone, [row(reviewed=100)], since=cursor) == {"cursor": cursor, "rows": []}


def test_invalid_rows_are_refused(phone):
    assert phone.post("/v1/sync", json={"since": 0, "rows": [{**row(), "wordId": "../../etc"}]}).status_code == 422


def test_neither_the_password_nor_a_token_is_stored(app, tmp_path, phone):
    token = phone.headers["Authorization"].removeprefix("Bearer ")
    stored = (tmp_path / "sync.db").read_bytes() + (tmp_path / "password").read_bytes()
    assert token.encode() not in stored
    assert PASSWORD.encode() not in stored
