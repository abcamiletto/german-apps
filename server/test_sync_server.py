import pytest
from fastapi.testclient import TestClient

import sync_server
from sync_server import create_app

CODE = "7KQ2M9XD3TFA8WPN"
OTHER = "0123456789ABCDEF"


def row(word="die_zeit", reviewed=100, reviews=1, **changes):
    return {
        "wordId": word, "stability": 3.0, "difficulty": 5.0, "dueAt": reviewed + 10,
        "lastReviewAt": reviewed, "reviews": reviews, "lapses": 0, **changes,
    }


@pytest.fixture
def client(tmp_path):
    return TestClient(create_app(str(tmp_path / "sync.db")))


def sync(client, rows=(), since=0, code=CODE):
    response = client.post("/v1/sync", json={"since": since, "rows": list(rows)}, headers={"Authorization": f"Bearer {code}"})
    assert response.status_code == 200, response.text
    return response.json()


def test_a_second_device_receives_what_the_first_one_sent(client):
    first = sync(client, [row()])
    assert first["cursor"] == 1
    assert sync(client)["rows"] == [row()]


def test_the_most_recent_review_wins_in_either_order(client):
    sync(client, [row(reviewed=200, reviews=2)])
    sync(client, [row(reviewed=100, reviews=1)])
    assert sync(client)["rows"] == [row(reviewed=200, reviews=2)]
    sync(client, [row(reviewed=300, reviews=3)])
    assert sync(client)["rows"] == [row(reviewed=300, reviews=3)]


def test_only_changes_after_the_cursor_come_back(client):
    cursor = sync(client, [row("die_zeit")])["cursor"]
    assert sync(client, since=cursor)["rows"] == []
    sync(client, [row("der_tag")])
    assert [r["wordId"] for r in sync(client, since=cursor)["rows"]] == ["der_tag"]


def test_a_stale_row_does_not_advance_the_cursor_of_other_devices(client):
    cursor = sync(client, [row(reviewed=200, reviews=2)])["cursor"]
    assert sync(client, [row(reviewed=100)], since=cursor) == {"cursor": cursor, "rows": []}


def test_codes_do_not_see_each_other(client):
    sync(client, [row()])
    assert sync(client, code=OTHER)["rows"] == []


@pytest.mark.parametrize("header", [{}, {"Authorization": "Bearer short"}, {"Authorization": "Bearer 7KQ2M9XD3TFA8WPI"}])
def test_a_missing_or_malformed_code_is_refused(client, header):
    assert client.post("/v1/sync", json={"since": 0, "rows": []}, headers=header).status_code == 401


def test_invalid_rows_are_refused(client):
    response = client.post(
        "/v1/sync", json={"since": 0, "rows": [row(word="../../etc")]}, headers={"Authorization": f"Bearer {CODE}"},
    )
    assert response.status_code == 422


def test_a_new_code_is_refused_when_the_server_is_full(client, monkeypatch):
    monkeypatch.setattr(sync_server, "MAX_CODES", 1)
    sync(client, [row()])
    response = client.post("/v1/sync", json={"since": 0, "rows": [row()]}, headers={"Authorization": f"Bearer {OTHER}"})
    assert response.status_code == 507


def test_a_code_cannot_hold_more_rows_than_the_limit(client, monkeypatch):
    monkeypatch.setattr(sync_server, "MAX_ROWS_PER_CODE", 1)
    sync(client, [row("die_zeit")])
    sync(client, [row("die_zeit", reviewed=200)])
    response = client.post("/v1/sync", json={"since": 0, "rows": [row("der_tag")]}, headers={"Authorization": f"Bearer {CODE}"})
    assert response.status_code == 413
    assert len(sync(client)["rows"]) == 1


def test_requests_are_rate_limited_per_client(client, monkeypatch):
    monkeypatch.setattr(sync_server, "REQUESTS_PER_MINUTE", 2)
    sync(client)
    sync(client)
    response = client.post("/v1/sync", json={"since": 0, "rows": []}, headers={"Authorization": f"Bearer {CODE}"})
    assert response.status_code == 429


def test_only_the_website_may_call_it_from_a_browser(client):
    preflight = {"Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "authorization,content-type"}
    allowed = client.options("/v1/sync", headers={"Origin": "https://abcamiletto.github.io", **preflight})
    assert allowed.headers["access-control-allow-origin"] == "https://abcamiletto.github.io"
    refused = client.options("/v1/sync", headers={"Origin": "https://example.com", **preflight})
    assert "access-control-allow-origin" not in refused.headers


def test_the_code_itself_is_never_stored(client, tmp_path):
    sync(client, [row()])
    assert CODE.encode() not in (tmp_path / "sync.db").read_bytes()
