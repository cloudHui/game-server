// Unified input: tap-a-lane (touch/mouse), horizontal swipe, and keyboard.
// Large, forgiving hit zones suit small hands.

export class Input {
  constructor(canvas, handlers) {
    this.canvas = canvas;
    this.h = handlers; // { onAbsolute(i), onRelative(dir), onPause() }
    this.lanes = handlers.lanes || 3;
    this._startX = null;
    this._startY = null;
    this._bind();
  }

  _laneFromX(clientX) {
    const rect = this.canvas.getBoundingClientRect();
    const rel = (clientX - rect.left) / rect.width;
    return Math.max(0, Math.min(this.lanes - 1, Math.floor(rel * this.lanes)));
  }

  _bind() {
    this._onDown = (e) => {
      const p = e.touches ? e.touches[0] : e;
      this._startX = p.clientX;
      this._startY = p.clientY;
    };
    this._onUp = (e) => {
      if (this._startX == null) return;
      const p = e.changedTouches ? e.changedTouches[0] : e;
      const dx = p.clientX - this._startX;
      const dy = p.clientY - this._startY;
      if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) {
        this.h.onRelative && this.h.onRelative(dx > 0 ? 1 : -1);
      } else if (Math.abs(dx) < 16 && Math.abs(dy) < 16) {
        this.h.onAbsolute && this.h.onAbsolute(this._laneFromX(p.clientX));
      }
      this._startX = this._startY = null;
    };
    this._onKey = (e) => {
      switch (e.key) {
        case "ArrowLeft":
        case "a":
        case "A":
          this.h.onRelative && this.h.onRelative(-1);
          e.preventDefault();
          break;
        case "ArrowRight":
        case "d":
        case "D":
          this.h.onRelative && this.h.onRelative(1);
          e.preventDefault();
          break;
        case "1":
          this.h.onAbsolute && this.h.onAbsolute(0);
          break;
        case "2":
        case "ArrowUp":
          this.h.onAbsolute && this.h.onAbsolute(1);
          break;
        case "3":
          this.h.onAbsolute && this.h.onAbsolute(2);
          break;
        case "p":
        case "P":
        case "Escape":
          this.h.onPause && this.h.onPause();
          break;
      }
    };
    this.canvas.addEventListener("pointerdown", this._onDown);
    this.canvas.addEventListener("pointerup", this._onUp);
    window.addEventListener("keydown", this._onKey);
  }

  destroy() {
    this.canvas.removeEventListener("pointerdown", this._onDown);
    this.canvas.removeEventListener("pointerup", this._onUp);
    window.removeEventListener("keydown", this._onKey);
  }
}
