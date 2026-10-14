#!/usr/bin/env python3
"""
Forex Factory high-impact news scraper.

Uses the FairEconomy weekly JSON feed (same data Forex Factory publishes
in its "Weekly Export" panel). Runs every 15 minutes via GitHub Actions,
writes data/news_events.json which the Android app downloads.
"""

import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

FF_URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"

def fetch_ff_json():
    """Fetch the weekly calendar JSON using curl with browser-like headers."""
    headers = [
        "-H", "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "-H", "Accept: application/json, text/plain, */*",
        "-H", "Accept-Language: en-US,en;q=0.9",
        "-H", "Referer: https://www.forexfactory.com/",
    ]
    result = subprocess.run(
        ["curl", "-s", "-L", "--compressed", "--max-time", "30"] + headers + [FF_URL],
        capture_output=True, text=True, timeout=45
    )
    if result.returncode != 0:
        print(f"curl failed: exit {result.returncode}", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        return None
    return result.stdout

def parse_events(raw):
    """Filter for High-impact events and normalize to app schema."""
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"JSON parse error: {e}", file=sys.stderr)
        print(f"First 200 chars: {raw[:200]}", file=sys.stderr)
        return []

    events = []
    for row in data:
        impact = (row.get("impact") or "").strip()
        if impact != "High":
            continue
        title = (row.get("title") or "").strip()
        country = (row.get("country") or "").strip()
        date_str = (row.get("date") or "").strip()
        if not title or not date_str:
            continue
        try:
            dt = datetime.fromisoformat(date_str)
        except ValueError:
            print(f"Skipping unparseable date: {date_str}", file=sys.stderr)
            continue
        dt_utc = dt.astimezone(timezone.utc)
        events.append({
            "title": title,
            "currency": country,
            "eventTimeUtc": dt_utc.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "impact": "High",
            "source": "ForexFactory"
        })
    events.sort(key=lambda e: e["eventTimeUtc"])
    return events

def main():
    raw = fetch_ff_json()
    if not raw:
        sys.exit(1)
    events = parse_events(raw)
    output = {
        "lastUpdated": datetime.now(timezone.utc).isoformat(),
        "events": events
    }
    out = Path("data/news_events.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(output, indent=2))
    print(f"Wrote {len(events)} high-impact events to {out}")

if __name__ == "__main__":
    main()