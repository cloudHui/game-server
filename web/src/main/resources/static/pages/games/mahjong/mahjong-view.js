/**
 * 麻将牌桌渲染：手牌/副露/弃牌/座位/操作栏。
 * 依赖页面全局 gameState / OP / userId / MahjongTile / GameTable / GameLandscape。
 */
function getTileName(tileId) { return MahjongTile.getTileName(tileId); }

/** 手牌排序：花色升序，同花色点数小的在左 */
function sortHandTiles(tiles) {
    tiles.sort(function (a, b) {
        var sa = Math.floor(a / 100), sb = Math.floor(b / 100);
        return sa - sb || (a % 100) - (b % 100);
    });
    return tiles;
}

/** 单排手牌适配：四人竖屏也强制横屏，再按可用宽度缩放牌面（约为缩小后的 2 倍） */
function layoutMyHand() {
    var row = document.getElementById('myTiles');
    if (!row || !window.GameLandscape) return;
    var n = Math.max(gameState.myTiles.length, 1);
    var exposedN = countExposedTiles(gameState.myPosition);
    var gap = 3;
    var drawnGap = gameState.drawnTileId ? Math.max(6, Math.round(gap + 8)) : 0;
    var exposedGap = exposedN > 0 ? 20 : 0;
    var pad = 16;
    var comfortW = 32;
    var minNeed = n * comfortW + (n - 1) * gap + drawnGap + pad
        + exposedN * Math.round(comfortW * 0.72) + exposedGap;
    if ((gameState.seatNum || 4) >= 4 && GameLandscape.isPortrait()) {
        minNeed = Math.max(minNeed, GameLandscape.gameSize().w + 1);
    }
    var size = GameLandscape.ensureFits(minNeed);
    var avail = Math.max(100, size.w - pad - drawnGap - exposedGap
        - exposedN * Math.round(comfortW * 0.72));
    var tileW = Math.floor((avail - (n - 1) * gap) / n);
    tileW = Math.max(24, Math.min(36, tileW));
    var tileH = Math.round(tileW * 1.4);
    var face = Math.max(12, Math.round(tileW * 0.42));
    row.style.setProperty('--mj-tile-w', tileW + 'px');
    row.style.setProperty('--mj-tile-h', tileH + 'px');
    row.style.setProperty('--mj-gap', gap + 'px');
    row.style.setProperty('--mj-drawn-gap', Math.max(Math.round(tileW / 3), 6) + 'px');
    row.style.setProperty('--mj-face', face + 'px');
    var handRow = document.getElementById('myHandRow');
    if (handRow) {
        handRow.style.setProperty('--mj-tile-w', tileW + 'px');
        handRow.style.setProperty('--mj-tile-h', tileH + 'px');
    }
    var bar = document.getElementById('actionBar');
    if (bar) bar.style.bottom = (tileH + 72) + 'px';
}

function renderMyTiles(opt) {
    opt = opt || {};
    var container = document.getElementById('myTiles');
    if (opt.flashDrawn && tryAppendDrawnTile(container)) {
        layoutMyHand();
        return;
    }
    var frag = document.createDocumentFragment();
    var drawnId = gameState.drawnTileId;
    for (var i = 0; i < gameState.myTiles.length; i++) {
        (function (idx, tileId) {
            var isDrawn = drawnId && idx === gameState.myTiles.length - 1 && tileId === drawnId;
            var tile = MahjongTile.createTileEl(tileId, {
                onClick: function () { toggleTile(idx); }
            });
            if (gameState.selectedTile === idx) tile.className += ' selected';
            if (isDrawn) {
                tile.className += ' tile-drawn';
                if (opt.flashDrawn) tile.className += ' tile-flash';
            }
            tile.style.zIndex = String(idx + 1);
            frag.appendChild(tile);
        })(i, gameState.myTiles[i]);
    }
    container.innerHTML = '';
    container.appendChild(frag);
    layoutMyHand();
}

/** 摸牌快路径：手牌主体 DOM 不动，只追加最右隔离牌并闪一下 */
function tryAppendDrawnTile(container) {
    var tiles = gameState.myTiles;
    if (!gameState.drawnTileId || tiles.length < 2) return false;
    var kids = container.children;
    var bodyLen = tiles.length - 1;
    if (kids.length !== bodyLen && kids.length !== tiles.length) return false;
    for (var i = 0; i < bodyLen; i++) {
        if (String(kids[i].dataset.tileId) !== String(tiles[i])) return false;
    }
    // 去掉旧的摸牌节点
    while (container.children.length > bodyLen) {
        container.removeChild(container.lastChild);
    }
    Array.prototype.forEach.call(container.children, function (el) {
        el.classList.remove('tile-drawn', 'tile-flash', 'selected');
    });
    var idx = tiles.length - 1;
    var tile = MahjongTile.createTileEl(tiles[idx], {
        onClick: function () { toggleTile(idx); }
    });
    tile.className += ' tile-drawn tile-flash';
    if (gameState.selectedTile === idx) tile.className += ' selected';
    tile.style.zIndex = String(idx + 1);
    container.appendChild(tile);
    return true;
}

