// Settings + accessibility application. Reflects saved preferences onto the
// document so they take effect immediately and persist locally.
import {get, save} from "./save.js";

export function applyAccessibility() {
    const s = get().settings;
    const b = document.body;
    b.classList.toggle("reduce-motion", !!s.reduceMotion);
    b.classList.toggle("high-contrast", !!s.highContrast);
    b.classList.toggle("dyslexia-font", !!s.dyslexiaFont);
    b.classList.toggle("colorblind", !!s.colorblind);
}

export function setSetting(key, value) {
    get().settings[key] = value;
    save();
    applyAccessibility();
    if (key === "voice" && window.MiniFeedback) window.MiniFeedback.setEnabled(value);
}

export function getSetting(key) {
    return get().settings[key];
}

// Honour the OS "reduce motion" preference on first run.
export function syncSystemPrefs() {
    const s = get().settings;
    if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        s.reduceMotion = true;
    }
    save();
    applyAccessibility();
}
