/**
 * 麻将副露/弃牌认领：解析、渲染、来源标注（对局与结算共用）。
 * 依赖全局 MahjongTile / gameState / OP。
 */

function countExposedTiles(seat) {
  var sets = gameState.exposedBySeat[seat] || [];
  var n = 0;
  for (var i = 0; i < sets.length; i++) n += (sets[i].tiles || []).length;
  return n;
}

/** 解析 type 或 type@fromSeat */
function parseExposedType(typeStr) {
  var s = String(typeStr || '');
  var at = s.indexOf('@');
  if (at < 0) return { kind: s, fromSeat: -1 };
  var from = parseInt(s.slice(at + 1), 10);
  return { kind: s.slice(0, at), fromSeat: isNaN(from) ? -1 : from };
}

/** 按当前玩家看到的桌面方位，计算牌组所有者指向来源玩家的箭头。 */
function exposedSourceArrow(ownerSeat, fromSeat) {
  if (fromSeat < 0 || ownerSeat === fromSeat) return '';
  var seatNum = gameState.seatNum || 4;
  function point(seat) {
    if (seat === gameState.myPosition) return { x: 0, y: 1 };
    var rel = (seat - gameState.myPosition + seatNum) % seatNum;
    var slot = seatSlot(rel, seatNum);
    if (slot === 'left') return { x: -1, y: 0 };
    if (slot === 'right') return { x: 1, y: 0 };
    return { x: 0, y: -1 };
  }
  var owner = point(ownerSeat);
  var source = point(fromSeat);
  var dx = source.x - owner.x;
  var dy = source.y - owner.y;
  if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? '→' : '←';
  return dy > 0 ? '↓' : '↑';
}

function markClaimSource(tile, ownerSeat, fromSeat) {
  if (!tile || fromSeat < 0) return;
  tile.className += ' from-claim';
  tile.dataset.sourceArrow = exposedSourceArrow(ownerSeat, fromSeat);
}

function findClaimTileIndex(tiles, claimTile) {
  if (claimTile == null) return 0;
  var idx = tiles.indexOf(claimTile);
  return idx >= 0 ? idx : 0;
}

/**
 * 渲染一组副露。
 * @param {{kind?:string,type?:string,tiles?:number[],tileIds?:number[],fromSeat?:number,claimTile?:number}} set
 * @param {{revealAnGang?:boolean, ownerSeat?:number}} opt
 */
function appendExposedSet(container, set, opt) {
  opt = opt || {};
  var parsed = parseExposedType(set.type || set.kind || '');
  var kind = set.kind || parsed.kind;
  var fromSeat = set.fromSeat != null ? set.fromSeat : parsed.fromSeat;
  var ownerSeat = opt.ownerSeat != null ? opt.ownerSeat : gameState.myPosition;
  var group = document.createElement('div');
  var isGang = kind === 'anGang' || kind === 'mingGang' || kind === 'buGang';
  group.className = 'exposed-set' + (kind === 'anGang' ? ' an-gang' : '')
      + (isGang ? ' gang-stack' : '');
  var tiles = set.tiles || set.tileIds || [];
  var markIdx = kind === 'chi' ? findClaimTileIndex(tiles, set.claimTile) : 0;
  if (isGang) {
    var base = document.createElement('div');
    base.className = 'gang-base';
    for (var g = 0; g < 3; g++) {
      if (kind === 'anGang') {
        var hidden = document.createElement('div');
        hidden.className = 'tile-back';
        base.appendChild(hidden);
      } else {
        var baseTile = MahjongTile.createTileEl(tiles[g] || tiles[0], { small: true });
        if (kind === 'buGang' && g === 1) markClaimSource(baseTile, ownerSeat, fromSeat);
        base.appendChild(baseTile);
      }
    }
    group.appendChild(base);
    var topTile;
    if (kind === 'anGang' && !opt.revealAnGang) {
      topTile = document.createElement('div');
      topTile.className = 'tile-back';
    } else {
      topTile = MahjongTile.createTileEl(tiles[3] || tiles[0], { small: true });
    }
    topTile.className += ' gang-top';
    if (kind === 'mingGang') markClaimSource(topTile, ownerSeat, fromSeat);
    group.appendChild(topTile);
  } else {
    for (var j = 0; j < tiles.length; j++) {
      var tile = MahjongTile.createTileEl(tiles[j], { small: true });
      if (fromSeat >= 0 && j === markIdx) markClaimSource(tile, ownerSeat, fromSeat);
      group.appendChild(tile);
    }
  }
  container.appendChild(group);
}

