#!/usr/bin/env python3
"""Original Phoenix UI sounds (RP6), synthesised from sine partials; no samples.

Writes resourcepack/java/assets/phoenix/sounds/<id>.ogg (mono 44.1 kHz Vorbis, bit-exact
so rebuilds keep the same bytes). Keep them short and soft, close to the vanilla UI feel.
Run: python3 resourcepack/src/sounds/sounds.py
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
OUT = os.path.join(ROOT, 'resourcepack', 'java', 'assets', 'phoenix', 'sounds')


def tone(freq, dur, start=0.0, amp=1.0, attack=0.004, decay=8.0, partials=((1, 1.0), (2, 0.25), (3, 0.08))):
    t = np.arange(int(SR * dur)) / SR
    env = np.minimum(t / attack, 1.0) * np.exp(-decay * t)
    sig = sum(a * np.sin(2 * np.pi * freq * k * t) for k, a in partials)
    return start, amp * env * sig


def mix(length, *parts):
    buf = np.zeros(int(SR * length))
    for start, sig in parts:
        i = int(SR * start); n = min(len(sig), len(buf) - i)
        buf[i:i + n] += sig[:n]
    peak = np.max(np.abs(buf)) or 1.0
    fade = np.ones_like(buf); f = int(SR * 0.01); fade[-f:] = np.linspace(1, 0, f)
    return buf / peak * fade


SOUNDS = {
    # soft wooden tick: a short high partial over a quick low knock
    'ui/click': (0.30, mix(0.06, tone(1850, 0.05, decay=90, partials=((1, 1.0), (2.7, 0.2))),
                          tone(420, 0.05, amp=0.6, decay=70))),
    # two warm rising notes (G5 → D6)
    'ui/success': (0.35, mix(0.32, tone(784, 0.25, decay=14), tone(1175, 0.24, start=0.07, decay=12))),
    # two low falling notes (E4 → C4), muted
    'ui/error': (0.35, mix(0.28, tone(330, 0.2, decay=16, partials=((1, 1.0), (2, 0.35), (3, 0.15))),
                          tone(262, 0.2, start=0.09, decay=14, partials=((1, 1.0), (2, 0.35), (3, 0.15))))),
    # crate approach: three quiet bell notes (C6 E6 G6) with a soft shimmer
    'crate/chime': (0.30, mix(0.9, tone(1047, 0.7, decay=6, partials=((1, 1.0), (2.76, 0.2), (5.4, 0.06))),
                             tone(1319, 0.6, start=0.09, decay=6, partials=((1, 1.0), (2.76, 0.2))),
                             tone(1568, 0.55, start=0.18, decay=6, partials=((1, 1.0), (2.76, 0.2))))),
}


def main():
    for sid, (vol, sig) in SOUNDS.items():
        pcm = (sig * vol * 32767).astype('<i2')
        dst = os.path.join(OUT, sid + '.ogg'); os.makedirs(os.path.dirname(dst), exist_ok=True)
        with tempfile.TemporaryDirectory() as td:
            wav = os.path.join(td, 'a.wav')
            with wave.open(wav, 'wb') as w:
                w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3',
                            '-fflags', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1',
                            '-serial_offset', '1', dst], check=True)
        print(sid, os.path.getsize(dst), 'bytes')


if __name__ == '__main__':
    main()
