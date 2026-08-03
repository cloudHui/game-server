/** 跑得快操作与推送处理 */
function sortHandCards(cards) {
    sortPokerByValue(cards);
}

function handleNotCard(data) {
    // 发牌通知：自己有牌值；roleId=0 为桌面底牌；他人牌值为0但张数有效
    if (!data || !data.nCards) return;
    var prevLen = gameState.myCards.length;
    gameState.myCards = [];
    gameState.opponentCounts = {};
    var bottom = [];
    var maxCount = 0;
    for (var i = 0; i < data.nCards.length; i++) {
        var nc = data.nCards[i];
        if (!nc.cards) continue;
        if (nc.roleId === 0) {
            for (var b = 0; b < nc.cards.length; b++) {
                if (nc.cards[b].value) bottom.push(nc.cards[b].value);
            }
            continue;
        }
        if (nc.roleId === userId) {
            for (var j = 0; j < nc.cards.length; j++) {
                gameState.myCards.push(nc.cards[j].value);
            }
        } else {
            gameState.opponentCounts[nc.roleId] = nc.cards.length;
        }
        if (nc.cards.length > maxCount) maxCount = nc.cards.length;
    }
    if (bottom.length) {
        gameState.bottomCards = bottom;
        renderDizhuCards(bottom);
    }
    sortHandCards(gameState.myCards);
    gameState.selectedCards.clear();
    // 断线重连/刷新：已有手牌则不再提示发牌完成
    renderMyCards();
    renderOpponentHands();
    if (prevLen === 0 && gameState.myCards.length >= 16) {
        showCenterMsg('发牌完成');
    }
}

/** 出牌确认：只保留最后一手；过牌显示「不要」；同步手牌张数 */
function handleAckOp(data) {
    gameState.opPending = false;
    document.getElementById('multipleInfo').textContent = '跑得快';
    var cards = data.cards || [];
    var choice = data.choice;
    if (choice === 0) {
        showPassHint(data.opFrom);
        return;
    }
    if (!cards.length) return;
    clearAllPlayedAreas();
    clearPassHints();
    renderPlayedCards(data.opFrom, cards);
    gameState.lastPlayedCards = cards.slice();
    // 记录最大出牌座位：有人全过后再轮到他首出时才清桌面
    for (var p = 0; p < gameState.players.length; p++) {
        if (gameState.players[p].roleId === data.opFrom) {
            gameState.lastPlaySeat = gameState.players[p].position;
            break;
        }
    }
    if (data.opFrom === userId) {
        for (var i = 0; i < cards.length; i++) {
            var idx = gameState.myCards.indexOf(cards[i]);
            if (idx >= 0) gameState.myCards.splice(idx, 1);
        }
        gameState.selectedCards.clear();
        renderMyCards();
    } else {
        var prev = gameState.opponentCounts[data.opFrom] || 0;
        gameState.opponentCounts[data.opFrom] = Math.max(0, prev - cards.length);
        renderOpponentHands();
    }
}

function handleNotOp(data) {
    if (!data) return;
    var opSeat = data.opSeat;
    highlightActivePlayer(opSeat, data.wait);
    var choices = data.choice || [];
    // 操作按钮完全由服务器下发：管不上仅不出；首出/能管仅出牌。
    // 清牌时机：仅当又轮到上一手最大牌玩家首出时清桌面；普通人能管必管时保留最大牌。
    var hasPlay = false, hasPass = false, hasPrepare = false;
    for (var i = 0; i < choices.length; i++) {
        if (choices[i].choice === 6) hasPlay = true;
        if (choices[i].choice === 0) hasPass = true;
        if (choices[i].choice === 7) hasPrepare = true;
    }
    if (hasPlay && !hasPass) {
        var hadLast = !!(gameState.lastPlayedCards && gameState.lastPlayedCards.length);
        var isNewLead = !hadLast
            || (typeof gameState.lastPlaySeat === 'number' && gameState.lastPlaySeat >= 0
                && opSeat === gameState.lastPlaySeat);
        if (isNewLead) {
            clearAllPlayedAreas();
            clearPassHints();
            gameState.lastPlayedCards = [];
        }
    }
    if (hasPrepare) {
        showActionButtons('prepare');
        return;
    }
    if (gameState.myPosition >= 0 && opSeat === gameState.myPosition) {
        gameState.lastChoices = choices;
        showOperationChoices(choices);
    } else {
        hideActions();
    }
}

function handleNotState(data) {
    if (data && data.state === TABLE_STATE_DIS) {
        gameWs.stopReconnect();
        GameTable.backToLobby();
        return;
    }
    if (data.state === 1) {
        document.getElementById('tableState').textContent = '游戏进行中';
    } else if (data.state === 10) {
        document.getElementById('tableState').textContent = '小结算';
    } else {
        document.getElementById('tableState').textContent = '等待中';
        clearAllPlayedAreas();
        clearPassHints();
        gameState.bottomCards = [];
        document.getElementById('dizhuCards').innerHTML = '';
        gameState.landlordId = 0;
    }
}

