// Audio: plays ElevenLabs-generated files when available, with a full
// procedural (WebAudio synth) fallback so the game still works if the audio
// assets are missing. All playback is local — the browser never calls any API.
import {getSetting} from "../progress/settings.js";

let ctx = null;
let master = null;
let musicGain = null;
let musicTimer = null; // synth-fallback loop
let musicSource = null; // file loop source
let currentTrack = null;
const buffers = {}; // url -> AudioBuffer (or null while loading / undefined if failed)

function ensure() {
    if (ctx) return ctx;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    ctx = new AC();
    master = ctx.createGain();
    master.gain.value = 0.6;
    master.connect(ctx.destination);
    musicGain = ctx.createGain();
    musicGain.gain.value = 0.3;
    musicGain.connect(master);
    return ctx;
}

export function getCtx() {
    return ensure();
}

// Fetch + decode an audio file into an AudioBuffer (cached). Returns null on
// failure so callers can fall back to synth.
export async function loadBuffer(url) {
    if (url in buffers) return buffers[url] || null;
    buffers[url] = null;
    const c = ensure();
    if (!c) return null;
    try {
        const res = await fetch(url);
        if (!res.ok) throw new Error(res.status);
        const buf = await c.decodeAudioData(await res.arrayBuffer());
        buffers[url] = buf;
        return buf;
    } catch (e) {
        buffers[url] = undefined; // mark unavailable (don't retry forever)
        return null;
    }
}

export function playBuffer(buffer, {gain = 0.9, loop = false, dest = null} = {}) {
    const c = ensure();
    if (!c || !buffer) return null;
    const src = c.createBufferSource();
    src.buffer = buffer;
    src.loop = loop;
    const g = c.createGain();
    g.gain.value = gain;
    src.connect(g);
    g.connect(dest || master);
    src.start();
    return src;
}

// Must be called from a user gesture to unlock audio on mobile.
export function unlock() {
    const c = ensure();
    if (c && c.state === "suspended") c.resume();
    preloadSfx();
}

// ---- SFX: generated files with synth fallback ----
const SFX_FILES = {
    collect: "assets/audio/sfx/collect.mp3",
    star: "assets/audio/sfx/star.mp3",
    correct: "assets/audio/sfx/correct.mp3",
    wrong: "assets/audio/sfx/wrong.mp3",
    win: "assets/audio/sfx/win.mp3",
    unlock: "assets/audio/sfx/unlock.mp3",
    click: "assets/audio/sfx/tap.mp3",
    levelup: "assets/audio/sfx/levelup.mp3",
};

let sfxPreloaded = false;

function preloadSfx() {
    if (sfxPreloaded) return;
    sfxPreloaded = true;
    for (const url of Object.values(SFX_FILES)) loadBuffer(url);
}

function tone(freq, dur, type = "sine", vol = 0.3, when = 0) {
    const c = ensure();
    if (!c) return;
    const t0 = c.currentTime + when;
    const osc = c.createOscillator();
    const g = c.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t0);
    g.gain.setValueAtTime(0, t0);
    g.gain.linearRampToValueAtTime(vol, t0 + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(g);
    g.connect(master);
    osc.start(t0);
    osc.stop(t0 + dur + 0.02);
}

function sweep(f1, f2, dur, type = "triangle", vol = 0.3) {
    const c = ensure();
    if (!c) return;
    const t0 = c.currentTime;
    const osc = c.createOscillator();
    const g = c.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(f1, t0);
    osc.frequency.exponentialRampToValueAtTime(Math.max(1, f2), t0 + dur);
    g.gain.setValueAtTime(vol, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(g);
    g.connect(master);
    osc.start(t0);
    osc.stop(t0 + dur + 0.02);
}

// Synth fallbacks, used when a generated file isn't (yet) loaded.
const synthSfx = {
    move: () => tone(420, 0.06, "square", 0.12),
    collect: () => {
        tone(880, 0.08, "triangle", 0.2);
        tone(1320, 0.08, "triangle", 0.12, 0.03);
    },
    star: () => {
        tone(784, 0.09, "triangle", 0.22);
        tone(1175, 0.12, "triangle", 0.18, 0.06);
    },
    correct: () => {
        tone(523, 0.1, "sine", 0.25);
        tone(659, 0.1, "sine", 0.25, 0.08);
        tone(784, 0.16, "sine", 0.25, 0.16);
    },
    wrong: () => sweep(300, 140, 0.25, "sawtooth", 0.16),
    click: () => tone(600, 0.05, "square", 0.14),
    win: () => {
        [523, 659, 784, 1047].forEach((n, i) => tone(n, 0.22, "triangle", 0.26, i * 0.12));
    },
    unlock: () => {
        tone(659, 0.12, "sine", 0.25);
        tone(988, 0.2, "sine", 0.22, 0.1);
    },
    levelup: () => {
        [523, 784, 1047].forEach((n, i) => tone(n, 0.14, "triangle", 0.22, i * 0.07));
    },
};

function playSfx(name) {
    if (!getSetting("sound")) return;
    ensure();
    const url = SFX_FILES[name];
    const buf = url && buffers[url];
    if (buf) playBuffer(buf, {gain: 0.9});
    else if (synthSfx[name]) synthSfx[name]();
}

// sfx.correct(), sfx.move(), etc. — any name works; unknown names no-op.
export const sfx = new Proxy({}, {get: (_t, name) => () => playSfx(name)});

// ---- Music: generated loop with synth fallback ----
const MUSIC_FILES = {
    menu_calm: "assets/audio/music/menu_calm.mp3",
    game_calm: "assets/audio/music/game_calm.mp3",
};

export function startMusic(track = "menu_calm") {
    if (!getSetting("music")) return;
    const c = ensure();
    if (!c) return;
    if (currentTrack === track && (musicSource || musicTimer)) return;
    stopMusic();
    currentTrack = track;
    const url = MUSIC_FILES[track];
    if (url) {
        loadBuffer(url).then((buf) => {
            if (currentTrack !== track || !getSetting("music")) return;
            if (buf) musicSource = playBuffer(buf, {gain: 1, loop: true, dest: musicGain});
            else startSynthMusic(); // file failed -> fallback
        });
    } else {
        startSynthMusic();
    }
}

export function stopMusic() {
    currentTrack = null;
    if (musicSource) {
        try {
            musicSource.stop();
        } catch (e) {
        }
        musicSource = null;
    }
    if (musicTimer) {
        clearInterval(musicTimer);
        musicTimer = null;
    }
}

const MELODY = [523, 587, 659, 784, 659, 587, 523, 440];

function startSynthMusic() {
    const c = ensure();
    if (!c || musicTimer) return;
    let i = 0;
    musicTimer = setInterval(() => {
        if (!getSetting("music")) return stopMusic();
        const f = MELODY[i % MELODY.length];
        const t0 = c.currentTime;
        const osc = c.createOscillator();
        const g = c.createGain();
        osc.type = "sine";
        osc.frequency.value = f / 2;
        g.gain.setValueAtTime(0, t0);
        g.gain.linearRampToValueAtTime(0.5, t0 + 0.05);
        g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.5);
        osc.connect(g);
        g.connect(musicGain);
        osc.start(t0);
        osc.stop(t0 + 0.55);
        i++;
    }, 480);
}
