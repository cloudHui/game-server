/**
 * 扑克牌桌共用渲染与结算。
 * 各玩法覆盖：roleBadgeHtml、showOperationChoices；四人桌另覆写座位相关函数。
 */
function cardMeta(cardId) { return PokerCard.cardMeta(cardId); }
function createCardFace(cardId) { return PokerCard.createCardFace(cardId); }

function layoutMyCards() {
    var container = document.getElementById('myCards');
    if (!container || !window.GameLandscape) return;
    var n = Math.max(gameState.layoutCardCount || gameState.myCards.length, 1);
    // 普通玩法尽量保持一排；只有拖拉机允许在 18 张以上分两排。
    var rows = window.pokerAllowTwoRows && n >= 18 ? 2 : 1;
    var rowCount = Math.ceil(n / rows);
    var pad = 24;
    var size = GameLandscape.forcePokerLandscape();
    var avail = Math.max(160, size.w - pad);
    var cardW = rows === 2 ? Math.min(62, Math.max(48, Math.floor(avail / rowCount))) : 54;
    var overlap = 0;
    if (rowCount > 1) {
        // 单排也按可用宽度压叠，保证普通玩法 20 张仍能完整落在牌区内。
        if (rows === 1) {
            overlap = Math.floor((avail - cardW) / (rowCount - 1) - cardW);
            overlap = Math.max(-Math.floor(cardW * 0.82), Math.min(-8, overlap));
        }
        if (rows === 2) {
        overlap = Math.floor((avail - cardW) / (rowCount - 1) - cardW);
        overlap = Math.max(-Math.floor(cardW * 0.78), Math.min(-12, overlap));
        var total = cardW + (rowCount - 1) * (cardW + overlap);
        if (total > avail) {
            cardW = Math.max(36, Math.floor(avail / (1 + (rowCount - 1) * 0.28)));
            overlap = Math.floor((avail - cardW) / (rowCount - 1) - cardW);
            overlap = Math.max(-Math.floor(cardW * 0.82), Math.min(-8, overlap));
        }
        }
    }
    // 牌数较多的玩法可按页面设置缩放，避免两排手牌遮住桌面。
    var handScale = Number(window.pokerHandScale || 1);
    if (handScale > 0 && handScale < 1) {
        cardW = Math.max(30, Math.round(cardW * handScale));
        overlap = Math.round(overlap * handScale);
    }
    var cardH = Math.round(cardW * (75 / 54));
    container.style.setProperty('--ddz-card-w', cardW + 'px');
    container.style.setProperty('--ddz-card-h', cardH + 'px');
    container.style.setProperty('--ddz-overlap', overlap + 'px');
    container.style.setProperty('--poker-corner', Math.max(7, Math.round(cardW * 0.17)) + 'px');
    container.style.setProperty('--poker-art', Math.max(16, Math.round(cardW * 0.5)) + 'px');
    var bar = document.getElementById('actionBar');
    // 操作栏使用 CSS 固定抬起位置，牌面重绘时不要跟着牌区跳动。
}

function renderMyCards() {
    var container = document.getElementById('myCards');
    var useTwoRows = window.pokerAllowTwoRows && gameState.myCards.length >= 18;
    container.className = 'my-cards' + (useTwoRows ? ' two-rows' : '');
    var frag = document.createDocumentFragment();
    var rowSize = useTwoRows ? Math.ceil(gameState.myCards.length / 2) : gameState.myCards.length;
    var row = null;
    var flashBag = {};
    var flashList = gameState.dealFlashIds || [];
    for (var f = 0; f < flashList.length; f++) {
        flashBag[flashList[f]] = (flashBag[flashList[f]] || 0) + 1;
    }
    for (var i = 0; i < gameState.myCards.length; i++) {
        if (i % rowSize === 0) {
            row = document.createElement('div');
            row.className = 'hand-row';
            frag.appendChild(row);
        }
        var cardId = gameState.myCards[i];
        var flash = flashBag[cardId] > 0;
        if (flash) flashBag[cardId]--;
        var card = document.createElement('div');
        var selected = gameState.selectedCardIndexes
            ? gameState.selectedCardIndexes.has(i) : gameState.selectedCards.has(cardId);
        card.className = 'card' + (selected ? ' selected' : '')
            + (flash ? ' deal-in' : '');
        card.dataset.index = i;
        card.style.zIndex = String(i + 1);
        card.appendChild(createCardFace(cardId));
        // 单击切换；拖选由 bindPokerHandSelect 统一处理
        card.onmousedown = (function(value, idx) {
            return function(ev) { beginPokerDragSelect(ev, value, idx); };
        })(cardId, i);
        card.ontouchstart = (function(value, idx) {
            return function(ev) { beginPokerDragSelect(ev, value, idx); };
        })(cardId, i);
        row.appendChild(card);
    }
    container.innerHTML = '';
    container.appendChild(frag);
    renderSelectionStatus();
    gameState.dealFlashIds = [];
    layoutMyCards();
    renderPlayerLabels();
    bindPokerHandSelect();
}

