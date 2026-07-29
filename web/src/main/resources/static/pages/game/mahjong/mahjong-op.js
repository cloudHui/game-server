/**
 * 麻将操作与推送处理：出牌/吃碰杠胡、本地手牌扣减。
 */
function choiceCards(choiceObj) {
    var cards = choiceObj && choiceObj.cards ? choiceObj.cards : [];
    var out = [];
    for (var i = 0; i < cards.length; i++) {
        var v = typeof cards[i] === 'object' ? cards[i].value : cards[i];
        if (v != null) out.push({ value: v });
    }
    return out;
}

function doOp(choice, cards) {
    if (gameState.opPending) return;
    cards = cards || [];
    if (choice === OP.DISCARD && cards.length === 0 && gameState.selectedTile >= 0) {
        cards = [{ value: gameState.myTiles[gameState.selectedTile] }];
    }
    if (choice === OP.MJ_CHI && cards.length > 0) {
        var chiTiles = [];
        for (var i = 0; i < cards.length; i++) chiTiles.push(cards[i].value);
        if (gameState.lastClaimTile) chiTiles.push(gameState.lastClaimTile);
        chiTiles.sort(function (a, b) { return a - b; });
        gameState._lastChiTiles = chiTiles;
    }

    if (choice === OP.DISCARD && cards.length > 0) {
        gameState.pendingDiscardTile = cards[0].value;
    }
    console.info('[麻将操作发送]', { choice: choice, cards: cards.map(function (c) { return c.value; }),
        handCount: gameState.myTiles.length, hand: gameState.myTiles.slice() });
    gameState.opPending = true;
    sendWsMessage('op', {
        choice: choice,
        cards: cards.length > 0 ? cards : undefined
    }, function (resp) {
        gameState.opPending = false;
        if (resp.code !== 0) {
            if (choice === OP.DISCARD) gameState.pendingDiscardTile = 0;
            // 非超时类失败才提示，避免误操作刷屏
            if (resp.msg && resp.msg.indexOf('超时') < 0) {
                showCenterMsg(resp.msg || '操作失败');
            }
            return;
        }
        console.info('[麻将操作成功]', { choice: choice, cards: cards.map(function (c) { return c.value; }),
            handCount: gameState.myTiles.length });
        if (choice === OP.MJ_HU) {
            showCenterMsg('胡!', 2000);
        }
    });
    hideActions();
}

function doPrepare() {
    gameState.exposedBySeat = {};
    gameState.discardedTiles = [];
    gameState.discardSeats = [];
    gameState.discardLayouts = [];
    gameState.lastDiscardEventAt = 0;
    gameState.myTiles = [];
    gameState.drawnTileId = 0;
    gameState.lastDiscardTile = 0;
    gameState.lastDiscardSeat = -1;
    gameState.pendingDiscardTile = 0;
    renderMyExposed();
    renderDiscarded();
    renderMyTiles();
    GameTable.doPrepare(sendWsMessage);
}

/** 重连前清空仅存在于浏览器的牌桌增量，随后由服务端完整回放恢复。 */
function resetMahjongViewForReconnect() {
    gameState.discardedTiles = [];
    gameState.discardSeats = [];
    gameState.discardLayouts = [];
    gameState.lastDiscardEventAt = 0;
    gameState.syncingHistory = true;
    gameState.exposedBySeat = {};
    gameState.myTiles = [];
    gameState.drawnTileId = 0;
    gameState.lastDiscardTile = 0;
    gameState.lastDiscardSeat = -1;
    gameState.pendingDiscardTile = 0;
    renderMyExposed();
    renderDiscarded();
    renderMyTiles();
}

function refreshTable() {
    var btn = document.getElementById('refreshTableBtn');
    if (!btn || btn.disabled) return;
    btn.disabled = true; btn.textContent = '刷新中';
    sendWsMessage('refreshTable', { tableId: tableId }, function (resp) {
        btn.disabled = false; btn.textContent = '刷新牌桌';
        if (resp.code !== 0 || !resp.data) {
            showCenterMsg(resp.msg || '刷新牌桌失败');
            return;
        }
        applyMahjongSnapshot(resp.data);
        showCenterMsg('牌桌已刷新', 1000);
    });
}

