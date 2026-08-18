#!/usr/bin/env node
'use strict';

var fs = require('fs');
var vm = require('vm');
var path = require('path');
var root = path.resolve(__dirname, '..');
var tangramDir = path.join(root,
  'web/src/main/resources/static/pages/mini/tangram');

[
  'tangram-levels.js',
  'tangram-poster-levels.js',
  'tangram-data.js',
  'tangram-view.js',
  'tangram-layout.js',
  'tangram-geometry.js',
  'tangram-solutions.js',
  'tangram-snap.js'
].forEach(function (file) {
  vm.runInThisContext(fs.readFileSync(path.join(tangramDir, file), 'utf8'), {
    filename: file
  });
});

var canvas = { width: 1080, height: 760 };
var templates = TangramData.scaledTemplates();
var levels = TangramData.buildLevels(canvas.width, canvas.height);
var errors = [];

function expect(condition, message) {
  if (!condition) errors.push(message);
}

function pieceOf(pl) {
  return {
    id: pl.id,
    pts: templates[pl.id].pts,
    x: pl.x,
    y: pl.y,
    rot: pl.rot,
    locked: false
  };
}

function boundsOf(placements) {
  var boxes = placements.map(function (pl) {
    return TangramView.aabbOf(pieceOf(pl));
  });
  return {
    minX: Math.min.apply(null, boxes.map(function (b) { return b.minX; })),
    minY: Math.min.apply(null, boxes.map(function (b) { return b.minY; })),
    maxX: Math.max.apply(null, boxes.map(function (b) { return b.maxX; })),
    maxY: Math.max.apply(null, boxes.map(function (b) { return b.maxY; }))
  };
}

function report(level, message) {
  errors.push((levels.indexOf(level) + 1) + '「' + level.name + '」: ' + message);
}

function sharedEdgeLength(a, b) {
  var pa = TangramView.worldPts(a);
  var pb = TangramView.worldPts(b);
  var total = 0;
  pa.forEach(function (a1, ai) {
    var a2 = pa[(ai + 1) % pa.length];
    var adx = a2[0] - a1[0];
    var ady = a2[1] - a1[1];
    var alen = Math.hypot(adx, ady);
    var ux = adx / alen;
    var uy = ady / alen;
    pb.forEach(function (b1, bi) {
      var b2 = pb[(bi + 1) % pb.length];
      var bdx = b2[0] - b1[0];
      var bdy = b2[1] - b1[1];
      if (Math.abs(adx * bdy - ady * bdx) > alen * Math.hypot(bdx, bdy) * 1e-6) return;
      if (Math.abs((b1[0] - a1[0]) * uy - (b1[1] - a1[1]) * ux) > 0.2) return;
      var bStart = (b1[0] - a1[0]) * ux + (b1[1] - a1[1]) * uy;
      var bEnd = (b2[0] - a1[0]) * ux + (b2[1] - a1[1]) * uy;
      total += Math.max(0, Math.min(alen, Math.max(bStart, bEnd)) -
        Math.max(0, Math.min(bStart, bEnd)));
    });
  });
  return total;
}

if (levels.length !== 53) {
  errors.push('题目总数应为 53，实际为 ' + levels.length);
}

var posterLevels = levels.filter(function (level) {
  return level.source === 'poster';
});
expect(posterLevels.length === 1, '海报新题应为 1，实际为 ' + posterLevels.length);
posterLevels.forEach(function (level) {
  expect(level.name.indexOf('新·') === 0, '海报题名应以「新·」开头: ' + level.name);
});

levels.forEach(function (level) {
  var ids = level.placements.map(function (pl) { return pl.id; }).sort();
  if (ids.join(',') !== '0,1,2,3,4,5,6') {
    report(level, '必须且只能包含 0-6 共七块');
  }

  var targets = level.placements.map(pieceOf);
  var i;
  var j;
  for (i = 0; i < targets.length; i++) {
    if (targets[i].rot % 45 !== 0) report(level, '存在非 45° 整数倍方向');
    for (j = i + 1; j < targets.length; j++) {
      if (TangramView.polygonsOverlap(targets[i], targets[j])) {
        report(level, '目标拼块 ' + targets[i].id + ' 与 ' + targets[j].id + ' 面积重叠');
      }
    }
  }
  var reached = { 0: true };
  var changed = true;
  while (changed) {
    changed = false;
    for (i = 0; i < targets.length; i++) {
      if (!reached[i]) continue;
      for (j = 0; j < targets.length; j++) {
        if (!reached[j] && sharedEdgeLength(targets[i], targets[j]) > 0.2) {
          reached[j] = true;
          changed = true;
        }
      }
    }
  }
  if (Object.keys(reached).length !== targets.length) {
    report(level, '目标不是由共边连接的完整图形');
  }

  var scattered = TangramLayout.clonePieces(
    templates, boundsOf(level.placements), canvas, TangramData.PAD,
    level.placements, false
  );
  for (i = 0; i < scattered.length; i++) {
    var box = TangramView.aabbOf(scattered[i]);
    if (box.minX < TangramData.PAD || box.minY < TangramData.PAD ||
        box.maxX > canvas.width - TangramData.PAD ||
        box.maxY > canvas.height - TangramData.PAD) {
      report(level, '初始拼块 ' + scattered[i].id + ' 超出画布');
    }
    for (j = i + 1; j < scattered.length; j++) {
      if (TangramView.polygonsOverlap(scattered[i], scattered[j])) {
        report(level, '初始拼块 ' + scattered[i].id + ' 与 ' + scattered[j].id + ' 重叠');
      }
    }
    for (j = 0; j < targets.length; j++) {
      if (TangramView.polygonsOverlap(scattered[i], targets[j])) {
        report(level, '初始拼块 ' + scattered[i].id + ' 压住目标底图');
      }
    }
  }

  var solving = templates.map(function (template) {
    return {
      id: template.id,
      pts: template.pts,
      x: -1000,
      y: -1000,
      rot: 0,
      locked: false
    };
  });
  level.placements.forEach(function (pl) {
    var piece = solving[pl.id];
    var pose = TangramSnap.poseToMatch(piece.id, pl, templates);
    piece.x = pose.x;
    piece.y = pose.y;
    piece.rot = pose.rot;
    if (!TangramSnap.trySnap(piece, level, solving, TangramData.SNAP_PX,
        null, templates)) {
      report(level, '标准答案中的拼块 ' + piece.id + ' 无法吸附');
    }
  });

  TangramSnap.solutionsOf(level, templates).forEach(function (solution, solutionIndex) {
    var alternateSolving = templates.map(function (template) {
      return {
        id: template.id,
        pts: template.pts,
        x: -1000,
        y: -1000,
        rot: 0,
        locked: false
      };
    });
    solution.forEach(function (pl) {
      var piece = alternateSolving[pl.id];
      var pose = TangramSnap.poseToMatch(piece.id, pl, templates);
      piece.x = pose.x;
      piece.y = pose.y;
      piece.rot = pose.rot;
      if (!TangramSnap.trySnap(piece, level, alternateSolving,
          TangramData.SNAP_PX, null, templates)) {
        report(level, '几何等价答案 ' + (solutionIndex + 1) +
          ' 中的拼块 ' + piece.id + ' 无法吸附');
      }
    });
  });
});