function renderSelectionStatus() {
    var bar = document.getElementById('actionBar');
    if (!bar || gameState.opSeat !== gameState.myPosition) return;
    var n = gameState.selectedCardIndexes ? gameState.selectedCardIndexes.size : gameState.selectedCards.size;
    var old = document.getElementById('selectionStatus');
    if (old) old.remove();
    if (!n) return;
    var tip = document.createElement('span');
    tip.id = 'selectionStatus';
    tip.className = 'selection-status';
    tip.textContent = '已选 ' + n + ' 张';
    bar.appendChild(tip);
}

/** 点非牌面区域放下已选牌；牌面上按下拖动松手后提起经过的牌（可智能成牌型） */
var _pokerSelectBound = false;
var _pokerDrag = null;
var _pokerIgnoreClickUntil = 0;

function clearPokerSelection() {
    if (gameState.selectedCardIndexes) gameState.selectedCardIndexes.clear();
    if (gameState.selectedCards) gameState.selectedCards.clear();
    renderMyCards();
}

function bindPokerHandSelect() {
    if (_pokerSelectBound) return;
    _pokerSelectBound = true;
    document.addEventListener('click', function (ev) {
        var bottomBox = document.getElementById('dizhuCards');
        if (bottomBox && bottomBox.classList.contains('bottom-card-open')
                && !(ev.target.closest && ev.target.closest('#dizhuCards'))) {
            renderDizhuCards(gameState.bottomCards || []);
        }
        if (Date.now() < _pokerIgnoreClickUntil) return;
        if (!gameState || !gameState.myCards || !gameState.myCards.length) return;
        if (ev.target.closest && ev.target.closest('#myCards .card')) return;
        if (ev.target.closest && ev.target.closest('#actionBar')) return;
        if (ev.target.closest && ev.target.closest('.settle-overlay')) return;
        var hasSel = (gameState.selectedCards && gameState.selectedCards.size)
            || (gameState.selectedCardIndexes && gameState.selectedCardIndexes.size);
        if (hasSel) clearPokerSelection();
    }, true);
    document.addEventListener('mousemove', onPokerDragMove);
    document.addEventListener('mouseup', endPokerDragSelect);
    document.addEventListener('touchmove', onPokerDragMove, { passive: false });
    document.addEventListener('touchend', endPokerDragSelect);
}

function beginPokerDragSelect(ev, cardValue, cardIndex) {
    if (ev.type === 'mousedown' && ev.button !== 0) return;
    if (ev.cancelable) ev.preventDefault();
    _pokerDrag = {
        startIndex: cardIndex,
        visited: {},
        moved: false,
        startX: ev.clientX || (ev.touches && ev.touches[0] && ev.touches[0].clientX) || 0,
        startY: ev.clientY || (ev.touches && ev.touches[0] && ev.touches[0].clientY) || 0
    };
    _pokerDrag.visited[cardIndex] = true;
}