function applyMahjongSnapshot(s) {
    updatePlayers(s.players || []);
    gameState.myTiles = [];
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].roleId === userId) gameState.myTiles = (gameState.players[i].cards || []).slice();
    }
    sortHandTiles(gameState.myTiles);
    gameState.drawnTileId = s.drawnTile || 0;
    gameState.discardedTiles = (s.discards || []).map(function (d) { return d.tileId; });
    gameState.discardSeats = (s.discards || []).map(function (d) { return d.seat; });
    gameState.discardLayouts = [];
    gameState.lastDiscardEventAt = 0;
    gameState.exposedBySeat = {};
    (s.exposed || []).forEach(function (e) {
        if (!gameState.exposedBySeat[e.seat]) gameState.exposedBySeat[e.seat] = [];
        gameState.exposedBySeat[e.seat].push({ kind: e.type, tiles: e.tileIds || [] });
    });
    gameState.wallLeft = s.wallLeft || 0;
    gameState.lastDiscardTile = s.pendingDiscardTile || 0;
    gameState.lastDiscardSeat = s.pendingDiscardSeat == null ? -1 : s.pendingDiscardSeat;
    gameState.opPending = false;
    renderMyTiles(); renderMyExposed(); renderDiscarded(); refreshOpponentBacks();
    setActiveSeat(s.opSeat);
    document.getElementById('wallInfo').textContent = '牌墙剩余: ' + gameState.wallLeft + '张';
    if (s.opSeat === gameState.myPosition) showOperationChoices(s.choices || []); else hideActions();
}

function recordExposedFromAction(data) {
    var seat = data.opSeat;
    if (seat < 0) return;
    if (!gameState.exposedBySeat[seat]) gameState.exposedBySeat[seat] = [];
    var info = buildExposedRecord(seat, data.action, data.tileId);
    if (!info) return;
    gameState.exposedBySeat[seat].push(info);
    if (seat === gameState.myPosition) renderMyExposed();
    else refreshOpponentBacks();
}

/**
 * 应用 notCard：开局全量排序；摸牌时新牌隔离在最右并仅闪新牌。
 * 摸牌不再提示「发牌完成」。
 */
function handleNotCard(data) {
    if (!data || !data.nCards) return;
    gameState.opPending = false;
    var prevLen = gameState.myTiles.length;
    var prevDrawn = gameState.drawnTileId;
    var nextMine = [];
    for (var i = 0; i < data.nCards.length; i++) {
        var nc = data.nCards[i];
        if (nc.roleId === userId && nc.cards) {
            for (var j = 0; j < nc.cards.length; j++) {
                nextMine.push(nc.cards[j].value);
            }
        }
        if (nc.roleId !== userId) {
            var count = nc.cards ? nc.cards.length : 0;
            for (var p = 0; p < gameState.players.length; p++) {
                if (gameState.players[p].roleId === nc.roleId) {
                    gameState.players[p].cardCount = count;
                }
            }
        }
    }

    console.info('[麻将手牌同步]', { previousCount: prevLen, nextCount: nextMine.length,
        nextMine: nextMine.slice(), previousDrawn: prevDrawn });

    var isInitialDeal = prevLen === 0 && nextMine.length >= 13;
    var isDraw = !isInitialDeal && nextMine.length === prevLen + 1;

    if (isDraw) {
        var drawn = findDrawnTile(gameState.myTiles, nextMine, prevDrawn);
        var rest = nextMine.slice();
        var di = rest.indexOf(drawn);
        if (di >= 0) rest.splice(di, 1);
        sortHandTiles(rest);
        rest.push(drawn);
        gameState.myTiles = rest;
        gameState.drawnTileId = drawn;
        gameState.selectedTile = rest.length - 1;
        renderMyTiles({ flashDrawn: true });
    } else {
        sortHandTiles(nextMine);
        gameState.myTiles = nextMine;
        gameState.drawnTileId = 0;
        gameState.selectedTile = -1;
        renderMyTiles();
        if (isInitialDeal) showCenterMsg('发牌完成', 1200);
    }
    updateTileCount();
    refreshOpponentBacks();
}

/** 对比新旧手牌找出刚摸入的一张 */
function findDrawnTile(prev, next, hint) {
    var bag = {};
    for (var i = 0; i < prev.length; i++) {
        bag[prev[i]] = (bag[prev[i]] || 0) + 1;
    }
    for (var j = 0; j < next.length; j++) {
        var id = next[j];
        if (!bag[id]) return id;
        bag[id]--;
    }
    if (hint && next.indexOf(hint) >= 0) return hint;
    return next[next.length - 1];
}

