// Minimal particle system for celebratory juice. Honours reduce-motion by
// emitting far fewer (or no) particles.
import { getSetting } from "../progress/settings.js";

export class Particles {
  constructor() {
    this.items = [];
  }

  burst(x, y, color, count = 14) {
    if (getSetting("reduceMotion")) count = Math.min(count, 4);
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const sp = 60 + Math.random() * 180;
      this.items.push({
        x,
        y,
        vx: Math.cos(a) * sp,
        vy: Math.sin(a) * sp - 40,
        life: 0.5 + Math.random() * 0.5,
        age: 0,
        r: 3 + Math.random() * 4,
        color: Array.isArray(color) ? color[(Math.random() * color.length) | 0] : color,
      });
    }
  }

  update(dt) {
    const arr = this.items;
    for (let i = arr.length - 1; i >= 0; i--) {
      const p = arr[i];
      p.age += dt;
      if (p.age >= p.life) {
        arr.splice(i, 1);
        continue;
      }
      p.vy += 420 * dt; // gravity
      p.x += p.vx * dt;
      p.y += p.vy * dt;
    }
  }

  draw(ctx) {
    for (const p of this.items) {
      ctx.globalAlpha = Math.max(0, 1 - p.age / p.life);
      ctx.fillStyle = p.color;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
}