function onPokerDragMove(ev) {
    if (!_pokerDrag) return;
    var pt = ev.touches && ev.touches[0] ? ev.touches[0] : ev;
    if (Math.abs(pt.clientX - _pokerDrag.startX) + Math.abs(pt.clientY - _pokerDrag.startY) > 6) {
        _pokerDrag.moved = true;
    }
    if (!_pokerDrag.moved) return;
    if (ev.cancelable) ev.preventDefault();
    var el = document.elementFromPoint(pt.clientX, pt.clientY);
    var cardEl = el && el.closest ? el.closest('#myCards .card') : null;
    if (!cardEl) return;
    var idx = Number(cardEl.dataset.index);
    if (isNaN(idx)) return;
    _pokerDrag.visited[idx] = true;
}

function endPokerDragSelect(ev) {
    if (!_pokerDrag) return;
    var drag = _pokerDrag;
    _pokerDrag = null;
    var indexes = Object.keys(drag.visited).map(Number).sort(function (a, b) { return a - b; });
    if (!drag.moved) {
        // 单击：切换单张；吞掉随后 click，避免与清空逻辑冲突
        _pokerIgnoreClickUntil = Date.now() + 80;
        toggleCard(gameState.myCards[drag.startIndex], drag.startIndex);
        return;
    }
    _pokerIgnoreClickUntil = Date.now() + 120;
    // 拖选：默认提起经过的牌；玩法可覆写为最长合法牌型
    var picked = indexes.map(function (i) { return gameState.myCards[i]; });
    if (typeof window.pokerSmartSelectFromDrag === 'function') {
        picked = window.pokerSmartSelectFromDrag(picked) || picked;
    }
    applyPokerPickedCards(picked);
}

function applyPokerPickedCards(picked) {
    if (gameState.selectedCardIndexes) {
        gameState.selectedCardIndexes.clear();
        var remainIdx = picked.slice();
        for (var i = 0; i < gameState.myCards.length; i++) {
            var at = remainIdx.indexOf(gameState.myCards[i]);
            if (at >= 0) {
                gameState.selectedCardIndexes.add(i);
                remainIdx.splice(at, 1);
            }
        }
    } else {
        gameState.selectedCards.clear();
        var remain = picked.slice();
        for (var j = 0; j < gameState.myCards.length; j++) {
            var v = gameState.myCards[j];
            var pos = remain.indexOf(v);
            if (pos >= 0) {
                gameState.selectedCards.add(v);
                remain.splice(pos, 1);
            }
        }
    }
    renderMyCards();
}

function renderDizhuCards(cardValues) {
    var box = document.getElementById('dizhuCards');
    if (!box) return;
    clearTimeout(gameState.bottomPeekTimer);
    box.innerHTML = '';
    if (!cardValues || !cardValues.length || gameState.landlordId !== userId) {
        box.hidden = true;
        return;
    }
    box.hidden = false;
    box.classList.remove('bottom-card-open');
    box.onclick = function (ev) {
        ev.stopPropagation();
        box.classList.add('bottom-card-open');
        box.innerHTML = '';
        var label = document.createElement('span');
        label.className = 'dizhu-label';
        label.textContent = '底牌';
        box.appendChild(label);
        cardValues.forEach(function (value) {
            var card = document.createElement('div');
            card.className = 'card';
            card.appendChild(createCardFace(value));
            box.appendChild(card);
        });
    };
    var hint = document.createElement('span');
    hint.className = 'bottom-card-hint';
    hint.textContent = '查看底牌';
    box.appendChild(hint);
}

function toggleCard(cardValue, cardIndex) {
    if (gameState.selectedCardIndexes) {
        if (gameState.selectedCardIndexes.has(cardIndex)) gameState.selectedCardIndexes.delete(cardIndex);
        else gameState.selectedCardIndexes.add(cardIndex);
        renderMyCards();
        return;
    }
    if (gameState.selectedCards.has(cardValue)) gameState.selectedCards.delete(cardValue);
    else gameState.selectedCards.add(cardValue);
    renderMyCards();
}

function scoreText(score) {
    score = Number(score || 0);
    return ' ' + (score > 0 ? '+' : '') + score;
}

function playedAreaIds() {
    return (gameState.seatNum || 3) >= 4
        ? ['playedTop', 'playedLeft', 'playedRight', 'playedBottom']
        : ['playedLeft', 'playedRight', 'playedBottom'];
}

