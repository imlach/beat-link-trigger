#!/usr/bin/env python3
"""Validate fresh deck updates, rather than treating cached assets as a live connection."""
import argparse
import json
from datetime import datetime
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('log', type=Path)
parser.add_argument('--seconds', type=float, default=60)
parser.add_argument('--players', type=int, nargs='+', default=[2, 3])
parser.add_argument('--require-track-change', action='store_true')
args = parser.parse_args()
rows = [json.loads(line) for line in args.log.read_text().splitlines() if line.strip()]
failures = []
results = {}
for player in args.players:
    samples = [r for r in rows if r.get('player') == player]
    if not samples:
        failures.append(f'No samples for player {player}')
        continue
    end = datetime.fromisoformat(samples[-1]['time'])
    samples = [r for r in samples if (end - datetime.fromisoformat(r['time'])).total_seconds() <= args.seconds + 6]
    times = [datetime.fromisoformat(r['time']) for r in samples]
    span = (times[-1] - times[0]).total_seconds()
    fresh = all(r.get('statusFresh') is True and 0 <= r.get('statusAgeMillis', 999999) < 2000 for r in samples)
    gaps_ok = all((b - a).total_seconds() <= 10 for a, b in zip(times, times[1:]))
    tracks = {(r.get('sourcePlayer'), r.get('rawSourceSlot'), r.get('rawTrackType'), r.get('trackId')) for r in samples if r.get('trackId')}
    results[str(player)] = {'span_seconds': span, 'samples': len(samples), 'all_status_fresh': fresh,
                            'sampling_continuous': gaps_ok, 'distinct_track_references': len(tracks),
                            'latest_title': samples[-1].get('title'), 'latest_sample': samples[-1]['time']}
    if span < args.seconds or not fresh or not gaps_ok:
        failures.append(f'Player {player}: insufficient continuous fresh updates')
    if args.require_track_change and len(tracks) < 2:
        failures.append(f'Player {player}: no track transition demonstrated')
print(json.dumps({'players': results, 'failures': failures,
                  'scope': 'Recorded interval only; does not assert current connectivity or metadata correctness.'}, indent=2))
raise SystemExit(bool(failures))
