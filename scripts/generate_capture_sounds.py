#!/usr/bin/env python3
"""Generate the headphone-capture feedback sounds in app/src/main/res/raw.

Both sounds are synthesized from scratch, so they carry no third-party licensing.

- capture_shutter.wav: a soft camera-shutter "click-clack" played when a headphone
  capture starts. Two short mechanical clicks (shutter, then mirror return) built from
  band-passed noise plus a low body thump, rounded off with a gentle low-pass and a hint
  of room echo so it sits under podcast audio rather than cutting through it.
- capture_failed.wav: a gentle two-note descending chime played when a capture fails.

Requires numpy and scipy. Run from the repository root:

    python3 scripts/generate_capture_sounds.py
"""

from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SAMPLE_RATE = 44_100
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"


def _time(duration_s):
    return np.arange(int(SAMPLE_RATE * duration_s)) / SAMPLE_RATE


def _bandpass(signal, low_hz, high_hz):
    sos = butter(2, [low_hz, high_hz], btype="bandpass", fs=SAMPLE_RATE, output="sos")
    return sosfilt(sos, signal)


def _lowpass(signal, cutoff_hz, order=2):
    sos = butter(order, cutoff_hz, btype="lowpass", fs=SAMPLE_RATE, output="sos")
    return sosfilt(sos, signal)


def _click(rng, duration_s, band_hz, noise_decay_s, body_hz, body_decay_s, body_level):
    """One mechanical click: a short noise transient over a decaying low sine 'thump'."""
    t = _time(duration_s)
    attack = np.minimum(1.0, t / 0.0015)  # 1.5 ms ramp takes the edge off the transient
    noise = _bandpass(rng.standard_normal(len(t)), *band_hz)
    noise *= attack * np.exp(-t / noise_decay_s)
    noise /= np.abs(noise).max()
    body = np.sin(2 * np.pi * body_hz * t) * attack * np.exp(-t / body_decay_s)
    return noise + body_level * body


def _place(buffer, sound, at_s, gain):
    start = int(at_s * SAMPLE_RATE)
    end = min(len(buffer), start + len(sound))
    buffer[start:end] += gain * sound[: end - start]


def _room_echo(signal, taps):
    out = signal.copy()
    for delay_s, gain in taps:
        delay = int(delay_s * SAMPLE_RATE)
        out[delay:] += gain * signal[:-delay]
    return out


def _normalize_peak(signal, peak_dbfs):
    return signal / np.abs(signal).max() * 10 ** (peak_dbfs / 20)


def shutter():
    rng = np.random.default_rng(1)  # fixed seed: regenerating gives the identical file
    out = np.zeros(int(SAMPLE_RATE * 0.20))
    # "Click": the shutter opening — brighter and slightly louder.
    _place(out, _click(rng, 0.06, (1400, 4200), 0.007, 230, 0.015, 0.15), at_s=0.0, gain=1.0)
    # "Clack": the mirror returning ~55 ms later — duller, softer, a touch longer.
    _place(out, _click(rng, 0.08, (1000, 3400), 0.010, 170, 0.02, 0.2), at_s=0.055, gain=0.75)
    out = _lowpass(out, 4000, order=4)
    out = _room_echo(out, [(0.018, 0.22), (0.031, 0.12)])
    fade = int(SAMPLE_RATE * 0.04)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    return _normalize_peak(out, -9.0)


def failed_chime():
    def note(freq_hz, duration_s, level):
        t = _time(duration_s)
        tone = np.sin(2 * np.pi * freq_hz * t) + 0.15 * np.sin(2 * np.pi * 2 * freq_hz * t)
        envelope = np.minimum(1.0, t / 0.008) * np.exp(-t / (duration_s / 3.2))
        return level * tone * envelope

    # D5 falling to A4: reads as "didn't work" without sounding like an alarm.
    out = np.concatenate([note(587.33, 0.16, 1.0), note(440.0, 0.26, 0.9), np.zeros(int(SAMPLE_RATE * 0.03))])
    return _normalize_peak(out, -15.0)


def _write(name, signal):
    path = OUTPUT_DIR / name
    wavfile.write(path, SAMPLE_RATE, (np.clip(signal, -1.0, 1.0) * 32767).astype(np.int16))
    print(f"Wrote {path.relative_to(OUTPUT_DIR.parent.parent.parent.parent.parent)}")


if __name__ == "__main__":
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    _write("capture_shutter.wav", shutter())
    _write("capture_failed.wav", failed_chime())
