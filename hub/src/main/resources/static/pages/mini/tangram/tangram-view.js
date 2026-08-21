/**
 * 七巧板渲染：目标剪影、帮助线、彩片与粒子。
 */
var TangramView = (function () {
    'use strict';

    function worldPts(p) {
        return p.pts.map(function (pt) {
            var r = TangramData.rotPoint(pt[0], pt[1], p.rot);
            return [r[0] + p.x, r[1] + p.y];
        });
    }

    function aabbOf(p) {
        var pts = worldPts(p);
        var minX = Infinity;
        var minY = Infinity;
        var maxX = -Infinity;
        var maxY = -Infinity;
        var i;
        for (i = 0; i < pts.length; i++) {
            if (pts[i][0] < minX) minX = pts[i][0];
            if (pts[i][1] < minY) minY = pts[i][1];
            if (pts[i][0] > maxX) maxX = pts[i][0];
            if (pts[i][1] > maxY) maxY = pts[i][1];
        }
        return {minX: minX, minY: minY, maxX: maxX, maxY: maxY};
    }

    function polygonsOverlap(a, b) {
        function axisSeparates(poly, other) {
            for (var i = 0; i < poly.length; i++) {
                var n = poly[(i + 1) % poly.length];
                var v = poly[i];
                var ax = -(n[1] - v[1]);
                var ay = n[0] - v[0];
                var axisLength = Math.hypot(ax, ay);
                if (axisLength < 1e-9) continue;
                ax /= axisLength;
                ay /= axisLength;
                var amin = Infinity;
                var amax = -Infinity;
                var bmin = Infinity;
                var bmax = -Infinity;
                for (var j = 0; j < poly.length; j++) {
                    var ap = poly[j][0] * ax + poly[j][1] * ay;
                    amin = Math.min(amin, ap);
                    amax = Math.max(amax, ap);
                }
                for (var k = 0; k < other.length; k++) {
                    var bp = other[k][0] * ax + other[k][1] * ay;
                    bmin = Math.min(bmin, bp);
                    bmax = Math.max(bmax, bp);
                }
                /* 坐标换算会有微小浮点误差；0.2px 内按共边/共点而非面积重叠处理。 */
                if (amax <= bmin + 0.2 || bmax <= amin + 0.2) return true;
            }
            return false;
        }

        return !axisSeparates(a, b) && !axisSeparates(b, a);
    }

    function centroid(pts) {
        var sx = 0;
        var sy = 0;
        var i;
        for (i = 0; i < pts.length; i++) {
            sx += pts[i][0];
            sy += pts[i][1];
        }
        return [sx / pts.length, sy / pts.length];
    }

    function drawPoly(ctx, pts, fill, stroke, lineWidth) {
        var j;
        ctx.beginPath();
        ctx.moveTo(pts[0][0], pts[0][1]);
        for (j = 1; j < pts.length; j++) ctx.lineTo(pts[j][0], pts[j][1]);
        ctx.closePath();
        if (fill) {
            ctx.fillStyle = fill;
            ctx.fill();
        }
        if (stroke) {
            ctx.strokeStyle = stroke;
            ctx.lineWidth = lineWidth || 1.5;
            ctx.stroke();
        }
    }

    function drawStar(ctx, x, y, r, rot) {
        var i;
        var a;
        var b;
        ctx.beginPath();
        for (i = 0; i < 5; i++) {
            a = rot + i * Math.PI * 2 / 5 - Math.PI / 2;
            b = a + Math.PI / 5;
            if (i === 0) ctx.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
            else ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
            ctx.lineTo(x + Math.cos(b) * r * 0.42, y + Math.sin(b) * r * 0.42);
        }
        ctx.closePath();
    }

    function drawTarget(ctx, level, pieces, showHelpLines, snapPx, templates, selected) {
        var list = level.placements;
        var sols = (level.solutions && level.solutions.length) ? level.solutions : [list];
        var i;
        var pl;
        var pts;
        var g;

        for (i = 0; i < list.length; i++) {
            pl = list[i];
            g = {pts: templates[pl.id].pts, x: pl.x, y: pl.y, rot: pl.rot};
            pts = worldPts(g);
            drawPoly(ctx, pts, '#b0bec5', showHelpLines ? '#78909c' : null, 1.5);
        }

    }

    function drawPieces(ctx, pieces, selected) {
        var i;
        var p;
        var pts;
        for (i = 0; i < pieces.length; i++) {
            p = pieces[i];
            pts = worldPts(p);
            ctx.globalAlpha = p.locked ? 1 : (i === selected ? 0.96 : 0.9);
            drawPoly(
                ctx,
                pts,
                p.color,
                p.locked ? '#1b5e20' : (i === selected ? '#111' : 'rgba(255,255,255,0.55)'),
                p.locked || i === selected ? 2.5 : 1
            );
            ctx.globalAlpha = 1;
        }
    }

    function drawParticles(ctx, particles) {
        var i;
        var s;
        for (i = 0; i < particles.length; i++) {
            s = particles[i];
            ctx.globalAlpha = Math.max(0, s.life);
            ctx.fillStyle = s.color;
            drawStar(ctx, s.x, s.y, s.r * (0.55 + 0.45 * s.life), s.rot);
            ctx.fill();
        }
        ctx.globalAlpha = 1;
    }

    function render(ctx, canvas, state) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        drawTarget(ctx, state.levels[state.level], state.pieces, state.showHelpLines,
            TangramData.SNAP_PX, state.templates, state.selected);
        drawPieces(ctx, state.pieces, state.selected);
        drawParticles(ctx, state.particles);
    }

    return {
        worldPts: worldPts,
        aabbOf: aabbOf,
        polygonsOverlap: function (a, b) {
            return polygonsOverlap(worldPts(a), worldPts(b));
        },
        centroid: centroid,
        render: render
    };
})();
