// Fixed-ish game loop built on requestAnimationFrame. dt is clamped so a
// backgrounded tab or slow frame never causes a physics "explosion".

export class Loop {
    constructor(step) {
        this.step = step; // step(dt in seconds)
        this._raf = 0;
        this._last = 0;
        this._running = false;
        this._tick = this._tick.bind(this);
    }

    start() {
        if (this._running) return;
        this._running = true;
        this._last = performance.now();
        this._raf = requestAnimationFrame(this._tick);
    }

    stop() {
        this._running = false;
        cancelAnimationFrame(this._raf);
    }

    _tick(now) {
        if (!this._running) return;
        let dt = (now - this._last) / 1000;
        this._last = now;
        if (dt > 0.05) dt = 0.05; // clamp to avoid huge jumps
        this.step(dt);
        this._raf = requestAnimationFrame(this._tick);
    }
}
