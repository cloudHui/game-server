/**
 * 七巧板入口：吸附、指针交互与状态编排。
 * 依赖 tangram-data / tangram-view / tangram-layout / tangram-snap。
 */
(function () {
    'use strict';

    MiniGames.requireLogin();
    document.getElementById('back').href = appUrl('/pages/mini/index.html');

    var canvas = document.getElementById('c');
    var ctx = canvas.getContext('2d');
    var SNAP_PX = TangramData.SNAP_PX;
    var PAD = TangramData.PAD;
    var templates = TangramData.scaledTemplates();
    var levels = TangramData.buildLevels(canvas.width, canvas.height);

    var state = {
        templates: templates,
        levels: levels,
        level: 0,
        pieces: [],
        selected: -1,
        sameDirection: false,
        showHelpLines: false,
        particles: []
    };
    var startedAt = 0;
    var completed = false;

    var drag = null;
    var raf = 0;
    document.getElementById('levelCount').textContent = levels.length;

    function targetBounds() {
        var list = levels[state.level].placements;
        var xs = [];
        var ys = [];
        var i;
        var pts;
        var j;
        var g;
        for (i = 0; i < list.length; i++) {
            g = {
                pts: templates[list[i].id].pts,
                x: list[i].x,
                y: list[i].y,
                rot: list[i].rot
            };
            pts = TangramView.worldPts(g);
            for (j = 0; j < pts.length; j++) {
                xs.push(pts[j][0]);
                ys.push(pts[j][1]);
            }
        }
        return {
            minX: Math.min.apply(null, xs),
            minY: Math.min.apply(null, ys),
            maxX: Math.max.apply(null, xs),
            maxY: Math.max.apply(null, ys)
        };
    }

    function resetPieces() {
        state.pieces = TangramLayout.clonePieces(templates, targetBounds(), canvas, PAD,
            levels[state.level].placements, state.sameDirection);
        state.selected = -1;
        drag = null;
        state.particles = [];
        startedAt = Date.now();
        completed = false;
    }

    function hit(p, x, y) {
        var pts = TangramView.worldPts(p);
        var inside = false;
        var i;
        var j;
        for (i = 0, j = pts.length - 1; i < pts.length; j = i++) {
            var xi = pts[i][0];
            var yi = pts[i][1];
            var xj = pts[j][0];
            var yj = pts[j][1];
            if (((yi > y) !== (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    function spawnStars(cx, cy, color) {
        var i;
        var a;
        var sp;
        for (i = 0; i < 16; i++) {
            a = (Math.PI * 2 * i) / 16 + Math.random() * 0.4;
            sp = 2.4 + Math.random() * 5;
            state.particles.push({
                x: cx + (Math.random() - 0.5) * 12,
                y: cy + (Math.random() - 0.5) * 12,
                vx: Math.cos(a) * sp,
                vy: Math.sin(a) * sp - 1.4,
                life: 1,
                decay: 0.016 + Math.random() * 0.014,
                r: 4.5 + Math.random() * 5.5,
                rot: Math.random() * Math.PI,
                spin: (Math.random() - 0.5) * 0.4,
                color: color
            });
        }
        if (!raf) raf = requestAnimationFrame(tick);
    }

    function onPieceLocked(p) {
        var c = TangramView.centroid(TangramView.worldPts(p));
        spawnStars(c[0], c[1], p.color);
    }

    function trySnap(p) {
        return TangramSnap.trySnap(p, levels[state.level], state.pieces, SNAP_PX, onPieceLocked, templates);
    }

    function previewSnap(p) {
        return TangramSnap.previewSnap(p, levels[state.level], state.pieces, SNAP_PX, templates);
    }

    function updateStatus() {
        var done = 0;
        var i;
        var el = document.getElementById('status');
        var rotate = document.getElementById('rotate');
        rotate.disabled = state.sameDirection || state.selected < 0 || state.pieces[state.selected].locked;
        for (i = 0; i < state.pieces.length; i++) if (state.pieces[i].locked) done++;
        if (done === state.pieces.length) {
            el.textContent = '完成！「' + levels[state.level].name + '」全部吸附成功，可点下一题';
            el.style.color = '#2e7d32';
            if (!completed) {
                completed = true;
                MiniResult.show({
                    pattern: '「' + levels[state.level].name + '」',
                    elapsed: formatElapsed(Date.now() - startedAt),
                    onNext: function () {
                        document.getElementById('next').click();
                    }
                });
            }
        } else {
            el.textContent = '题目：' + levels[state.level].name + '（已拼对 ' + done + '/' +
                state.pieces.length + '）方向正确且中心接近目标（约30-45px）会吸附；已吸附块不可再提起';
            el.style.color = '#1565c0';
        }
    }

    function formatElapsed(ms) {
        var sec = Math.max(0, Math.round(ms / 1000));
        return Math.floor(sec / 60) + '分' + (sec % 60) + '秒';
    }

    function draw() {
        TangramView.render(ctx, canvas, state);
        updateStatus();
    }

    function tick() {
        var i;
        var s;
        var p;
        var alive = false;
        var particles = state.particles;
        raf = 0;
        for (i = 0; i < state.pieces.length; i++) {
            p = state.pieces[i];
            /* 拼块不使用重力；tick 只负责吸附粒子动画。 */
        }
        for (i = particles.length - 1; i >= 0; i--) {
            s = particles[i];
            s.x += s.vx;
            s.y += s.vy;
            s.vy += 0.13;
            s.vx *= 0.985;
            s.rot += s.spin;
            s.life -= s.decay;
            if (s.life <= 0) particles.splice(i, 1);
            else alive = true;
        }
        draw();
        if (alive) raf = requestAnimationFrame(tick);
    }

    function pointer(e) {
        var r = canvas.getBoundingClientRect();
        var sx = canvas.width / r.width;
        var sy = canvas.height / r.height;
        var cx = (e.clientX !== undefined ? e.clientX : e.touches[0].clientX) - r.left;
        var cy = (e.clientY !== undefined ? e.clientY : e.touches[0].clientY) - r.top;
        return {x: cx * sx, y: cy * sy};
    }

    canvas.addEventListener('pointerdown', function (e) {
        var p = pointer(e);
        var i;
        var moved;
        var pieces = state.pieces;
        state.selected = -1;
        drag = null;
        for (i = pieces.length - 1; i >= 0; i--) {
            if (!hit(pieces[i], p.x, p.y)) continue;
            /* 已吸附块挡住点击，不可提起、不可点穿到下层 */
            if (pieces[i].locked) break;
            moved = pieces.splice(i, 1)[0];
            pieces.push(moved);
            state.selected = pieces.length - 1;
            drag = {
                pointerId: e.pointerId,
                x: p.x - moved.x,
                y: p.y - moved.y
            };
            canvas.setPointerCapture(e.pointerId);
            break;
        }
        draw();
    });

    canvas.addEventListener('pointermove', function (e) {
        if (state.selected < 0 || !drag || e.pointerId !== drag.pointerId) return;
        var p = pointer(e);
        var piece = state.pieces[state.selected];
        if (piece.locked) return;
        piece.x = p.x - drag.x;
        piece.y = p.y - drag.y;
        previewSnap(piece);
        draw();
    });

    function endDrag(e, cancelled) {
        if (!drag || (e && e.pointerId !== drag.pointerId)) return;
        if (state.selected >= 0) {
            var piece = state.pieces[state.selected];
            TangramLayout.clampPieceInCanvas(piece, canvas, PAD);
            if (!cancelled) trySnap(piece);
            piece.snapped = false;
        }
        if (e && canvas.hasPointerCapture(e.pointerId)) canvas.releasePointerCapture(e.pointerId);
        drag = null;
        draw();
    }

    canvas.addEventListener('pointerup', function (e) {
        endDrag(e, false);
    });
    canvas.addEventListener('pointercancel', function (e) {
        endDrag(e, true);
    });

    document.getElementById('rotate').onclick = function () {
        if (state.sameDirection || state.selected < 0 || state.pieces[state.selected].locked) return;
        var piece = state.pieces[state.selected];
        var before = TangramView.centroid(TangramView.worldPts(piece));
        piece.rot = (piece.rot + 45) % 360;
        var after = TangramView.centroid(TangramView.worldPts(piece));
        piece.x += before[0] - after[0];
        piece.y += before[1] - after[1];
        TangramLayout.clampPieceInCanvas(piece, canvas, PAD);
        trySnap(piece);
        draw();
    };

    document.getElementById('reset').onclick = function () {
        resetPieces();
        draw();
    };

    document.getElementById('next').onclick = function () {
        state.level = (state.level + 1) % levels.length;
        resetPieces();
        draw();
    };

    document.getElementById('helpLines').onchange = function () {
        state.showHelpLines = !!this.checked;
        draw();
    };

    document.getElementById('sameDirection').onchange = function () {
        state.sameDirection = !!this.checked;
        resetPieces();
        draw();
    };

    resetPieces();
    draw();
})();