function handleNotOp(data) {
    if (!data) return;
    var opSeat = data.opSeat;
    setActiveSeat(opSeat);
    if (gameState.myPosition >= 0 && opSeat === gameState.myPosition) {
        showOperationChoices(data.choice || []);
    } else {
        hideActions();
    }
}

function handleNotState(data) {
    if (data && data.state === TABLE_STATE_DIS) {
        gameWs.stopReconnect();
        window.location.href = appUrl('/pages/home/room.html');
        return;
    }
    document.getElementById('tableState').textContent =
        data.state === 1 ? '游戏进行中' : '等待中';
}

function handleNotMjState(data) {
    if (!data) return;
    console.info('[麻将状态消息]', { action: data.action, seat: data.opSeat, tile: data.tileId,
        wallLeft: data.wallLeft, handCount: gameState.myTiles.length });
    if (data.tileId) gameState.lastClaimTile = data.tileId;
    if (data.wallLeft != null) {
        gameState.wallLeft = data.wallLeft;
        document.getElementById('wallInfo').textContent = '牌墙剩余: ' + data.wallLeft + '张';
    }
    if (data.tileId && data.action === OP.DISCARD) {
        gameState.lastDiscardTile = data.tileId;
        gameState.lastDiscardSeat = data.opSeat;
        gameState.pendingDiscardTile = 0;
        appendDiscardEvent(data.opSeat, data.tileId);
        if (data.opSeat === gameState.myPosition) {
            gameState.opPending = false;
            gameState.drawnTileId = 0;
        }
    }
    if (data.action === OP.MJ_PENG || data.action === OP.MJ_GANG || data.action === OP.MJ_CHI) {
        if (data.opSeat === gameState.myPosition) gameState.opPending = false;
        var gangKind = '';
        if (data.action === OP.MJ_GANG) {
            if (!upgradePengToBuGang(data.opSeat, data.tileId)) {
                recordExposedFromAction(data);
            }
            gangKind = resolveGangKindAfter(data.opSeat, data.tileId);
        } else {
            recordExposedFromAction(data);
        }
        if (shouldRemoveDiscardForAction(data.action, data.opSeat, data.tileId)) {
            removeClaimedDiscard(data.tileId);
        }
        var tip = data.action === OP.MJ_PENG ? '碰'
            : (data.action === OP.MJ_CHI ? '吃'
                : (gangKind === 'anGang' ? '暗杠' : (gangKind === 'buGang' ? '补杠' : '杠')));
        showCenterMsg('座位 ' + data.opSeat + ' ' + tip, 1200);
    }
    if (data.action === OP.MJ_HU) {
        if (shouldRemoveDiscardForAction(data.action, data.opSeat, data.tileId)) {
            removeClaimedDiscard(data.tileId);
        }
        showCenterMsg('座位 ' + data.opSeat + ' 胡!', 2500);
    }
    var choices = data.choice || [];
    if (choices.length > 0 && gameState.myPosition >= 0 && data.opSeat === gameState.myPosition) {
        showOperationChoices(choices);
    }
}

function handleNotResult(data) {
    var msg = (data && data.winner && data.winner > 0)
        ? ('玩家 ' + data.winner + ' 获胜!') : '流局!';
    showCenterMsg(msg, 3000);
    showActionButtons('prepare');
}

function handleNotRoundResult(data) {
    if (!data) return;
    applyTotalScores(data.totalScores || []);
    var title = data.winnerSeat < 0 ? '流局' : ('第 ' + data.round + ' 局 · 胡');
    var meta = (data.winnerSeat >= 0
        ? ('座位 ' + data.winnerSeat + ' 胡 · ' + (data.fan || 0) + ' 番 · ' + (data.winType || ''))
        : ('第 ' + data.round + ' 局'));
    var rows = '';
    var scores = data.seatScores || [];
    for (var i = 0; i < scores.length; i++) {
        var sign = scores[i].score > 0 ? '+' : '';
        rows += '<div class="row"><span>座位 ' + scores[i].seat + '</span><span>'
            + sign + scores[i].score + '</span></div>';
    }
    if (data.winTile) removeClaimedDiscard(data.winTile);
    showSettle(title, meta, rows, buildMjSettleHandsHtml(data.hands || []));
    showCenterMsg(title, 2500);
    gameState.exposedBySeat = {};
    gameState.discardedTiles = [];
    gameState.discardSeats = [];
    gameState.discardLayouts = [];
    gameState.lastDiscardEventAt = 0;
    gameState.drawnTileId = 0;
    gameState.lastDiscardTile = 0;
    gameState.lastDiscardSeat = -1;
    renderMyExposed();
    renderDiscarded();
    showActionButtons('prepare');
}

