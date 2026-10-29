/**
 * 牛牛操作与阶段控制器（复用 shared/poker-view.js 座位、手牌、倒计时与结算渲染）。
 */
var STATE_LABELS = {1: '等待准备', 3: '明牌抢庄', 4: '明牌抢庄', 6: '闲家加倍', 7: '拼牛亮牌', 10: '本局结算'};
var ROB_CHOICES = [{choice: 20}, {choice: 21}, {choice: 22}, {choice: 23}, {choice: 24}];
var BET_CHOICES = [{choice: 25}, {choice: 26}, {choice: 27}, {choice: 28}, {choice: 29}];
var SHOW_CHOICES = [{choice: 30}];

window.pokerSortSettleCards = function () {};
window.pokerOpChoiceMap = {
    20: {cls: 'btn-pass', text: '不抢'},
    21: {cls: 'btn-call', text: '1倍抢'},
    22: {cls: 'btn-call', text: '2倍抢'},
    23: {cls: 'btn-rob', text: '3倍抢'},
    24: {cls: 'btn-rob', text: '4倍抢'},
    25: {cls: 'btn-call', text: '下1倍'},
    26: {cls: 'btn-call', text: '下2倍'},
    27: {cls: 'btn-call', text: '下3倍'},
    28: {cls: 'btn-rob', text: '下4倍'},
    29: {cls: 'btn-rob', text: '下5倍'},
    30: {cls: 'btn-play', text: '亮牌'}
};

function roleBadgeHtml(roleId) {
    if (!gameState.landlordId) return '';
    if (roleId === gameState.landlordId) {
        return '<span class="avatar-mark landlord">庄</span>'
            + '<span class="role-badge landlord">庄×' + (gameState.robMultiplier || 1) + '</span>';
    }
    var bet = gameState.betMultipliers[roleId];
    return '<span class="avatar-mark farmer">闲</span>'
        + '<span class="role-badge farmer">' + (bet ? ('闲×' + bet) : '闲') + '</span>';
}

function pokerSettleTag(roleId, landlordId) {
    return roleId === landlordId ? '庄' : '闲';
}

function renderStageHints() {
    if (!gameState.players.length) return;
    var st = gameState.currentState;
    if (st === 3 || st === 4) {
        Object.keys(gameState.robMultipliers).forEach(function (rid) {
            var rm = gameState.robMultipliers[rid];
            showBidHint(Number(rid), rm > 0 ? ('抢×' + rm) : '不抢');
        });
    } else if (st === 6) {
        Object.keys(gameState.betMultipliers).forEach(function (rid) {
            showBidHint(Number(rid), '下' + gameState.betMultipliers[rid] + '倍');
        });
    } else if (st === 7) {
        gameState.showedPlayers.forEach(function (rid) {
            showBidHint(rid, '已亮牌');
        });
    }
}

function updatePlayers(players) {
    gameState.players = players || [];
    gameState.seatNum = 4;
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].roleId === userId) {
            gameState.myPosition = gameState.players[i].position;
            break;
        }
    }
    renderOpponentHands();
    renderStageHints();
}

function updateMultipleInfo() {
    var el = document.getElementById('multipleInfo');
    if (!el) return;
    var base = gameState.baseScore || 1;
    if (!gameState.landlordId) {
        el.textContent = '底分 ' + base + ' ｜ 明牌抢庄';
        return;
    }
    var myBet = gameState.betMultipliers[userId];
    var betPart = (userId !== gameState.landlordId && myBet) ? (' ｜ 我的下注 ×' + myBet) : '';
    el.textContent = '底分 ' + base + ' ｜ 庄家 ×' + (gameState.robMultiplier || 1) + betPart;
}

function syncMyCardsAndSplit() {
    var split = false;
    if (gameState.myCards.length === 5) {
        var ev = NiuniuRule.evaluate(gameState.myCards);
        gameState.myCards = ev.orderedCards.slice();
        split = ev.hasGroupSplit;
    }
    renderMyCards();
    if (split) {
        var cards = document.querySelectorAll('#myCards .card');
        if (cards[3]) cards[3].classList.add('niu-gap');
    }
}

function showWaitingActions() {
    if (gameState.autoNextRound) {
        showActionButtons('waiting');
        return;
    }
    var bar = document.getElementById('actionBar');
    bar.innerHTML = '';
    bar.style.display = 'flex';
    if (!gameState.myReady) {
        var readyBtn = document.createElement('button');
        readyBtn.className = 'action-btn btn-prepare';
        readyBtn.textContent = '准备';
        readyBtn.onclick = doPrepare;
        bar.appendChild(readyBtn);
    }
    if (gameState.players.length < 4) {
        var botBtn = document.createElement('button');
        botBtn.className = 'action-btn btn-call';
        botBtn.textContent = '+ 添加机器人';
        botBtn.onclick = addRobot;
        bar.appendChild(botBtn);
    }
    if (gameState.myReady) {
        var tip = document.createElement('span');
        tip.style.cssText = 'color:#fff;font-size:14px;padding:6px 10px;';
        tip.textContent = '已准备，满2人全员准备即开局';
        bar.appendChild(tip);
    }
}

