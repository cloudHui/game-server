/**
 * 七巧板吸附状态编排。
 *
 * 几何计算由 TangramGeometry 完成，等价答案由 TangramSolutions 提供。
 * 本文件只负责占位、最近候选选择、重叠保护和最终锁定。
 */
var TangramSnap = (function () {
    'use strict';

    function pieceCoversPlacement(piece, placement, templates, tolerance) {
        var poses = TangramGeometry.posesToMatch(piece.id, placement, templates);
        var center = TangramGeometry.centerOfPiece(piece);
        for (var i = 0; i < poses.length; i++) {
            if (TangramGeometry.normRot(piece.rot) !== poses[i].rot) continue;
            var target = TangramGeometry.centerOfPose(piece.id, poses[i], templates);
            if (Math.hypot(center[0] - target[0], center[1] - target[1]) <=
                (tolerance || 2)) return true;
        }
        return false;
    }

    /**
     * 将已锁定拼块逐一匹配到当前答案的槽位。
     * 大三角形和小三角形分别允许组内互换，但一个槽位只能占用一次。
     */
    function occupiedSlotIndexes(pieces, solution, templates) {
        var used = {};
        pieces.forEach(function (piece) {
            if (!piece.locked) return;
            for (var i = 0; i < solution.length; i++) {
                if (used[i] ||
                    !TangramGeometry.sameShape(piece.id, solution[i].id)) continue;
                if (pieceCoversPlacement(piece, solution[i], templates, 2)) {
                    used[i] = true;
                    break;
                }
            }
        });
        return used;
    }

    /** 已锁定块必须能全部落在同一个答案中，避免混用两套互斥分割。 */
    function lockedMatchSolution(pieces, solution, templates) {
        var lockedCount = pieces.filter(function (piece) {
            return piece.locked;
        }).length;
        if (!lockedCount) return true;
        return Object.keys(occupiedSlotIndexes(pieces, solution, templates)).length ===
            lockedCount;
    }

    function overlapsLocked(projected, piece, pieces) {
        return pieces.some(function (other) {
            return other !== piece && other.locked &&
                TangramView.polygonsOverlap(projected, other);
        });
    }

    /**
     * 收集所有方向正确且中心进入吸附半径的姿态，返回中心距离最近者。
     * 先使用投影副本检查重叠，失败时不会改动正在拖动的拼块坐标。
     */
    function nearestCandidate(piece, level, pieces, basePx, templates) {
        var currentCenter = TangramGeometry.centerOfPiece(piece);
        var maxDistance = TangramGeometry.snapDistance(piece, basePx);
        var best = null;
        var solutions = TangramSolutions.solutionsOf(level, templates);

        solutions.forEach(function (solution) {
            if (!lockedMatchSolution(pieces, solution, templates)) return;
            var used = occupiedSlotIndexes(pieces, solution, templates);
            solution.forEach(function (placement, slotIndex) {
                if (used[slotIndex] ||
                    !TangramGeometry.sameShape(piece.id, placement.id)) return;
                TangramGeometry.posesToMatch(piece.id, placement, templates)
                    .forEach(function (pose) {
                        if (TangramGeometry.normRot(piece.rot) !== pose.rot) return;
                        var targetCenter = TangramGeometry.centerOfPose(piece.id, pose, templates);
                        var distance = Math.hypot(
                            currentCenter[0] - targetCenter[0],
                            currentCenter[1] - targetCenter[1]);
                        if (distance > maxDistance || (best && distance >= best.distance)) return;

                        var projected = {
                            pts: piece.pts,
                            x: pose.x,
                            y: pose.y,
                            rot: TangramGeometry.normRot(pose.rot)
                        };
                        if (!overlapsLocked(projected, piece, pieces)) {
                            best = {pose: pose, distance: distance};
                        }
                    });
            });
        });
        return best;
    }

    /** 松手后精确校正到最佳姿态并锁定。 */
    function trySnap(piece, level, pieces, basePx, onLocked, templates) {
        if (!piece || piece.locked || !templates) return false;
        var candidate = nearestCandidate(piece, level, pieces, basePx, templates);
        if (!candidate) return false;
        piece.x = candidate.pose.x;
        piece.y = candidate.pose.y;
        piece.rot = TangramGeometry.normRot(candidate.pose.rot);
        piece.locked = true;
        if (onLocked) onLocked(piece);
        return true;
    }

    /** 拖动预览只改变提示状态，不提前移动或锁定拼块。 */
    function previewSnap(piece, level, pieces, basePx, templates) {
        if (!piece || piece.locked || !templates) return false;
        piece.snapped = !!nearestCandidate(piece, level, pieces, basePx, templates);
        return piece.snapped;
    }

    /** 帮助点绘制使用：判断某个槽位是否已被同形锁定块占用。 */
    function slotTaken(pieces, placement, templates) {
        return pieces.some(function (piece) {
            return piece.locked &&
                TangramGeometry.sameShape(piece.id, placement.id) &&
                pieceCoversPlacement(piece, placement, templates, 2);
        });
    }

    return {
        lockedMatchSolution: lockedMatchSolution,
        poseToMatch: TangramGeometry.poseToMatch,
        posesToMatch: TangramGeometry.posesToMatch,
        previewSnap: previewSnap,
        sameShape: TangramGeometry.sameShape,
        snapDistance: TangramGeometry.snapDistance,
        solutionsOf: TangramSolutions.solutionsOf,
        slotTaken: slotTaken,
        trySnap: trySnap
    };
})();
