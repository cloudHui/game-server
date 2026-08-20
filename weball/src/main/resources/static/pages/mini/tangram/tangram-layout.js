/**
 * 七巧板布局：待拼块散列在目标轮廓四周，保证入框且互不重叠。
 */
var TangramLayout = (function () {
    'use strict';

    function boxesOverlap(a, b, gap) {
        return !(a.maxX + gap <= b.minX || b.maxX + gap <= a.minX ||
            a.maxY + gap <= b.minY || b.maxY + gap <= a.minY);
    }

    function inCanvas(box, canvas, pad) {
        return box.minX >= pad && box.minY >= pad &&
            box.maxX <= canvas.width - pad && box.maxY <= canvas.height - pad;
    }

    function overlapsAny(box, placed, gap) {
        var i;
        for (i = 0; i < placed.length; i++) {
            if (boxesOverlap(box, placed[i], gap)) return true;
        }
        return false;
    }

    function pieceOverlapsAny(piece, placed) {
        for (var i = 0; i < placed.length; i++) {
            if (TangramView.polygonsOverlap(piece, placed[i])) return true;
        }
        return false;
    }

    /** 生成目标图形周围的散列位置。 */
    function scatterOrigins(canvas, tb, pad) {
        var candidates = [];
        var x;
        var y;
        var step = 22;

        function push(px, py) {
            candidates.push([px, py]);
        }

        for (x = pad + 40; x <= canvas.width - pad - 40; x += step) {
            push(x, Math.max(pad + 50, tb.minY - 100));
            push(x, Math.min(canvas.height - pad - 50, tb.maxY + 100));
            push(x, pad + 70);
            push(x, canvas.height - pad - 70);
        }
        for (y = pad + 40; y <= canvas.height - pad - 40; y += step) {
            push(Math.max(pad + 50, tb.minX - 100), y);
            push(Math.min(canvas.width - pad - 50, tb.maxX + 100), y);
            push(pad + 90, y);
            push(canvas.width - pad - 90, y);
        }
        push(pad + 120, pad + 90);
        push(canvas.width - pad - 120, pad + 90);
        push(pad + 120, canvas.height - pad - 90);
        push(canvas.width - pad - 120, canvas.height - pad - 90);

        /* 边缘候选不足时，继续扫描整个画布，避免失败后把拼块堆到同一固定点。 */
        for (y = pad + 20; y <= canvas.height - pad - 20; y += 8) {
            for (x = pad + 20; x <= canvas.width - pad - 20; x += 8) {
                push(x, y);
            }
        }
        return candidates;
    }

    /** 将拼块限制在画布安全范围内。 */
    function clampPieceInCanvas(p, canvas, pad) {
        var box = TangramView.aabbOf(p);
        var dx = 0;
        var dy = 0;
        if (box.minX < pad) dx = pad - box.minX;
        if (box.maxX > canvas.width - pad) dx = canvas.width - pad - box.maxX;
        if (box.minY < pad) dy = pad - box.minY;
        if (box.maxY > canvas.height - pad) dy = canvas.height - pad - box.maxY;
        p.x += dx;
        p.y += dy;
    }

    /** 选择一个不与目标和其他拼块重叠的起始位置。 */
    function placePieceNoOverlap(piece, candidates, placedBoxes, placedPieces, tb, canvas, pad, keepRotation) {
        var i;
        var box;
        var rot;
        var savedRot = piece.rot;
        var rotations = keepRotation ? [piece.rot] : [0, 45, 90, 135, 180, 225, 270, 315];

        for (rot = 0; rot < rotations.length; rot++) {
            piece.rot = rotations[rot];
            for (i = 0; i < candidates.length; i++) {
                piece.x = candidates[i][0];
                piece.y = candidates[i][1];
                box = TangramView.aabbOf(piece);
                if (!inCanvas(box, canvas, pad)) continue;
                if (boxesOverlap(box, tb, 6)) continue;
                if (overlapsAny(box, placedBoxes, 8) || pieceOverlapsAny(piece, placedPieces)) continue;
                placedBoxes.push(box);
                placedPieces.push({pts: piece.pts, x: piece.x, y: piece.y, rot: piece.rot});
                return true;
            }
        }

        /* 候选已穷尽时保留初始位置；正常画布尺寸下不会走到这里。 */
        piece.rot = savedRot;
        return false;
    }

    /** 创建并散开放置一局的全部拼块。 */
    function clonePieces(templates, targetBounds, canvas, pad, targetPlacements, keepTargetDirection) {
        var candidates = scatterOrigins(canvas, targetBounds, pad);
        var placedBoxes = [];
        var placedPieces = [];
        var order = [0, 1, 6, 5, 3, 2, 4];
        var map = {};
        var pieces = [];
        var i;
        var tmpl;
        var piece;

        for (i = 0; i < order.length; i++) {
            tmpl = templates[order[i]];
            piece = {
                id: tmpl.id,
                color: tmpl.color,
                pts: tmpl.pts.map(function (pt) {
                    return pt.slice();
                }),
                x: pad + 80,
                y: pad + 80,
                rot: 0,
                locked: false
            };
            if (keepTargetDirection && targetPlacements) {
                for (var k = 0; k < targetPlacements.length; k++) {
                    if (targetPlacements[k].id === piece.id) {
                        piece.rot = targetPlacements[k].rot;
                        break;
                    }
                }
            }
            placePieceNoOverlap(piece, candidates, placedBoxes, placedPieces, targetBounds, canvas, pad,
                !!(keepTargetDirection && targetPlacements));
            map[piece.id] = piece;
        }
        for (i = 0; i < templates.length; i++) pieces.push(map[i]);
        return pieces;
    }

    return {
        clonePieces: clonePieces,
        clampPieceInCanvas: clampPieceInCanvas
    };
})();