function syncStageActions(waitSec) {
    stopOperationCountdown();
    var st = gameState.currentState;
    if (st === 1) {
        showWaitingActions();
        return;
    }
    if (waitSec > 0) {
        startOperationCountdown(document.getElementById('playerBottom'), waitSec);
    }
    if ((st === 3 || st === 4) && gameState.robMultipliers[userId] == null) {
        showOperationChoices(ROB_CHOICES);
    } else if (st === 6 && userId !== gameState.landlordId && gameState.betMultipliers[userId] == null) {
        showOperationChoices(BET_CHOICES);
    } else if (st === 7 && !gameState.showedPlayers.has(userId)) {
        showOperationChoices(SHOW_CHOICES);
    } else {
        hideActions();
    }
}

function handleNotCard(data) {
    GameTable.noteRoundStarted();
    if (!data || !data.nCards) return;
    gameState.myCards = [];
    gameState.opponentCounts = {};
    for (var i = 0; i < data.nCards.length; i++) {
        var nc = data.nCards[i];
        if (!nc.cards || !nc.roleId) continue;
        if (nc.roleId === userId) {
            gameState.myCards = nc.cards.map(function (c) { return c.value; });
        } else {
            gameState.opponentCounts[nc.roleId] = nc.cards.length;
        }
    }
    syncMyCardsAndSplit();
    renderOpponentHands();
}

function handleNotState(data) {
    if (!data) return;
    GameTable.renderRoundInfo(data.currentRound, data.totalRounds);
    if (data.state === TABLE_STATE_DIS) {
        GameTable.handleTableDestroyed(gameWs);
        return;
    }
    gameState.currentState = data.state;
    gameState.opPending = false;
    document.getElementById('tableState').textContent = STATE_LABELS[data.state] || '进行中';

    if (data.state === 1) {
        closeSettle();
        clearAllPlayedAreas();
        gameState.landlordId = 0;
        gameState.robMultiplier = 1;
        gameState.robMultipliers = {};
        gameState.betMultipliers = {};
        gameState.showedPlayers.clear();
        gameState.myReady = false;
        updateMultipleInfo();
        renderOpponentHands();
    } else if (data.state === 3) {
        closeSettle();
        clearAllPlayedAreas();
        gameState.landlordId = 0;
        gameState.robMultipliers = {};
        gameState.betMultipliers = {};
        gameState.showedPlayers.clear();
        updateMultipleInfo();
        showCenterMsg('明牌抢庄', 1000);
    } else if (data.state === 4 || data.state === 6 || data.state === 7) {
        clearAllPlayedAreas();
        renderStageHints();
    }
    syncStageActions(Number(data.wait || 0));
}

function handleAckOp(data) {
    gameState.opPending = false;
    var ch = data.choice;
    var actor = data.opFrom;
    if (ch === 2) {
        gameState.landlordId = actor;
        gameState.robMultiplier = data.robMultiplier || 1;
        if (data.baseScore) gameState.baseScore = data.baseScore;
        updateMultipleInfo();
        renderOpponentHands();
        if (actor === userId && gameState.currentState === 6) hideActions();
        if (gameState.players.length) {
            showCenterMsg(findPlayerName(actor) + ' 当庄 ×' + gameState.robMultiplier, 1200);
        }
        return;
    }
    if (ch >= 20 && ch <= 24) {
        var rm = ch - 20;
        gameState.robMultipliers[actor] = rm;
        showBidHint(actor, rm > 0 ? ('抢×' + rm) : '不抢');
    } else if (ch >= 25 && ch <= 29) {
        var bm = ch - 24;
        gameState.betMultipliers[actor] = bm;
        updateMultipleInfo();
        renderOpponentHands();
        showBidHint(actor, '下' + bm + '倍');
    } else if (ch === 30) {
        gameState.showedPlayers.add(actor);
        showBidHint(actor, '已亮牌');
    }
    if (actor === userId) hideActions();
}

function renderShowdownArea(roleId, ev) {
    renderPlayedCards(roleId, ev.orderedCards);
    var target = playedTargetForRole(roleId);
    if (!target) return;
    if (ev.hasGroupSplit) {
        var faces = target.querySelectorAll('.played-face');
        if (faces[3]) faces[3].classList.add('niu-gap');
    }
    var badge = document.createElement('span');
    badge.className = 'niu-badge' + (ev.type === 0 ? ' none' : '');
    badge.textContent = ev.name + (ev.multiplier > 1 ? (' ×' + ev.multiplier) : '');
    target.appendChild(badge);
}

