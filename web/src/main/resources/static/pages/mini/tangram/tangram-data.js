/**
 * 七巧板数据：标准拼块模板与题面（未缩放原点），以及缩放居中。
 */
var TangramData = (function () {
  'use strict';

  var SCALE = 1.2;

  var PIECES_BASE = [
    { id: 0, color: '#c62828', pts: [[0, 0], [220, 0], [110, 110]] },
    { id: 1, color: '#ef6c00', pts: [[0, 0], [110, 110], [0, 220]] },
    { id: 2, color: '#00838f', pts: [[0, 0], [0, 110], [-55, 55]] },
    { id: 3, color: '#7cb342', pts: [[0, 0], [55, -55], [110, 0], [55, 55]] },
    { id: 4, color: '#ad1457', pts: [[0, 0], [55, 55], [-55, 55]] },
    { id: 5, color: '#6a1b9a', pts: [[0, 0], [110, 0], [55, 55], [-55, 55]] },
    { id: 6, color: '#f9a825', pts: [[0, 0], [0, 110], [-110, 110]] }
  ];

  var LEVEL_DEFS = [
    {
      name: '正方形',
      /* 主解：大三角形斜边为 \ 对角线；副解：/ 对角线，两解外轮廓相同 */
      placements: [
        { id: 0, x: 360, y: 100, rot: 0 },
        { id: 1, x: 360, y: 100, rot: 0 },
        { id: 2, x: 580, y: 100, rot: 0 },
        { id: 3, x: 470, y: 210, rot: 0 },
        { id: 4, x: 470, y: 210, rot: 0 },
        { id: 5, x: 415, y: 265, rot: 0 },
        { id: 6, x: 580, y: 210, rot: 0 }
      ],
      solutions: [
        [
          { id: 0, x: 360, y: 100, rot: 0 },
          { id: 1, x: 360, y: 100, rot: 0 },
          { id: 2, x: 580, y: 100, rot: 0 },
          { id: 3, x: 470, y: 210, rot: 0 },
          { id: 4, x: 470, y: 210, rot: 0 },
          { id: 5, x: 415, y: 265, rot: 0 },
          { id: 6, x: 580, y: 210, rot: 0 }
        ],
        [
          { id: 0, x: 580, y: 100, rot: 90 },
          { id: 1, x: 580, y: 100, rot: 90 },
          { id: 2, x: 580, y: 320, rot: 90 },
          { id: 3, x: 470, y: 210, rot: 90 },
          { id: 4, x: 470, y: 210, rot: 90 },
          { id: 5, x: 415, y: 155, rot: 90 },
          { id: 6, x: 470, y: 320, rot: 90 }
        ]
      ]
    },
    {
      name: '飞船',
      placements: [
        { id: 0, x: 355, y: 135, rot: 0 },
        { id: 1, x: 520, y: 190, rot: 270 },
        { id: 2, x: 465, y: 245, rot: 90 },
        { id: 3, x: 300, y: 190, rot: 0 },
        { id: 4, x: 454, y: 80, rot: 0 },
        { id: 5, x: 520, y: 190, rot: 0 },
        { id: 6, x: 465, y: 245, rot: 270 }
      ]
    }
  ];

  function rotPoint(x, y, deg) {
    var r = deg * Math.PI / 180;
    var c = Math.cos(r);
    var s = Math.sin(r);
    return [x * c - y * s, x * s + y * c];
  }

  function scaledTemplates() {
    return PIECES_BASE.map(function (p) {
      return {
        id: p.id,
        color: p.color,
        pts: p.pts.map(function (pt) { return [pt[0] * SCALE, pt[1] * SCALE]; })
      };
    });
  }

  function rawSilhouetteBounds(placements) {
    var xs = [];
    var ys = [];
    var i;
    var j;
    var pl;
    var pt;
    var r;
    for (i = 0; i < placements.length; i++) {
      pl = placements[i];
      for (j = 0; j < PIECES_BASE[pl.id].pts.length; j++) {
        pt = PIECES_BASE[pl.id].pts[j];
        r = rotPoint(pt[0], pt[1], pl.rot);
        xs.push(r[0] + pl.x);
        ys.push(r[1] + pl.y);
      }
    }
    return {
      minX: Math.min.apply(null, xs),
      minY: Math.min.apply(null, ys),
      maxX: Math.max.apply(null, xs),
      maxY: Math.max.apply(null, ys)
    };
  }

  function scalePlacement(pl, ocx, ocy, ncx, ncy) {
    return {
      id: pl.id,
      rot: pl.rot,
      x: ncx + (pl.x - ocx) * SCALE,
      y: ncy + (pl.y - ocy) * SCALE
    };
  }

  function scaleAndCenterLevel(def, canvasW, canvasH) {
    var b = rawSilhouetteBounds(def.placements);
    var ocx = (b.minX + b.maxX) / 2;
    var ocy = (b.minY + b.maxY) / 2;
    var ncx = canvasW / 2;
    var ncy = canvasH / 2;
    var placements = def.placements.map(function (pl) {
      return scalePlacement(pl, ocx, ocy, ncx, ncy);
    });
    var solutions = null;
    if (def.solutions && def.solutions.length) {
      solutions = def.solutions.map(function (sol) {
        return sol.map(function (pl) {
          return scalePlacement(pl, ocx, ocy, ncx, ncy);
        });
      });
    }
    return {
      name: def.name,
      placements: placements,
      solutions: solutions
    };
  }

  function buildLevels(canvasW, canvasH) {
    var defs = LEVEL_DEFS.slice().concat(TANGRAM_GENERATED_LEVELS);
    return defs.map(function (def) {
      return scaleAndCenterLevel(def, canvasW, canvasH);
    });
  }

  return {
    SCALE: SCALE,
    SNAP_PX: 30,
    PAD: 10,
    rotPoint: rotPoint,
    scaledTemplates: scaledTemplates,
    buildLevels: buildLevels
  };
})();
