"""Minimal Gemini client for structured text generation."""
import json
import os
import time

import httpx

BASE = "https://generativelanguage.googleapis.com/v1beta/models"
client = httpx.Client(timeout=600, headers={"x-goog-api-key": os.environ["GEMINI_API_KEY"]})


def retry_delay(response: httpx.Response) -> float:
    """Honour the delay Google states for a quota error, in seconds."""
    try:
        details = response.json()["error"]["details"]
        stated = next(d["retryDelay"] for d in details if "retryDelay" in d)
        return float(stated.rstrip("s")) + 1
    except (KeyError, StopIteration, ValueError, TypeError):
        return 30.0


def generate(model: str, body: dict) -> dict:
    """POST generateContent. Per-minute quotas clear on their own, so keep waiting them out."""
    for attempt in range(60):
        try:
            response = client.post(f"{BASE}/{model}:generateContent", json=body)
        except httpx.TransportError:
            time.sleep(min(2**attempt, 60))
            continue
        if response.status_code == 429:
            time.sleep(retry_delay(response))
            continue
        if response.status_code in (500, 502, 503, 504):
            time.sleep(min(2**attempt, 60))
            continue
        response.raise_for_status()
        return response.json()
    raise RuntimeError(f"Gemini request failed after retries for {model}")


def structured(prompt: str, schema: dict, model: str = "gemini-3.8-flash") -> dict:
    return structured_parts([{"text": prompt}], schema, model)


def structured_parts(parts: list[dict], schema: dict, model: str = "gemini-3.8-flash") -> dict:
    data = generate(
        model,
        {
            "contents": [{"parts": parts}],
            "generationConfig": {
                "responseMimeType": "application/json",
                "responseJsonSchema": schema,
            },
        },
    )
    return json.loads("".join(part.get("text", "") for part in data["candidates"][0]["content"]["parts"]))