function resolveGangKind(seat, tileId) {
  var sets = gameState.exposedBySeat[seat] || [];
  for (var i = 0; i < sets.length; i++) {
    if (sets[i].kind === 'peng' && sets[i].tiles && sets[i].tiles[0] === tileId) {
      return 'buGang';
    }
  }
  if (gameState.lastDiscardTile === tileId && gameState.lastDiscardSeat >= 0
      && gameState.lastDiscardSeat !== seat) {
    return 'mingGang';
  }
  return 'anGang';
}

function findPengFromSeat(seat, tileId) {
  var sets = gameState.exposedBySeat[seat] || [];
  for (var i = 0; i < sets.length; i++) {
    if (sets[i].kind === 'peng' && sets[i].tiles && sets[i].tiles[0] === tileId) {
      return sets[i].fromSeat != null ? sets[i].fromSeat : -1;
    }
  }
  return -1;
}

/** 根据动作推断副露种类与来源座位 */
function buildExposedRecord(seat, action, tileId) {
  var tiles = [];
  var kind = '';
  var fromSeat = -1;
  if (action === OP.MJ_PENG) {
    kind = 'peng';
    tiles = [tileId, tileId, tileId];
    fromSeat = gameState.lastDiscardSeat;
  } else if (action === OP.MJ_CHI) {
    kind = 'chi';
    tiles = gameState._lastChiTiles || [tileId];
    gameState._lastChiTiles = null;
    fromSeat = gameState.lastDiscardSeat;
  } else if (action === OP.MJ_GANG) {
    kind = resolveGangKind(seat, tileId);
    tiles = [tileId, tileId, tileId, tileId];
    if (kind === 'anGang') fromSeat = -1;
    else if (kind === 'buGang') fromSeat = findPengFromSeat(seat, tileId);
    else fromSeat = gameState.lastDiscardSeat;
  } else {
    return null;
  }
  return { kind: kind, tiles: tiles, fromSeat: fromSeat, claimTile: tileId };
}

/** 吃碰明杠胡时，只移除刚打出的那张弃牌并释放落点 */
function removeClaimedDiscard(tileId) {
  if (!tileId) return;
  var n = gameState.discardedTiles.length;
  var sourceSeat = gameState.lastDiscardSeat;
  if (n > 0 && gameState.discardedTiles[n - 1] === tileId
      && (!gameState.discardSeats || gameState.discardSeats[n - 1] === sourceSeat)) {
    gameState.discardedTiles.pop();
    if (gameState.discardSeats) gameState.discardSeats.pop();
    if (gameState.discardLayouts && gameState.discardLayouts.length) {
      gameState.discardLayouts.pop();
    }
  } else {
    var idx = -1;
    for (var i = n - 1; i >= 0; i--) {
      if (gameState.discardedTiles[i] === tileId
          && (!gameState.discardSeats || gameState.discardSeats[i] === sourceSeat)) {
        idx = i;
        break;
      }
    }
    if (idx >= 0) {
      gameState.discardedTiles.splice(idx, 1);
      if (gameState.discardSeats) gameState.discardSeats.splice(idx, 1);
      if (gameState.discardLayouts && gameState.discardLayouts.length > idx) {
        gameState.discardLayouts.splice(idx, 1);
      }
    }
  }
  gameState.lastDiscardTile = 0;
  gameState.lastDiscardSeat = -1;
  renderDiscarded();
}

function shouldRemoveDiscardForAction(action, opSeat, tileId) {
  if (!tileId || gameState.lastDiscardTile !== tileId) return false;
  if (action === OP.MJ_CHI || action === OP.MJ_PENG || action === OP.MJ_HU) return true;
  if (action === OP.MJ_GANG) {
    return gameState.lastDiscardSeat >= 0 && gameState.lastDiscardSeat !== opSeat;
  }
  return false;
}

/** 补杠：把原碰升级，返回是否已升级 */
function upgradePengToBuGang(seat, tileId) {
  var sets = gameState.exposedBySeat[seat];
  if (!sets) return false;
  for (var i = 0; i < sets.length; i++) {
    if (sets[i].kind === 'peng' && sets[i].tiles && sets[i].tiles[0] === tileId) {
      sets[i].kind = 'buGang';
      sets[i].tiles = [tileId, tileId, tileId, tileId];
      if (seat === gameState.myPosition) renderMyExposed();
      else refreshOpponentBacks();
      return true;
    }
  }
  return false;
}

function resolveGangKindAfter(seat, tileId) {
  var sets = gameState.exposedBySeat[seat] || [];
  for (var i = 0; i < sets.length; i++) {
    if (sets[i].tiles && sets[i].tiles[0] === tileId) {
      if (sets[i].kind === 'buGang') return 'buGang';
      if (sets[i].kind === 'anGang') return 'anGang';
      if (sets[i].kind === 'mingGang') return 'mingGang';
    }
  }
  return 'mingGang';
}