function handleNotResult(data) {
    if (!data) return;
    var title = '头游：' + findPlayerName(data.winner);
    var meta = '结算分 ' + (data.settle_factor || 0)
        + (data.spring ? ' · 有人被关' : '');
    var rows = '';
    var players = data.rPlayers || [];
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        var name = findPlayerName(p.roleId);
        rows += '<div class="row"><span>' + name + '</span>'
            + '<span>余牌 ' + ((p.cards && p.cards.length) || 0) + '</span></div>';
    }
    showSettle(title, meta, rows, players, 0);
    showCenterMsg(title, 2500);
    // 始终可点准备；有人准备后服务端会让机器人自动准备
    showActionButtons('prepare');
}

function handlePdkRoundResult(data) {
    if (!data) return;
    var totals = data.totalScores || [];
    for (var i = 0; i < totals.length; i++) {
        for (var j = 0; j < gameState.players.length; j++) {
            if (gameState.players[j].position === totals[i].seat) {
                gameState.players[j].totalScore = totals[i].score;
            }
        }
    }
    renderPlayerLabels();
    renderOpponentHands();
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
    showSettle(title, meta, rows);
    showActionButtons('prepare');
}

function doOp(choice) {
    if (gameState.opPending) return;
    var selected = [];
    gameState.selectedCards.forEach(function (cardValue) {
        selected.push({value: cardValue});
    });
    if (choice === 6 && selected.length === 0) {
        showCenterMsg('请先选择要出的牌');
        return;
    }

    gameState.opPending = true;
    sendWsMessage('op', {
        choice: choice,
        cards: selected.length > 0 ? selected : undefined
    }, function (resp) {
        if (resp.code !== 0) {
            gameState.opPending = false;
            showCenterMsg(resp.msg || '操作失败');
            // 失败时保留选牌，并恢复刚才的操作按钮
            if (gameState.lastChoices && gameState.lastChoices.length) {
                showOperationChoices(gameState.lastChoices);
            }
            return;
        }
    });
    hideActions();
}

function doPrepare() {
    GameTable.doPrepare(sendWsMessage);
}

function refreshTable() {
    var btn = document.getElementById('refreshTableBtn');
    if (!btn || btn.disabled) return;
    btn.disabled = true;
    btn.textContent = '刷新中';
    sendWsMessage('refreshTable', {tableId: tableId}, function (resp) {
        btn.disabled = false;
        btn.textContent = '刷新牌桌';
        if (resp.code !== 0 || !resp.data) {
            showCenterMsg(resp.msg || '刷新牌桌失败');
            return;
        }
        applyDdzSnapshot(resp.data);
        showCenterMsg('牌桌已刷新', 1000);
    });
}

function applyDdzSnapshot(s) {
    updatePlayers(s.players || []);
    gameState.myCards = [];
    gameState.opponentCounts = {};
    (s.players || []).forEach(function (p) {
        if (p.roleId === userId) gameState.myCards = (p.cards || []).slice();
        else gameState.opponentCounts[p.roleId] = p.cardCount || 0;
    });
    sortHandCards(gameState.myCards);
    gameState.selectedCards.clear();
    gameState.bottomCards = (s.bottomCards || []).slice();
    gameState.lastPlayedCards = (s.lastCards || []).slice();
    gameState.lastPlaySeat = typeof s.lastPlaySeat === 'number' ? s.lastPlaySeat : -1;
    gameState.landlordId = 0;
    (s.players || []).forEach(function (p) {
        if (p.position === s.landlordSeat) gameState.landlordId = p.roleId;
    });
    clearAllPlayedAreas();
    clearPassHints();
    if (gameState.lastPlayedCards.length) {
        var actor = 0;
        (s.players || []).forEach(function (p) {
            if (p.position === s.lastPlaySeat) actor = p.roleId;
        });
        renderPlayedCards(actor, gameState.lastPlayedCards);
    }
    (s.passSeats || []).forEach(function (seat) {
        (s.players || []).forEach(function (p) {
            if (p.position === seat) showPassHint(p.roleId);
        });
    });
    renderDizhuCards(gameState.bottomCards);
    renderMyCards();
    renderOpponentHands();
    highlightActivePlayer(s.opSeat, snapshotOperationWait(s));
    document.getElementById('multipleInfo').textContent = '底分 ' + (s.baseScore || 1)
        + ' · 叫抢×' + (s.robMultiplier || 1) + ' · 炸弹×' + (s.bombMultiplier || 1)
        + ' · 当前×' + (s.currentMultiplier || 1);
    gameState.opPending = false;
    if (s.opSeat === gameState.myPosition) showOperationChoices(s.choices || []); else hideActions();
}

function backToLobby() {
    GameTable.backToLobby();
}

function exitRoom() {
    GameTable.exitRoom(sendWsMessage);
}
