/**
 * 七巧板几何等价答案扩展。
 *
 * 题库仍保存一套清晰的标准分割；运行时只在两个全等直角三角形
 * 恰好组成正方形时，补出另一条对角线。这样目标外轮廓不变，
 * 玩家使用 “/” 或 “\” 分割都能得到同样有效的答案。
 */
var TangramSolutions = (function () {
  'use strict';

  var CACHE = new WeakMap();

  function pointNear(a, b) {
    return Math.hypot(a[0] - b[0], a[1] - b[1]) <= 2;
  }

  function uniquePoints(polygons) {
    var result = [];
    polygons.forEach(function (polygon) {
      polygon.forEach(function (point) {
        for (var i = 0; i < result.length; i++) {
          if (pointNear(result[i], point)) return;
        }
        result.push(point);
      });
    });
    return result;
  }

  /**
   * 两个三角形必须共享两个顶点，合并后只有四个外角；
   * 四边等长且共享边连接相对角时，它们的并集才按正方形处理。
   */
  function squareCorners(a, b) {
    var common = [];
    a.forEach(function (pa) {
      b.forEach(function (pb) {
        if (pointNear(pa, pb)) common.push(pa);
      });
    });
    var corners = uniquePoints([a, b]);
    if (common.length !== 2 || corners.length !== 4) return null;

    var center = [
      corners.reduce(function (sum, p) { return sum + p[0]; }, 0) / 4,
      corners.reduce(function (sum, p) { return sum + p[1]; }, 0) / 4
    ];
    corners.sort(function (p, q) {
      return Math.atan2(p[1] - center[1], p[0] - center[0]) -
        Math.atan2(q[1] - center[1], q[0] - center[0]);
    });

    var sides = corners.map(function (point, i) {
      var next = corners[(i + 1) % 4];
      return Math.hypot(point[0] - next[0], point[1] - next[1]);
    });
    if (Math.min.apply(null, sides) < 1 ||
        Math.max.apply(null, sides) - Math.min.apply(null, sides) > 2) return null;

    var diagonal = [];
    corners.forEach(function (corner, i) {
      if (pointNear(corner, common[0]) || pointNear(corner, common[1])) diagonal.push(i);
    });
    return diagonal.length === 2 && Math.abs(diagonal[0] - diagonal[1]) === 2
      ? { corners: corners, diagonal: diagonal } : null;
  }

  function flipTrianglePair(solution, first, second, templates) {
    function polygonAt(index) {
      var placement = solution[index];
      return TangramGeometry.worldOf(templates[placement.id].pts,
        placement.x, placement.y, placement.rot);
    }

    var square = squareCorners(polygonAt(first), polygonAt(second));
    if (!square) return null;
    var otherDiagonal = [0, 1, 2, 3].filter(function (i) {
      return square.diagonal.indexOf(i) < 0;
    });
    var targets = square.diagonal.map(function (oldCorner) {
      return [
        square.corners[otherDiagonal[0]],
        square.corners[otherDiagonal[1]],
        square.corners[oldCorner]
      ];
    });
    var poses = [
      TangramGeometry.posesToPolygon(solution[first].id, targets[0], templates)[0],
      TangramGeometry.posesToPolygon(solution[second].id, targets[1], templates)[0]
    ];
    if (!poses[0] || !poses[1]) return null;

    var flipped = solution.map(function (placement) {
      return {
        id: placement.id, x: placement.x, y: placement.y, rot: placement.rot
      };
    });
    [first, second].forEach(function (index, i) {
      flipped[index] = {
        id: solution[index].id,
        x: poses[i].x,
        y: poses[i].y,
        rot: poses[i].rot
      };
    });
    return flipped;
  }

  function solutionKey(solution) {
    return solution.map(function (placement) {
      return placement.id + ':' + Math.round(placement.x * 10) + ':' +
        Math.round(placement.y * 10) + ':' + TangramGeometry.normRot(placement.rot);
    }).join('|');
  }

  function solutionsOf(level, templates) {
    var base = level.solutions && level.solutions.length
      ? level.solutions : [level.placements];
    if (!templates) return base;
    if (CACHE.has(level)) return CACHE.get(level);

    var result = base.slice();
    var seen = {};
    result.forEach(function (solution) { seen[solutionKey(solution)] = true; });

    /* 继续扫描新答案，使同一题中的两组独立三角形可以分别翻转。 */
    for (var cursor = 0; cursor < result.length && result.length < 32; cursor++) {
      var solution = result[cursor];
      for (var i = 0; i < solution.length; i++) {
        for (var j = i + 1; j < solution.length; j++) {
          var group = TangramGeometry.shapeOf(solution[i].id);
          if ((group !== 'large' && group !== 'small') ||
              group !== TangramGeometry.shapeOf(solution[j].id)) continue;
          var flipped = flipTrianglePair(solution, i, j, templates);
          var key = flipped && solutionKey(flipped);
          if (flipped && !seen[key]) {
            seen[key] = true;
            result.push(flipped);
          }
        }
      }
    }
    CACHE.set(level, result);
    return result;
  }

  return { solutionsOf: solutionsOf };
})();
