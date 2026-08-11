/** 斗地主操作与推送处理 */
function sortHandCards(cards) {
    sortPokerByValue(cards);
}

function handleNotCard(data) {
    GameTable.noteRoundStarted();
    // 发牌通知：自己有牌值；roleId=0 为桌面底牌；他人牌值为0但张数有效
    if (!data || !data.nCards) return;
    gameState.myCards = [];
    gameState.opponentCounts = {};
    var bottom = [];
    var maxCount = 0;
    var landlordCandidate = 0;
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
        if (nc.cards.length > maxCount) {
            maxCount = nc.cards.length;
            landlordCandidate = nc.roleId;
        }
    }
    if (bottom.length) {
        gameState.bottomCards = bottom;
        renderDizhuCards(bottom);
    }
    // 20 张视为地主（17+3 底牌）
    if (maxCount >= 20 && landlordCandidate) {
        gameState.landlordId = landlordCandidate;
        renderDizhuCards(bottom);
    }
    sortHandCards(gameState.myCards);
    gameState.selectedCards.clear();
    renderMyCards();
    renderOpponentHands();
    showCenterMsg(bottom.length ? '地主亮牌' : '发牌完成');
}

/** 出牌确认：只保留最后一手；过牌显示「不要」；同步手牌张数 */
function handleAckOp(data) {
    gameState.opPending = false;
    if (data.currentMultiplier) {
        document.getElementById('multipleInfo').textContent = '底分 ' + (data.baseScore || 1)
            + ' · 叫抢×' + (data.robMultiplier || 1) + ' · 炸弹×' + (data.bombMultiplier || 1)
            + ' · 当前×' + data.currentMultiplier;
    }
    var cards = data.cards || [];
    var choice = data.choice;
    var bidHints = {
        1: '叫地主', 2: '抢地主', 3: '不叫', 4: '不抢',
        9: '叫 1 分', 10: '叫 2 分', 11: '叫 3 分'
    };
    if (Object.prototype.hasOwnProperty.call(bidHints, choice)) {
        showBidHint(data.opFrom, bidHints[choice]);
        if (choice === 1 || choice === 2 || choice >= 9) {
            showCenterMsg(findPlayerName(data.opFrom) + bidHints[choice], 1200);
        }
        return;
    }
    if (choice === 0) {
        showPassHint(data.opFrom);
        return;
    }
    if (!cards.length) return;
    rememberPreviousHand(gameState.lastPlayRoleId, gameState.lastPlayedCards);
    clearAllPlayedAreas();
    clearPassHints();
    renderPlayedCards(data.opFrom, cards);
    gameState.lastPlayRoleId = data.opFrom;
    gameState.lastPlayedCards = cards.slice();
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
    // 仅有出牌、无可过：新一轮首出，清掉桌面上一手牌
    var hasPlay = false, hasPass = false, hasPrepare = false;
    for (var i = 0; i < choices.length; i++) {
        if (choices[i].choice === 6) hasPlay = true;
        if (choices[i].choice === 0) hasPass = true;
        if (choices[i].choice === 7) hasPrepare = true;
    }
    if (hasPlay && !hasPass) {
        clearAllPlayedAreas();
        clearPassHints();
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
    if (data) GameTable.renderRoundInfo(data.currentRound, data.totalRounds);
    if (data && data.state === TABLE_STATE_DIS) {
        GameTable.handleTableDestroyed(gameWs);
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
    if (data.landlord_id) gameState.landlordId = data.landlord_id;
    var title = data.win_team === 0 ? '地主获胜' : '农民获胜';
    if (data.spring) title += ' · 春天';
    if (data.anti_spring) title += ' · 反春';
    var meta = '底分 ' + (data.base_score || 0)
        + ' · 抢地主×' + (data.rob_multiplier || 1)
        + ' · 结算系数 ' + (data.settle_factor || 0);
    var rows = '';
    var players = data.rPlayers || [];
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        var tag = (p.roleId === data.landlord_id) ? '地主' : '农民';
        var name = findPlayerName(p.roleId);
        rows += '<div class="row"><span>' + name + '（' + tag + '）</span>'
            + '<span>余牌 ' + ((p.cards && p.cards.length) || 0) + '</span></div>';
    }
    showSettle(title, meta, rows, players, data.landlord_id);
    showCenterMsg(title, 2500);
    showActionButtons('prepare');
}

function handleDdzRoundResult(data) {
    GameTable.noteRoundCompleted(data && data.round);
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
    GameTable.showScoreFinal(data, gameWs);
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