/**
 * 服务器是牌河的唯一数据源。实时链路偶发重复转发同一条出牌时，只保留一次；
 * 重连历史按座位回放，允许同一玩家连续出现两张相同牌。
 */
function appendDiscardEvent(seat, tileId) {
    var n = gameState.discardedTiles.length;
    var now = Date.now();
    var duplicate = !gameState.syncingHistory && n > 0
        && gameState.discardedTiles[n - 1] === tileId
        && gameState.discardSeats[n - 1] === seat
        && now - gameState.lastDiscardEventAt < 2000;
    if (duplicate) {
        console.warn('[麻将弃牌去重]', { seat: seat, tile: tileId });
        return false;
    }
    gameState.discardedTiles.push(tileId);
    gameState.discardSeats.push(seat);
    gameState.lastDiscardEventAt = now;
    renderDiscarded();
    return true;
}

function applyTotalScores(totals) {
    for (var i = 0; i < totals.length; i++) {
        for (var j = 0; j < gameState.players.length; j++) {
            if (gameState.players[j].position === totals[i].seat) {
                gameState.players[j].totalScore = totals[i].score;
            }
        }
    }
    refreshOpponentBacks();
}

/** 结算亮开手牌与副露；暗杠亮牌并打「暗杠」标识，来源标注方向 */
function buildMjSettleHandsHtml(hands) {
    if (!hands || !hands.length) return '';
    var html = '';
    for (var i = 0; i < hands.length; i++) {
        var h = hands[i];
        html += '<div class="settle-hand-row"><div class="label">座位 ' + h.seat;
        if (h.exposed && h.exposed.length) {
            html += '<span class="settle-badge">含副露</span>';
        }
        html += '</div><div class="settle-hand-cards" data-seat="' + h.seat + '"></div></div>';
    }
    setTimeout(function () { fillSettleHandCards(hands); }, 0);
    return html;
}

function fillSettleHandCards(hands) {
    for (var i = 0; i < hands.length; i++) {
        var h = hands[i];
        var box = document.querySelector('.settle-hand-cards[data-seat="' + h.seat + '"]');
        if (!box) continue;
        box.innerHTML = '';
        var tiles = h.handTiles || [];
        for (var t = 0; t < tiles.length; t++) {
            box.appendChild(MahjongTile.createTileEl(tiles[t], { small: true }));
        }
        var exposed = h.exposed || [];
        for (var e = 0; e < exposed.length; e++) {
            var parsed = parseExposedType(exposed[e].type);
            appendExposedSet(box, {
                kind: parsed.kind,
                tiles: exposed[e].tileIds || [],
                fromSeat: parsed.fromSeat
            }, { ownerSeat: h.seat, revealAnGang: true });
        }
    }
}

function showSettle(title, meta, rowsHtml, handsHtml) {
    GameTable.showSettle({
        title: title,
        meta: meta,
        rowsHtml: rowsHtml,
        handsHtml: handsHtml || '',
        autoNext: gameState.autoNextRound
    });
}

function handleNotGameResult(data) {
    if (!data) return;
    var title = '总结算';
    var meta = '完成 ' + (data.completedRounds || 0) + ' / ' + (data.totalRounds || 0) + ' 局';
    var rows = '';
    var totals = data.totalScores || [];
    for (var i = 0; i < totals.length; i++) {
        rows += '<div class="row"><span>座位 ' + totals[i].seat + '</span><span>'
            + totals[i].score + ' 分</span></div>';
    }
    showSettle(title, meta, rows, '');
    showActionButtons('prepare');
}

function closeSettle() { GameTable.closeSettle(); }
function backToLobby() { GameTable.backToLobby(); }
function exitRoom() { GameTable.exitRoom(sendWsMessage); }