function renderMyExposed() {
    var box = document.getElementById('myExposed');
    if (!box) return;
    box.innerHTML = '';
    var sets = gameState.exposedBySeat[gameState.myPosition] || [];
    for (var i = 0; i < sets.length; i++) {
        appendExposedSet(box, sets[i], { ownerSeat: gameState.myPosition, revealAnGang: false });
    }
    layoutMyHand();
}

function toggleTile(index) {
    if (gameState.selectedTile === index) {
        doOp(OP.DISCARD);
    } else {
        gameState.selectedTile = index;
        renderMyTiles();
    }
}

function renderCardBacks(containerId, count) {
    var container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';
    var frag = document.createDocumentFragment();
    for (var i = 0; i < count; i++) {
        var back = document.createElement('div');
        back.className = 'tile-back';
        frag.appendChild(back);
    }
    container.appendChild(frag);
}

/**
 * 弃牌落在中央框内：left/top 用容器百分比，旋转后仍留边距，不盖玩家手牌区。
 * x/y 为相对框中心的偏移百分比（非牌面自身百分比）。
 */
function renderDiscarded() {
    var container = document.getElementById('discardedArea');
    if (!container) return;
    var frag = document.createDocumentFragment();
    var n = gameState.discardedTiles.length;
    if (!gameState.discardLayouts) gameState.discardLayouts = [];
    while (gameState.discardLayouts.length < n) {
        gameState.discardLayouts.push(nextDiscardLayout(gameState.discardLayouts));
    }
    if (gameState.discardLayouts.length > n) {
        gameState.discardLayouts.length = n;
    }
    for (var i = 0; i < n; i++) {
        var tile = MahjongTile.createTileEl(gameState.discardedTiles[i], { small: true });
        var lay = gameState.discardLayouts[i];
        tile.style.position = 'absolute';
        tile.style.left = (50 + lay.x) + '%';
        tile.style.top = (50 + lay.y) + '%';
        tile.style.transform = 'translate(-50%, -50%) rotate(' + lay.rot + 'deg)';
        if (i === n - 1) tile.className += ' tile-latest';
        frag.appendChild(tile);
    }
    container.innerHTML = '';
    container.appendChild(frag);
}

/** 弃牌安全散落：半宽/半高预留旋转外扩，网格回退保证不超框 */
function nextDiscardLayout(existing) {
    var tw = 9;
    var th = 13;
    var gap = 1.2;
    var maxX = 38;
    var maxY = 32;
    var i;
    for (i = 0; i < 56; i++) {
        var cand = {
            x: (Math.random() * 2 - 1) * maxX,
            y: (Math.random() * 2 - 1) * maxY,
            rot: (Math.random() * 2 - 1) * 22
        };
        if (!discardOverlaps(cand, existing, tw + gap, th + gap)) return cand;
    }
    var idx = existing.length;
    var cols = 7;
    var rows = 5;
    var col = idx % cols;
    var row = Math.floor(idx / cols) % rows;
    return {
        x: (col - (cols - 1) / 2) * (tw + 0.8),
        y: (row - (rows - 1) / 2) * (th + 0.6),
        rot: ((idx % 5) - 2) * 6
    };
}

function discardOverlaps(cand, existing, minDx, minDy) {
    for (var i = 0; i < existing.length; i++) {
        var o = existing[i];
        if (Math.abs(cand.x - o.x) < minDx && Math.abs(cand.y - o.y) < minDy) return true;
    }
    return false;
}

/** 相对座位 → 界面槽位：2人对面；3人右/左；4人右/对/左 */
function seatSlot(relPos, seatNum) {
    if (seatNum <= 2) return 'top';
    if (seatNum === 3) return relPos === 1 ? 'right' : 'left';
    if (relPos === 1) return 'right';
    if (relPos === 2) return 'top';
    return 'left';
}