function clearAllPlayedAreas() {
    playedAreaIds().forEach(function(id) {
        var el = document.getElementById(id);
        if (el) el.innerHTML = '';
    });
}

function clearPassHints() {
    playedAreaIds().forEach(function(id) {
        var el = document.getElementById(id);
        if (el && el.querySelector('.pass-hint')) el.innerHTML = '';
    });
}

function playedTargetForRole(roleId) {
    if (roleId === userId) return document.getElementById('playedBottom');
    var seatNum = gameState.seatNum || 3;
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].roleId !== roleId) continue;
        var rel = (gameState.players[i].position - gameState.myPosition + seatNum) % seatNum;
        if (seatNum >= 4) {
            var slot = seatSlot(rel, seatNum);
            return document.getElementById('played' + slot.charAt(0).toUpperCase() + slot.slice(1));
        }
        return document.getElementById(rel === 1 ? 'playedLeft' : 'playedRight');
    }
    return null;
}

function seatSlot(relPos, seatNum) {
    if (seatNum <= 2) return 'top';
    if (seatNum === 3) return relPos === 1 ? 'right' : 'left';
    if (relPos === 1) return 'right';
    if (relPos === 2) return 'top';
    return 'left';
}

function renderPlayedCards(roleId, cardValues) {
    var target = playedTargetForRole(roleId);
    if (!target) return;
    target.innerHTML = '';
    target.classList.toggle('has-played-cards', !!(cardValues && cardValues.length));
    var values = cardValues || [];
    var count = values.length;
    var slot = target.id || '';
    // 出牌区只保留牌值/数字的可读性，所有牌水平密集叠放，不再扇形展开。
    values.forEach(function(value, index) {
        var face = createCardFace(value);
        face.classList.add('played-face');
        target.appendChild(face);
    });
}

function rememberPreviousHand(roleId, cards) {
    if (!cards || !cards.length) return;
    gameState.previousHand = {roleId: roleId, cards: cards.slice()};
    var btn = document.getElementById('previousHandBtn');
    if (btn) btn.hidden = false;
}

function showPreviousHand() {
    if (!gameState.previousHand) return;
    renderPlayedCards(gameState.previousHand.roleId, gameState.previousHand.cards);
    clearTimeout(gameState.previousHandTimer);
    gameState.previousHandTimer = setTimeout(function () {
        clearAllPlayedAreas();
    }, 1500);
}

function showPassHint(roleId) {
    var target = playedTargetForRole(roleId);
    if (!target) return;
    target.innerHTML = '<div class="pass-hint">不要</div>';
}

function showBidHint(roleId, text) {
    var target = playedTargetForRole(roleId);
    if (!target) return;
    target.innerHTML = '<div class="pass-hint">' + text + '</div>';
}

function roleBadgeHtml(roleId) { return ''; }

function renderPlayerLabels() {
    var bottom = document.getElementById('playerBottom');
    if (!bottom) return;
    var mine = null;
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].roleId === userId) mine = gameState.players[i];
    }
    bottom.innerHTML = roleBadgeHtml(userId)
        + '<span class="name" id="myName">' + (nickname || '我')
        + scoreText(mine && mine.totalScore) + '</span>';
}

function renderCardBacks(containerId, count) {
    var container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = '';
    var show = Math.min(count, (gameState.seatNum || 3) >= 4 ? 12 : count);
    for (var i = 0; i < show; i++) {
        var back = document.createElement('div');
        back.className = 'card-back';
        container.appendChild(back);
    }
}

