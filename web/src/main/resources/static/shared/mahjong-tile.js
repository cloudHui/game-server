/**
 * 麻将牌面渲染：优先图片，失败时用结构化花色+点数图案（避免只显示阿拉伯数字）。
 */
(function (w) {
  'use strict';

  var WAN = ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'];
  var FENG = ['', '东', '南', '西', '北'];
  var JIAN = ['', '中', '发', '白'];

  function suitOf(tileId) { return Math.floor(Number(tileId) / 100); }
  function valueOf(tileId) { return Number(tileId) % 100; }

  function getTileImagePath(tileId) {
    var suit = suitOf(tileId);
    var value = valueOf(tileId);
    var name = '';
    if (suit === 1) name = 'B_character_' + value + '.png';
    else if (suit === 2) name = 'B_bamboo_' + value + '.png';
    else if (suit === 3) name = 'B_dot_' + value + '.png';
    else if (suit === 4) name = 'B_wind_' + value + '.png';
    else if (suit === 5) name = 'B_wind_' + (value + 4) + '.png';
    else return '';
    return appUrl('/img/card/' + name);
  }

  function getTileName(tileId) {
    var suit = suitOf(tileId);
    var value = valueOf(tileId);
    if (suit === 4) return FENG[value] || String(tileId);
    if (suit === 5) return JIAN[value] || String(tileId);
    if (suit === 1) return (WAN[value] || value) + '万';
    if (suit === 2) return value + '条';
    if (suit === 3) return value + '筒';
    return String(tileId);
  }

  function buildFaceHtml(tileId) {
    var suit = suitOf(tileId);
    var value = valueOf(tileId);
    if (suit === 1) {
      return '<span class="mj-rank wan">' + (WAN[value] || value) + '</span>'
        + '<span class="mj-suit wan">万</span>';
    }
    if (suit === 2) {
      var sticks = '';
      for (var i = 0; i < value; i++) sticks += '<i class="mj-bamboo"></i>';
      return '<span class="mj-rank tiao">' + value + '</span>'
        + '<span class="mj-pattern bamboo">' + sticks + '</span>'
        + '<span class="mj-suit tiao">条</span>';
    }
    if (suit === 3) {
      var dots = '';
      for (var d = 0; d < value; d++) dots += '<i class="mj-dot"></i>';
      return '<span class="mj-rank tong">' + value + '</span>'
        + '<span class="mj-pattern dots">' + dots + '</span>'
        + '<span class="mj-suit tong">筒</span>';
    }
    if (suit === 4) {
      return '<span class="mj-honor feng">' + (FENG[value] || '') + '</span>';
    }
    if (suit === 5) {
      var cls = value === 1 ? 'zhong' : (value === 2 ? 'fa' : 'bai');
      return '<span class="mj-honor ' + cls + '">' + (JIAN[value] || '') + '</span>';
    }
    return '<span class="mj-rank">' + tileId + '</span>';
  }

  /**
   * 创建一张可见牌（含图片与结构化回退）。
   * @param {number} tileId
   * @param {{small?:boolean, selectable?:boolean, onClick?:Function}} opts
   */
  function createTileEl(tileId, opts) {
    opts = opts || {};
    var tile = document.createElement('div');
    tile.className = 'tile' + (opts.small ? ' tile-sm' : '');
    tile.dataset.tileId = String(tileId);

    var face = document.createElement('div');
    face.className = 'tile-face' + (opts.small ? ' small' : '');
    face.innerHTML = buildFaceHtml(tileId);

    var img = document.createElement('img');
    img.alt = getTileName(tileId);
    img.draggable = false;
    img.style.display = 'none';
    img.style.background = '#fffdf6';
    img.src = getTileImagePath(tileId);
    // 必须用 this，避免 for 循环 var 闭包导致只显示最后一张图、其余只剩数字
    img.onload = function () {
      this.style.display = 'block';
      if (this.previousSibling) this.previousSibling.style.display = 'none';
    };
    img.onerror = function () {
      this.style.display = 'none';
      if (this.previousSibling) this.previousSibling.style.display = 'flex';
    };

    tile.appendChild(face);
    tile.appendChild(img);
    if (opts.onClick) tile.onclick = opts.onClick;
    return tile;
  }

  w.MahjongTile = {
    suitOf: suitOf,
    valueOf: valueOf,
    getTileImagePath: getTileImagePath,
    getTileName: getTileName,
    createTileEl: createTileEl
  };
})(window);