function clearSeatSlots() {
    ['Top', 'Left', 'Right'].forEach(function (s) {
        document.getElementById('player' + s).innerHTML = '<span class="name">等待加入</span>';
        document.getElementById('tiles' + s).innerHTML = '';
        var exp = document.getElementById('exposed' + s);
        if (exp) exp.innerHTML = '';
    });
}

function updatePlayers(players) {
    if (!players) return;
    gameState.players = players;
    gameState.seatNum = resolveSeatNum(gameState.roomId);
    clearSeatSlots();
    for (var i = 0; i < players.length; i++) {
        if (players[i].roleId === userId) {
            gameState.myPosition = players[i].position;
            document.getElementById('myName').textContent =
                (nickname || '我') + scoreText(players[i].totalScore);
            break;
        }
    }
    refreshOpponentBacks();
    layoutMyHand();
}

function refreshOpponentBacks() {
    var seatNum = gameState.seatNum || 4;
    var players = gameState.players || [];
    for (var j = 0; j < players.length; j++) {
        var p = players[j];
        if (p.roleId === userId || gameState.myPosition < 0) continue;
        var tileCount = p.cardCount || 0;
        var relPos = (p.position - gameState.myPosition + seatNum) % seatNum;
        var slot = seatSlot(relPos, seatNum);
        var cap = slot.charAt(0).toUpperCase() + slot.slice(1);
        var info = document.getElementById('player' + cap);
        var tiles = document.getElementById('tiles' + cap);
        if (!info || !tiles) continue;
        info.innerHTML =
            '<span class="name">' + (p.nickName || '玩家') + scoreText(p.totalScore) + '</span>' +
            '<span class="tile-count">' + tileCount + '张</span>';
        // 手牌仅绿牌背看张数；副露（碰/杠/吃）亮倒可见
        renderOpponentExposed(cap, p.position);
        renderCardBacks('tiles' + cap, tileCount);
    }
}

function scoreText(score) {
    if (!gameState.players || gameState.players.length < 2) return '';
    score = Number(score || 0);
    return ' ' + (score > 0 ? '+' : '') + score;
}

/** 渲染对手已亮倒的副露（暗杠仍牌背） */
function renderOpponentExposed(cap, seat) {
    var box = document.getElementById('exposed' + cap);
    if (!box) return;
    box.innerHTML = '';
    var sets = gameState.exposedBySeat[seat] || [];
    for (var i = 0; i < sets.length; i++) {
        appendExposedSet(box, sets[i], { ownerSeat: seat, revealAnGang: false });
    }
}

function updateTileCount() {
    document.getElementById('myTileCount').textContent = gameState.myTiles.length + '张';
}

function setActiveSeat(position, waitSeconds) {
    stopMahjongOperationCountdown();
    ['playerTop', 'playerLeft', 'playerRight', 'playerBottom'].forEach(function (id) {
        document.getElementById(id).className = 'player-info';
    });
    gameState.activeSeat = position;
    updateOperationArrow(position);
    if (position === gameState.myPosition) {
        document.getElementById('playerBottom').className = 'player-info active';
        startMahjongOperationCountdown(document.getElementById('playerBottom'), waitSeconds);
        return;
    }
    var seatNum = gameState.seatNum || 4;
    var relPos = (position - gameState.myPosition + seatNum) % seatNum;
    var slot = seatSlot(relPos, seatNum);
    var cap = slot.charAt(0).toUpperCase() + slot.slice(1);
    var active = document.getElementById('player' + cap);
    active.className = 'player-info active';
    startMahjongOperationCountdown(active, waitSeconds);
}

function stopMahjongOperationCountdown() {
    if (gameState.operationTimer) clearInterval(gameState.operationTimer);
    gameState.operationTimer = null;
    document.querySelectorAll('.operation-countdown').forEach(function (el) { el.remove(); });
}

function startMahjongOperationCountdown(target, waitSeconds) {
    var left = Math.max(0, Math.ceil(Number(waitSeconds || 0)));
    if (!target || !left) return;
    var badge = document.createElement('span');
    badge.className = 'operation-countdown';
    target.appendChild(badge);
    function tick() {
        badge.textContent = left + '秒';
        if (left <= 0) {
            clearInterval(gameState.operationTimer);
            gameState.operationTimer = null;
            return;
        }
        left--;
    }
    tick();
    gameState.operationTimer = setInterval(tick, 1000);
}

function snapshotOperationWait(snapshot) {
    var duration = Number(snapshot && snapshot.stateDuration || 0);
    var started = Number(snapshot && snapshot.stateStart || 0);
    if (!duration || !started) return duration;
    var elapsed = Math.max(0, Math.floor((Date.now() - started) / 1000));
    return Math.max(0, duration - elapsed);
}

