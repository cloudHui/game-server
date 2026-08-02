// Voice playback — plays named narration clips (tutorial + fixed Pip lines).
// Files are generated at dev time; the browser only plays local audio. Respects
// the Voice and Sound settings. Only one line plays at a time.
import {getSetting} from "../progress/settings.js";
import {loadBuffer, playBuffer} from "./audio.js";

let current = null;

export function playVoice(id) {
    if (!getSetting("voice") || !getSetting("sound")) return;
    const url = `assets/audio/voice/${id}.mp3`;
    loadBuffer(url).then((buf) => {
        if (!buf || !getSetting("voice") || !getSetting("sound")) return;
        stopVoice();
        current = playBuffer(buf, {gain: 1.0});
        if (current) current.onended = () => {
            current = null;
        };
    });
}

export function stopVoice() {
    if (current) {
        try {
            current.stop();
        } catch (e) {
        }
        current = null;
    }
}