function renderOpponentHands() {
    var players = gameState.players || [];
    var seatNum = gameState.seatNum || 3;
    renderPlayerLabels();
    if (seatNum >= 4) {
        ['Top', 'Left', 'Right'].forEach(function(s) {
            var info = document.getElementById('player' + s);
            var cards = document.getElementById('cards' + s);
            if (info) info.innerHTML = '<span class="name">等待加入</span>';
            if (cards) cards.innerHTML = '';
        });
        for (var i = 0; i < players.length; i++) {
            var p = players[i];
            if (p.roleId === userId || gameState.myPosition < 0) continue;
            var cardCount = gameState.opponentCounts[p.roleId];
            if (cardCount == null) cardCount = p.cardCount || 0;
            var displayName = p.nickName || (p.robot || p.roleId < 0 ? '机器人' : '玩家');
            var relPos = (p.position - gameState.myPosition + seatNum) % seatNum;
            var slot = seatSlot(relPos, seatNum);
            var cap = slot.charAt(0).toUpperCase() + slot.slice(1);
            var info = document.getElementById('player' + cap);
            if (!info) continue;
            info.innerHTML = roleBadgeHtml(p.roleId)
                + '<span class="name">' + displayName + scoreText(p.totalScore) + '</span>'
                + '<span class="card-count">' + cardCount + '张</span>';
            renderCardBacks('cards' + cap, cardCount);
        }
        return;
    }
    var leftEl = document.getElementById('playerLeft');
    var rightEl = document.getElementById('playerRight');
    for (var j = 0; j < players.length; j++) {
        var op = players[j];
        if (op.roleId === userId) continue;
        var cnt = gameState.opponentCounts[op.roleId];
        if (cnt == null) cnt = op.cardCount || 0;
        var name = op.nickName || (op.robot || op.roleId < 0 ? '机器人' : '玩家');
        var nameHtml = roleBadgeHtml(op.roleId)
            + '<span class="name">' + name + scoreText(op.totalScore) + '</span>'
            + '<span class="card-count">' + cnt + '张</span>';
        var rel = (op.position - gameState.myPosition + 3) % 3;
        if (rel === 1) {
            leftEl.innerHTML = nameHtml;
            renderCardBacks('cardsLeft', cnt);
        } else {
            rightEl.innerHTML = nameHtml;
            renderCardBacks('cardsRight', cnt);
        }
    }
}

function updatePlayers(players) {
    gameState.players = players;
    for (var i = 0; i < players.length; i++) {
        if (players[i].roleId === userId) {
            gameState.myPosition = players[i].position;
            break;
        }
    }
    if ((gameState.seatNum || 3) < 4) {
        var leftEl = document.getElementById('playerLeft');
        var rightEl = document.getElementById('playerRight');
        var leftCards = document.getElementById('cardsLeft');
        var rightCards = document.getElementById('cardsRight');
        if (leftEl) leftEl.innerHTML = '<span class="name">等待加入</span>';
        if (rightEl) rightEl.innerHTML = '<span class="name">等待加入</span>';
        if (leftCards) leftCards.innerHTML = '';
        if (rightCards) rightCards.innerHTML = '';
    }
    renderOpponentHands();
}

function highlightActivePlayer(position, waitSeconds) {
    gameState.opSeat = position;
    stopOperationCountdown();
    var seatNum = gameState.seatNum || 3;
    var ids = seatNum >= 4
        ? ['playerTop', 'playerLeft', 'playerRight', 'playerBottom']
        : ['playerLeft', 'playerRight', 'playerBottom'];
    ids.forEach(function(id) {
        var el = document.getElementById(id);
        if (el) el.className = 'player-info';
    });
    if (position === gameState.myPosition) {
        document.getElementById('playerBottom').className = 'player-info active';
        startOperationCountdown(document.getElementById('playerBottom'), waitSeconds);
        return;
    }
    var relPos = (position - gameState.myPosition + seatNum) % seatNum;
    if (seatNum >= 4) {
        var slot = seatSlot(relPos, seatNum);
        var el4 = document.getElementById('player' + slot.charAt(0).toUpperCase() + slot.slice(1));
        if (el4) el4.className = 'player-info active';
        startOperationCountdown(el4, waitSeconds);
        return;
    }
    var active = document.getElementById(relPos === 1 ? 'playerLeft' : 'playerRight');
    active.className = 'player-info active';
    startOperationCountdown(active, waitSeconds);
}

function stopOperationCountdown() {
    if (gameState.operationTimer) clearInterval(gameState.operationTimer);
    gameState.operationTimer = null;
    document.querySelectorAll('.operation-countdown').forEach(function(el) { el.remove(); });
}

