/**
 * 七巧板基础几何匹配。
 *
 * 这里不处理“是否锁定”之类的游戏状态，只回答三个问题：
 * 1. 两块是否属于可互换的全等形状；
 * 2. 一个拼块有哪些姿态能完整覆盖目标多边形；
 * 3. 当前拼块中心与目标姿态中心相距多远。
 */
var TangramGeometry = (function () {
    'use strict';

    var SHAPE_GROUP = {
        0: 'large', 1: 'large',
        2: 'small', 4: 'small',
        3: 'square',
        5: 'para',
        6: 'medium'
    };
    var POSE_CACHE = new WeakMap();

    function shapeOf(id) {
        return SHAPE_GROUP[id] || ('id:' + id);
    }

    function sameShape(a, b) {
        return shapeOf(a) === shapeOf(b);
    }

    function normRot(rot) {
        return Math.round(((rot % 360) + 360) % 360);
    }

    function centroid(pts) {
        var sx = 0;
        var sy = 0;
        for (var i = 0; i < pts.length; i++) {
            sx += pts[i][0];
            sy += pts[i][1];
        }
        return [sx / pts.length, sy / pts.length];
    }

    function worldOf(pts, x, y, rot) {
        return pts.map(function (pt) {
            var turned = TangramData.rotPoint(pt[0], pt[1], rot);
            return [turned[0] + x, turned[1] + y];
        });
    }

    function centerOfPiece(piece) {
        return centroid(worldOf(piece.pts, piece.x, piece.y, piece.rot));
    }

    function centerOfPose(pieceId, pose, templates) {
        return centroid(worldOf(templates[pieceId].pts, pose.x, pose.y, pose.rot));
    }

    /**
     * 按“中心到最远顶点”定义拼块半径，取其 60% 作为手感范围。
     * 默认 30px 下限避免小块难放，45px 限制按拼块尺寸计算出的范围。
     * basePx 可以更大：响应式画布在手机上会被缩小，需要用它把屏幕距离
     * 换算回画布坐标，否则 38 个画布像素在手机上只有十几像素的手感。
     */
    function snapDistance(piece, basePx) {
        var center = centroid(piece.pts);
        var radius = 0;
        for (var i = 0; i < piece.pts.length; i++) {
            radius = Math.max(radius, Math.hypot(
                piece.pts[i][0] - center[0], piece.pts[i][1] - center[1]));
        }
        return Math.max(basePx || 30, Math.min(45, radius * 0.6));
    }

    /** 双向累计每个顶点到另一多边形最近顶点的距离，0 表示完全重合。 */
    function polygonScore(a, b) {
        function oneWay(from, to) {
            var sum = 0;
            for (var i = 0; i < from.length; i++) {
                var nearest = Infinity;
                for (var j = 0; j < to.length; j++) {
                    nearest = Math.min(nearest, Math.hypot(
                        from[i][0] - to[j][0], from[i][1] - to[j][1]));
                }
                sum += nearest;
            }
            return sum;
        }

        return oneWay(a, b) + oneWay(b, a);
    }

    /**
     * 枚举每个 45° 方向，并让每对顶点依次对齐。
     * 因为返回全部零误差姿态，正方形自然得到 4 个等价方向，
     * 平行四边形自然得到 2 个，不需要写死角度特判。
     */
    function posesToPolygon(pieceId, target, templates) {
        var local = templates[pieceId].pts;
        var matches = [];
        var seen = {};
        for (var rot = 0; rot < 360; rot += 45) {
            var rotated = local.map(function (pt) {
                return TangramData.rotPoint(pt[0], pt[1], rot);
            });
            for (var i = 0; i < rotated.length; i++) {
                for (var j = 0; j < target.length; j++) {
                    var x = target[j][0] - rotated[i][0];
                    var y = target[j][1] - rotated[i][1];
                    var world = rotated.map(function (pt) {
                        return [pt[0] + x, pt[1] + y];
                    });
                    var score = polygonScore(world, target);
                    var key = rot + ':' + Math.round(x * 1000) + ':' + Math.round(y * 1000);
                    if (score <= 2 && !seen[key]) {
                        seen[key] = true;
                        matches.push({x: x, y: y, rot: rot, score: score});
                    }
                }
            }
        }
        return matches;
    }

    function posesToMatch(pieceId, placement, templates) {
        var cached = POSE_CACHE.get(placement);
        if (cached && cached[pieceId]) return cached[pieceId];
        var target = worldOf(templates[placement.id].pts,
            placement.x, placement.y, placement.rot);
        var poses = posesToPolygon(pieceId, target, templates);
        if (!cached) {
            cached = {};
            POSE_CACHE.set(placement, cached);
        }
        cached[pieceId] = poses;
        return poses;
    }

    /** 返回一个稳定的代表姿态；实际吸附会检查 posesToMatch 的全部姿态。 */
    function poseToMatch(pieceId, placement, templates) {
        var poses = posesToMatch(pieceId, placement, templates);
        var best = poses[0] || null;
        for (var i = 1; i < poses.length; i++) {
            if (poses[i].score < best.score ||
                (poses[i].score === best.score && poses[i].rot < best.rot)) best = poses[i];
        }
        return best;
    }

    return {
        centerOfPiece: centerOfPiece,
        centerOfPose: centerOfPose,
        normRot: normRot,
        poseToMatch: poseToMatch,
        posesToMatch: posesToMatch,
        posesToPolygon: posesToPolygon,
        sameShape: sameShape,
        shapeOf: shapeOf,
        snapDistance: snapDistance,
        worldOf: worldOf
    };
})();
