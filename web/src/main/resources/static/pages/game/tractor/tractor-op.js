/** 拖拉机操作与推送：本墩四人门口留牌，末家出完停顿，下一家首出再清 */

function roleIdBySeat(seat) {
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].position === seat) return gameState.players[i].roleId;
    }
    return 0;
}

function clearKillEffects() {
    document.querySelectorAll('.kill-fx, .trick-win-fx').forEach(function(el) {
        el.remove();
    });
}

function showKillEffect(roleId) {
    var target = playedTargetForRole(roleId);
    if (!target) return;
    var fx = document.createElement('div');
    fx.className = 'kill-fx';
    fx.textContent = '杀';
    target.appendChild(fx);
    showCenterMsg('主杀！', 900);
}

function showTrickWinEffect(roleId, killed) {
    var target = playedTargetForRole(roleId);
    if (target) {
        var fx = document.createElement('div');
        fx.className = 'trick-win-fx' + (killed ? ' killed' : '');
        fx.textContent = killed ? '杀进' : '大';
        target.appendChild(fx);
    }
    showCenterMsg(killed ? '主杀吃进' : '本墩最大', 1000);
}

function handleAckOp(data) {
    gameState.opPending = false;
    gameState.defenderScore = data.baseScore || 0;
    updateTrumpMeta(data.robMultiplier, data.bombMultiplier);
    var cards = data.cards || [];
    var choice = data.choice;
    var mult = data.currentMultiplier || 0;

    if (choice === 0 || choice === 3 || choice === 4) {
        showPassHint(data.opFrom);
        return;
    }
    if (choice === 1 || choice === 2) {
        gameState.bottomCards = [];
        renderDizhuCards([]);
        if (cards.length) {
            renderPlayedCards(data.opFrom, cards);
            showCenterMsg(choice === 1 ? '亮主' : '反主', 1200);
        }
        if (data.opFrom) gameState.landlordId = data.opFrom;
        sortHandCards(gameState.myCards);
        renderMyCards();
        return;
    }
    if (choice === 13) {
        showCenterMsg('扣底完成，开始出牌', 1200);
        return;
    }
    if (!cards.length) return;
    if (data.opFrom === userId && gameState.pendingPlayCount > 1 && cards.length === 1) {
        var playedMeta = cardMeta(cards[0]);
        var playedName = playedMeta.joker ? playedMeta.label : playedMeta.suit + playedMeta.rank;
        showCenterMsg('甩牌失败，已出最小的 ' + playedName, 2600);
    }
    if (data.opFrom === userId) gameState.pendingPlayCount = 0;

    // 上一墩四家已出完：等本墩首家出牌时再清门口
    if (gameState.trickDone) {
        clearAllPlayedAreas();
        clearKillEffects();
        gameState.trickPlays = {};
        gameState.trickCount = 0;
        gameState.trickDone = false;
    }

    gameState.trickPlays[data.opFrom] = cards.slice();
    gameState.trickCount = (gameState.trickCount || 0) + 1;
    renderPlayedCards(data.opFrom, cards);
    gameState.lastPlayedCards = cards.slice();

    if (mult === 2) showKillEffect(data.opFrom);

    if (data.opFrom === userId) {
        for (var i = 0; i < cards.length; i++) {
            var idx = gameState.myCards.indexOf(cards[i]);
            if (idx >= 0) gameState.myCards.splice(idx, 1);
        }
        gameState.selectedCards.clear();
        gameState.selectedCardIndexes.clear();
        renderMyCards();
    } else {
        var prev = gameState.opponentCounts[data.opFrom] || 0;
        gameState.opponentCounts[data.opFrom] = Math.max(0, prev - cards.length);
        renderOpponentHands();
    }

    // 第四家出完：牌留在各家门口停顿，等下一墩首出再清
    if (mult >= 100) {
        var killed = mult >= 200;
        var winSeat = killed ? (mult - 200) : (mult - 100);
        showTrickWinEffect(roleIdBySeat(winSeat) || data.opFrom, killed);
        gameState.trickDone = true;
    }
}

function handleNotOp(data) {
    if (!data) return;
    var opSeat = data.opSeat;
    highlightActivePlayer(opSeat, data.wait);
    var choices = data.choice || [];
    var hasPlay = false, hasPass = false, hasPrepare = false;
    for (var i = 0; i < choices.length; i++) {
        if (choices[i].choice === 6) hasPlay = true;
        if (choices[i].choice === 0) hasPass = true;
        if (choices[i].choice === 7) hasPrepare = true;
    }
    // 拖拉机每轮必出、无过牌：不要在 notOp 时清桌面（本墩四人牌要留着）
    if (hasPlay && hasPass) {
        clearAllPlayedAreas();
        clearPassHints();
    }
    if (hasPrepare) {
        showActionButtons('prepare');
        return;
    }
    if (gameState.myPosition >= 0 && opSeat === gameState.myPosition) {
        gameState.lastChoices = choices;
        if (!choices.length) hideActions();
        else showOperationChoices(choices);
    } else if (!gameState.dealing) {
        hideActions();
    }
}