function startOperationCountdown(target, waitSeconds) {
    var left = Math.max(0, Math.ceil(Number(waitSeconds || 0)));
    if (!target || !left) return;
    var badge = document.createElement('span');
    badge.className = 'operation-countdown';
    target.appendChild(badge);
    function tick() {
        badge.textContent = left + '秒';
        badge.classList.toggle('warning', left < 10);
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
        tip.textContent = '等待玩家坐满后自动开局';
        bar.appendChild(tip);
    }
}

function hideActions() { GameTable.hideActions(); }
function showCenterMsg(msg, duration) { GameTable.showCenterMsg(msg, duration); }

function sortPokerByValue(cards) {
    cards.sort(function(a, b) {
        var va = a % 100, vb = b % 100;
        if (va !== vb) return va - vb;
        return Math.floor(a / 100) - Math.floor(b / 100);
    });
}

function findPlayerName(roleId) {
    if (roleId === userId) return nickname || '我';
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].roleId === roleId) {
            return gameState.players[i].nickName || ('玩家' + roleId);
        }
    }
    return '玩家' + roleId;
}

/** 结算余牌角色标签，玩法可覆写 pokerSettleTag */
function pokerSettleTag(roleId, landlordId) {
    if (!landlordId) return '余牌';
    return roleId === landlordId ? '地主' : '农民';
}

function buildRemainHandsHtml(remainPlayers, landlordId) {
    if (!remainPlayers || !remainPlayers.length) return '';
    var html = '';
    for (var i = 0; i < remainPlayers.length; i++) {
        var p = remainPlayers[i];
        var cards = p.cards || [];
        if (!cards.length) continue;
        html += '<div class="settle-hand-row"><div class="label">'
            + findPlayerName(p.roleId) + '（' + pokerSettleTag(p.roleId, landlordId) + '）剩余手牌</div>'
            + '<div class="settle-hand-cards"></div></div>';
    }
    return html;
}

function fillRemainHandFaces(remainPlayers) {
    var wrap = document.getElementById('settleHands');
    if (!wrap || !remainPlayers) return;
    var rows = wrap.querySelectorAll('.settle-hand-cards');
    var ri = 0;
    for (var i = 0; i < remainPlayers.length; i++) {
        var cards = (remainPlayers[i].cards || []).slice();
        if (!cards.length) continue;
        // 小结算余牌按手牌序展示
        if (typeof window.pokerSortSettleCards === 'function') window.pokerSortSettleCards(cards);
        else sortPokerByValue(cards);
        var box = rows[ri++];
        if (!box) continue;
        for (var c = 0; c < cards.length; c++) {
            var face = createCardFace(cards[c]);
            face.classList.add('played-face');
            box.appendChild(face);
        }
    }
}

function showSettle(title, meta, rowsHtml, remainPlayers, landlordId) {
    GameTable.showSettle({
        title: title,
        meta: meta,
        rowsHtml: rowsHtml,
        handsHtml: buildRemainHandsHtml(remainPlayers, landlordId),
        autoNext: gameState.autoNextRound
    });
    fillRemainHandFaces(remainPlayers);
}

function closeSettle() { GameTable.closeSettle(); }

function appendOpChoice(bar, choice, map) {
    var conf = map[choice.choice];
    if (!conf) return;
    var btn = document.createElement('button');
    btn.className = 'action-btn ' + conf.cls;
    btn.textContent = conf.text;
    btn.onclick = (function(code) {
        return function() { doOp(code); };
    })(choice.choice);
    bar.appendChild(btn);
}

function showOperationChoices(choices) {
    var bar = document.getElementById('actionBar');
    bar.innerHTML = '';
    bar.style.display = 'flex';
    var map = window.pokerOpChoiceMap || {};
    for (var i = 0; i < choices.length; i++) appendOpChoice(bar, choices[i], map);
    var canPlay = choices.some(function (item) { return item.choice === 6; });
    if (canPlay && typeof window.pokerSuggestPlay === 'function') {
        var hint = document.createElement('button');
        hint.className = 'action-btn btn-hint';
        hint.textContent = '提示';
        hint.onclick = window.pokerSuggestPlay;
        bar.appendChild(hint);
    }
}