function handleNotRoundResult(data) {
    GameTable.noteRoundCompleted(data && data.round);
    if (!data) return;
    stopOperationCountdown();
    hideActions();

    var seatPlayer = {};
    gameState.players.forEach(function (p) { seatPlayer[p.position] = p; });
    (data.totalScores || []).forEach(function (ts) {
        if (seatPlayer[ts.seat]) seatPlayer[ts.seat].totalScore = ts.score;
    });
    if (data.winnerSeat != null && seatPlayer[data.winnerSeat]) {
        gameState.landlordId = seatPlayer[data.winnerSeat].roleId;
    }
    if (data.fan) gameState.robMultiplier = data.fan;
    renderOpponentHands();

    var roundScoreBySeat = {};
    (data.seatScores || []).forEach(function (ss) { roundScoreBySeat[ss.seat] = ss.score; });

    var rows = '';
    var remainPlayers = [];
    var splitFlags = [];
    (data.hands || []).forEach(function (h) {
        var p = seatPlayer[h.seat];
        if (!p) return;
        var ev = NiuniuRule.evaluate(h.handTiles || []);
        renderShowdownArea(p.roleId, ev);
        remainPlayers.push({roleId: p.roleId, cards: ev.orderedCards});
        splitFlags.push(ev.hasGroupSplit);
        var sc = Number(roundScoreBySeat[h.seat] || 0);
        var tag = p.roleId === gameState.landlordId ? '庄' : '闲';
        rows += '<div class="row"><span>' + findPlayerName(p.roleId) + '（' + tag + ' · ' + ev.name + '×' + ev.multiplier + '）</span>'
            + '<span>' + (sc > 0 ? '+' : '') + sc + '</span></div>';
    });

    var mySeatScore = roundScoreBySeat[gameState.myPosition] || 0;
    var title = mySeatScore > 0 ? '本局获胜' : (mySeatScore < 0 ? '本局失利' : '本局平局');
    var meta = '第 ' + (data.round || 1) + ' 局 ｜ 庄家 ×' + (gameState.robMultiplier || 1);
    showSettle(title, meta, rows, remainPlayers, gameState.landlordId);

    var handRows = document.querySelectorAll('#settleHands .settle-hand-cards');
    for (var i = 0; i < splitFlags.length; i++) {
        if (splitFlags[i] && handRows[i]) {
            var fc = handRows[i].querySelectorAll('.played-face');
            if (fc[3]) fc[3].classList.add('niu-gap');
        }
    }
    if (!gameState.autoNextRound) {
        gameState.myReady = false;
        showActionButtons('prepare');
    }
}

function handleNotGameResult(data) {
    GameTable.showScoreFinal(data, gameWs);
}

function doOp(choice) {
    if (gameState.opPending) return;
    gameState.opPending = true;
    sendWsMessage('op', {choice: choice}, function (resp) {
        if (resp && resp.code !== 0) {
            gameState.opPending = false;
            showCenterMsg(resp.msg || '操作失败');
            syncStageActions(0);
        }
    });
    hideActions();
}

function doPrepare() {
    gameState.myReady = true;
    GameTable.doPrepare(sendWsMessage);
    if (gameState.currentState === 1) showWaitingActions();
}

function addRobot() {
    sendWsMessage('addRobot', {tableId: tableId}, function (resp) {
        if (resp && resp.code !== 0) {
            showCenterMsg(resp.msg || '添加机器人失败');
        }
    });
}

function refreshTable() {
    var btn = document.getElementById('refreshTableBtn');
    if (!btn || btn.disabled) return;
    btn.disabled = true;
    btn.textContent = '刷新中';
    sendWsMessage('refreshTable', {tableId: tableId}, function (resp) {
        btn.disabled = false;
        btn.textContent = '刷新牌桌';
        if (!resp || resp.code !== 0 || !resp.data) {
            showCenterMsg((resp && resp.msg) || '刷新牌桌失败');
            return;
        }
        applyNiuniuSnapshot(resp.data);
        showCenterMsg('牌桌已刷新', 1000);
    });
}

function applyNiuniuSnapshot(s) {
    gameState.myCards = [];
    gameState.opponentCounts = {};
    gameState.landlordId = 0;
    (s.players || []).forEach(function (p) {
        if (p.roleId === userId) gameState.myCards = (p.cards || []).slice();
        else gameState.opponentCounts[p.roleId] = p.cardCount || 0;
        if (s.landlordSeat >= 0 && p.position === s.landlordSeat) gameState.landlordId = p.roleId;
    });
    gameState.baseScore = s.baseScore || 1;
    gameState.robMultiplier = s.robMultiplier || 1;
    if (s.state) gameState.currentState = s.state;
    GameTable.renderRoundInfo(s.currentRound, s.totalRounds);
    document.getElementById('tableState').textContent = STATE_LABELS[gameState.currentState] || '进行中';
    updateMultipleInfo();
    syncMyCardsAndSplit();
    clearAllPlayedAreas();
    updatePlayers(s.players || []);
    syncStageActions(snapshotOperationWait(s));
}

function backToLobby() {
    GameTable.backToLobby();
}

function exitRoom() {
    GameTable.exitRoom(sendWsMessage);
}