function handleNotState(data) {
    if (data && data.state === TABLE_STATE_DIS) {
        gameWs.stopReconnect();
        window.location.href = appUrl('/pages/home/room.html');
        return;
    }
    var labels = { 1: '游戏进行中', 2: '发牌中', 3: '亮主中', 4: '亮主中', 5: '扣底中', 6: '扣底中', 10: '小结算' };
    var label = labels[data.state];
    document.getElementById('tableState').textContent = label || '等待中';
    gameState.dealing = data.state === 2;
    if (!label) {
        stopDealAnim();
        gameState.trumpSuit = 0;
        gameState.trickPlays = {};
        gameState.trickCount = 0;
        gameState.trickDone = false;
        clearAllPlayedAreas();
        clearPassHints();
        clearKillEffects();
        gameState.bottomCards = [];
        document.getElementById('dizhuCards').innerHTML = '';
        gameState.landlordId = 0;
    }
}

function doOp(choice) {
    if (gameState.opPending) return;
    var selected = [];
    gameState.selectedCardIndexes.forEach(function(cardIndex) {
        if (gameState.myCards[cardIndex] != null) {
            selected.push({ value: gameState.myCards[cardIndex] });
        }
    });
    if (choice === 6 && selected.length === 0) {
        showCenterMsg('请先选择要出的牌');
        return;
    }
    if ((choice === 1 || choice === 2) && selected.length === 0) {
        showCenterMsg(choice === 1 ? '请选级牌亮主' : '请选对子/王反主');
        return;
    }
    if (choice === 13 && selected.length !== 8) {
        showCenterMsg('请选择正好 8 张牌放回地下');
        return;
    }

    gameState.opPending = true;
    if (choice === 6) gameState.pendingPlayCount = selected.length;
    sendWsMessage('op', {
        choice: choice,
        cards: selected.length > 0 ? selected : undefined
    }, function(resp) {
        if (resp.code !== 0) {
            gameState.opPending = false;
            gameState.pendingPlayCount = 0;
            if (choice === 6 && selected.length > 2 && Number(resp.code) === 13) {
                showCenterMsg(throwFailHint(selected), 2600);
            } else {
                showCenterMsg(resp.msg || '操作失败');
            }
            if (gameState.lastChoices && gameState.lastChoices.length) {
                showOperationChoices(gameState.lastChoices);
            }
            return;
        }
        if (choice === 1 || choice === 2 || choice === 13) {
            gameState.selectedCards.clear();
            gameState.selectedCardIndexes.clear();
            renderMyCards();
        }
    });
    hideActions();
}

function throwFailHint(selected) {
    var ids = selected.map(function(card) { return Number(card.value); });
    ids.sort(function(a, b) {
        return tractorHandOrder(a, gameState.levelRank || 15, gameState.trumpSuit || 0)
            - tractorHandOrder(b, gameState.levelRank || 15, gameState.trumpSuit || 0);
    });
    var meta = cardMeta(ids[0]);
    var name = meta.joker ? meta.label : meta.suit + meta.rank;
    return '甩牌失败，请出所选牌中最小的 ' + name;
}

function doPrepare() { GameTable.doPrepare(sendWsMessage); }

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
        gameState.skipDealAnim = true;
        applyDdzSnapshot(resp.data);
        showCenterMsg('牌桌已刷新', 1000);
    });
}

function applyDdzSnapshot(s) {
    stopDealAnim();
    updatePlayers(s.players || []);
    gameState.myCards = [];
    gameState.opponentCounts = {};
    (s.players || []).forEach(function (p) {
        if (p.roleId === userId) gameState.myCards = (p.cards || []).slice();
        else gameState.opponentCounts[p.roleId] = p.cardCount || 0;
    });
    gameState.defenderScore = s.baseScore || 0;
    updateTrumpMeta(s.robMultiplier, s.bombMultiplier);
    gameState.dealing = s.state === 2;
    gameState.skipDealAnim = true;
    sortHandCards(gameState.myCards);
    gameState.selectedCards.clear();
    gameState.selectedCardIndexes.clear();
    gameState.dealFlashIds = [];
    gameState.bottomCards = (s.bottomCards || []).slice();
    gameState.lastPlayedCards = (s.lastCards || []).slice();
    gameState.landlordId = 0;
    (s.players || []).forEach(function (p) {
        if (p.position === s.landlordSeat) gameState.landlordId = p.roleId;
    });

    // 恢复本墩四人门口出牌
    gameState.trickPlays = {};
    gameState.trickCount = 0;
    gameState.trickDone = false;
    clearAllPlayedAreas();
    clearKillEffects();
    var exposed = s.exposed || [];
    for (var e = 0; e < exposed.length; e++) {
        var ex = exposed[e];
        if (!ex || (ex.type && ex.type !== 'trick')) continue;
        var rid = roleIdBySeat(ex.seat);
        var ids = ex.tileIds || [];
        if (rid && ids.length) {
            gameState.trickPlays[rid] = ids.slice();
            gameState.trickCount++;
            renderPlayedCards(rid, ids);
        }
    }
    if (!gameState.trickCount && gameState.lastPlayedCards.length) {
        var actor = 0;
        (s.players || []).forEach(function (p) { if (p.position === s.lastPlaySeat) actor = p.roleId; });
        if (actor) renderPlayedCards(actor, gameState.lastPlayedCards);
    }

    renderDizhuCards(gameState.bottomCards);
    renderMyCards();
    renderOpponentHands();
    highlightActivePlayer(s.opSeat, snapshotOperationWait(s));
    gameState.opPending = false;
    gameState.skipDealAnim = false;
    if (s.opSeat === gameState.myPosition) showOperationChoices(s.choices || []); else hideActions();
}

function backToLobby() { GameTable.backToLobby(); }
function exitRoom() { GameTable.exitRoom(sendWsMessage); }