var squarePlacement = levels[0].placements.filter(function (pl) {
  return pl.id === 3;
})[0];
var squarePoses = TangramSnap.posesToMatch(3, squarePlacement, templates);
expect(squarePoses.map(function (pose) { return pose.rot; }).join(',') === '0,90,180,270',
  '正方形应把每旋转 90° 的姿态视为几何等价');

var paraPlacement = levels[0].placements.filter(function (pl) {
  return pl.id === 5;
})[0];
var paraPoses = TangramSnap.posesToMatch(5, paraPlacement, templates);
expect(paraPoses.map(function (pose) { return pose.rot; }).join(',') === '0,180',
  '平行四边形应把相差 180° 的姿态视为几何等价');

templates.forEach(function (template) {
  var distance = TangramSnap.snapDistance(template, TangramData.SNAP_PX);
  expect(distance >= TangramData.SNAP_PX,
    '拼块 ' + template.id + ' 的吸附距离不应小于统一基础距离');
});

var equivalentPose = squarePoses[1];
var equivalentSquare = {
  id: 3,
  pts: templates[3].pts,
  x: equivalentPose.x,
  y: equivalentPose.y,
  rot: equivalentPose.rot,
  locked: false
};
expect(TangramSnap.trySnap(equivalentSquare, { placements: [squarePlacement] },
  [equivalentSquare], TangramData.SNAP_PX, null, templates),
  '正方形处于视觉等价的 90° 姿态时应能吸附');

var nearPose = squarePoses[0];
var nearDistance = TangramSnap.snapDistance(templates[3], TangramData.SNAP_PX);
expect(TangramSnap.snapDistance(templates[3], 100) === 100,
  '窄屏换算后的吸附距离不应被 45px 上限截断');
var nearSquare = {
  id: 3,
  pts: templates[3].pts,
  x: nearPose.x + nearDistance - 0.5,
  y: nearPose.y,
  rot: nearPose.rot,
  locked: false
};
expect(TangramSnap.trySnap(nearSquare, { placements: [squarePlacement] },
  [nearSquare], TangramData.SNAP_PX, null, templates),
  '拼块中心进入自适应距离时应能吸附');

var farSquare = {
  id: 3,
  pts: templates[3].pts,
  x: nearPose.x + nearDistance + 1,
  y: nearPose.y,
  rot: nearPose.rot,
  locked: false
};
expect(!TangramSnap.trySnap(farSquare, { placements: [squarePlacement] },
  [farSquare], TangramData.SNAP_PX, null, templates),
  '拼块中心超出自适应距离时不应吸附');

var nearestLevel = {
  placements: [
    { id: 3, x: 100, y: 100, rot: 0 },
    { id: 3, x: 160, y: 100, rot: 0 }
  ]
};
var nearestSquare = {
  id: 3,
  pts: templates[3].pts,
  x: 135,
  y: 100,
  rot: 0,
  locked: false
};
expect(TangramSnap.trySnap(nearestSquare, nearestLevel, [nearestSquare],
  TangramData.SNAP_PX, null, templates) && Math.abs(nearestSquare.x - 160) < 0.1,
  '多个槽位都在范围内时应吸附到中心最近的槽位');

var alternateDiagonalLevels = levels.filter(function (level) {
  var baseCount = level.solutions && level.solutions.length ? level.solutions.length : 1;
  return TangramSnap.solutionsOf(level, templates).length > baseCount;
});
expect(alternateDiagonalLevels.length > 0,
  '题库中的全等三角形正方形应生成另一条对角线答案');

if (errors.length) {
  process.stderr.write(errors.join('\n') + '\n');
  process.exit(1);
}
process.stdout.write('七巧板校验通过：' + levels.length +
  ' 题均为七块、无重叠、可散列且可完整吸附（含海报新题 ' +
  posterLevels.length + '）。\n');