function updateOperationArrow(position) {
    var arrow = document.getElementById('operationArrow');
    if (!arrow || position == null || position < 0) return;
    var glyph = arrow.querySelector('.operation-arrow-glyph');
    var label = arrow.querySelector('.operation-arrow-label');
    var seatNum = gameState.seatNum || 4;
    var rel = (position - gameState.myPosition + seatNum) % seatNum;
    var symbol = position === gameState.myPosition ? '↓' : '↑';
    if (position !== gameState.myPosition && rel === 1) symbol = '→';
    else if (position !== gameState.myPosition && rel === seatNum - 1) symbol = '←';
    glyph.textContent = symbol;
    label.textContent = '座位 ' + position + ' 操作';
    arrow.classList.add('visible');
}

/** 吃牌选项：展示完整面子牌面（手牌两张+弃牌），约手牌 1/4 大小，多组可分别点选 */
function appendChiTileFaces(btn, handCards, claimTile) {
    var wrap = document.createElement('span');
    wrap.className = 'chi-tiles';
    var ids = [];
    for (var i = 0; i < handCards.length; i++) ids.push(handCards[i].value);
    if (claimTile) ids.push(claimTile);
    ids.sort(function (a, b) { return a - b; });
    for (var j = 0; j < ids.length; j++) {
        var t = MahjongTile.createTileEl(ids[j], { small: true });
        t.className += ' chi-face';
        if (claimTile && ids[j] === claimTile) t.className += ' chi-claim';
        wrap.appendChild(t);
    }
    btn.appendChild(wrap);
}

function showOperationChoices(choices) {
    var bar = document.getElementById('actionBar');
    bar.innerHTML = '';
    bar.style.display = 'flex';
    for (var i = 0; i < choices.length; i++) {
        (function (choiceObj) {
            var code = choiceObj.choice;
            var cards = choiceCards(choiceObj);
            var btn = document.createElement('button');
            btn.className = 'action-btn';
            if (code === OP.DISCARD || code === 6) {
                btn.className += ' btn-discard';
                btn.textContent = '出牌';
                btn.onclick = function () {
                    if (gameState.selectedTile >= 0) doOp(OP.DISCARD);
                    else showCenterMsg('请先选择一张牌');
                };
            } else if (code === OP.PASS || code === OP.MJ_PASS) {
                btn.className += ' btn-pass';
                btn.textContent = '过';
                btn.onclick = function () { doOp(OP.MJ_PASS); };
            } else if (code === OP.MJ_CHI) {
                btn.className += ' btn-chi btn-chi-tiles';
                var lab = document.createElement('span');
                lab.className = 'chi-label';
                lab.textContent = '吃';
                btn.appendChild(lab);
                appendChiTileFaces(btn, cards, gameState.lastClaimTile);
                btn.onclick = function () { doOp(OP.MJ_CHI, cards); };
            } else if (code === OP.MJ_PENG) {
                btn.className += ' btn-peng';
                btn.textContent = '碰';
                btn.onclick = function () { doOp(OP.MJ_PENG, cards); };
            } else if (code === OP.MJ_GANG) {
                btn.className += ' btn-gang';
                btn.textContent = cards.length === 1
                    ? ('杠(' + getTileName(cards[0].value) + ')')
                    : '杠';
                btn.onclick = function () { doOp(OP.MJ_GANG, cards); };
            } else if (code === OP.MJ_HU) {
                btn.className += ' btn-hu';
                btn.textContent = '胡';
                btn.onclick = function () { doOp(OP.MJ_HU, cards); };
            } else {
                return;
            }
            bar.appendChild(btn);
        })(choices[i]);
    }
}

function showActionButtons(type) {
    var bar = document.getElementById('actionBar');
    bar.innerHTML = '';
    bar.style.display = 'flex';
    if (type === 'prepare') {
        var btn = document.createElement('button');
        btn.className = 'action-btn btn-prepare';
        btn.textContent = '准备';
        btn.onclick = doPrepare;
        bar.appendChild(btn);
    } else if (type === 'waiting') {
        var tip = document.createElement('span');
        tip.style.cssText = 'color:#fff;font-size:14px;padding:8px 12px;';
        tip.textContent = '等待玩家坐满后自动开局（本桌 ' + gameState.seatNum + ' 人）';
        bar.appendChild(tip);
    }
}

function hideActions() { GameTable.hideActions(); }
function showCenterMsg(msg, duration) { GameTable.showCenterMsg(msg, duration); }
