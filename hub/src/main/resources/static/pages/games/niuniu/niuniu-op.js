/**
 * 牛牛操作与消息推送处理。
 * 支持 2~4 人动态人数、明牌抢庄、闲家加倍、添加陪练机器人、拼牛摊牌与闲庄比牌结算。
 */
(function (w) {
    'use strict';

    var TABLE_STATE = {
        WAITING: 1,
        START_ANI: 2,
        ROB: 3,
        IDLE_ROB: 4,
        SHOW_CARD: 5,
        BET: 6, // IDLE_SHOW_CARD 在牛牛中作为闲家加倍阶段
        CARD: 7, // 拼牛摊牌阶段
        IDLE_CARD: 8,
        TABLE_OVER: 10,
        ROUND_OVER: 11
    };

    var STATE_NAMES = {
        1: '等待准备',
        2: '发牌中',
        3: '明牌抢庄',
        4: '抢庄等待',
        6: '闲家加倍',
        7: '拼牛摊牌',
        10: '局间结算',
        11: '等待下一局'
    };

    /** 牛牛操作码（与 const.proto 中 Operation 强类型对齐） */
    var NIU_OP = {
        NO_ROB: 20,
        ROB_1: 21, ROB_2: 22, ROB_3: 23, ROB_4: 24,
        BET_1: 25, BET_2: 26, BET_3: 27, BET_4: 28, BET_5: 29,
        SHOW: 30
    };

    function totalSeats() {
        return Math.max(2, gameState.players.length || 4);
    }

    function slotForSeat(seat) {
        var myPos = gameState.myPosition >= 0 ? gameState.myPosition : 0;
        return TableSeatView.slotFor(seat, myPos, totalSeats());
    }

    function playerByRoleId(roleId) {
        for (var i = 0; i < gameState.players.length; i++) {
            if (gameState.players[i].roleId === roleId) return gameState.players[i];
        }
        return null;
    }

    function playerBySeat(seat) {
        for (var i = 0; i < gameState.players.length; i++) {
            if (gameState.players[i].position === seat) return gameState.players[i];
        }
        return null;
    }

    function renderPlayerLabels() {
        var myPos = gameState.myPosition >= 0 ? gameState.myPosition : 0;
        var seats = totalSeats();
        ['bottom', 'right', 'top', 'left'].forEach(function (slot) {
            var infoEl = document.getElementById(slot === 'bottom' ? 'playerBottom' : ('player' + slot.charAt(0).toUpperCase() + slot.slice(1)));
            if (!infoEl) return;
            var targetPlayer = null;
            for (var i = 0; i < gameState.players.length; i++) {
                var p = gameState.players[i];
                if (TableSeatView.slotFor(p.position, myPos, seats) === slot) {
                    targetPlayer = p;
                    break;
                }
            }
            if (!targetPlayer) {
                infoEl.innerHTML = '<span class="name">等待加入</span>';
                return;
            }

            var html = '<span class="name">' + (targetPlayer.nickName || ('玩家' + targetPlayer.roleId)) + '</span>';
            // 庄家标志
            if (gameState.bankerId && targetPlayer.roleId === gameState.bankerId) {
                html += '<span class="banker-tag">👑 庄(' + (gameState.bankerMultiplier || 1) + '倍)</span>';
            }
            // 抢庄/下注状态标记
            var rob = gameState.robMultipliers[targetPlayer.roleId];
            if (rob != null && !gameState.bankerId) {
                html += '<span class="mult-tag rob">' + (rob > 0 ? ('抢' + rob + '倍') : '不抢') + '</span>';
            }
            var bet = gameState.betMultipliers[targetPlayer.roleId];
            if (bet != null && targetPlayer.roleId !== gameState.bankerId) {
                html += '<span class="mult-tag bet">' + bet + '倍</span>';
            }
            if (gameState.showedPlayers.has(targetPlayer.roleId)) {
                html += '<span class="mult-tag" style="color:#a78bfa;">已摊牌</span>';
            }
            infoEl.innerHTML = html;
        });
    }

    function renderOpponentHands() {
        var myPos = gameState.myPosition >= 0 ? gameState.myPosition : 0;
        var seats = totalSeats();
        ['right', 'top', 'left'].forEach(function (slot) {
            var container = document.getElementById('cards' + slot.charAt(0).toUpperCase() + slot.slice(1));
            if (!container) return;
            container.innerHTML = '';
            var targetPlayer = null;
            for (var i = 0; i < gameState.players.length; i++) {
                var p = gameState.players[i];
                if (TableSeatView.slotFor(p.position, myPos, seats) === slot) {
                    targetPlayer = p;
                    break;
                }
            }
            if (!targetPlayer) return;

            var cards = gameState.opponentCards[targetPlayer.roleId];
            var count = gameState.opponentCounts[targetPlayer.roleId] || (cards ? cards.length : 0);

            if (cards && cards.length && cards[0] > 0) {
                // 亮明的手牌（结算后）
                cards.forEach(function (cid) {
                    container.appendChild(TableSeatView.createPokerCard(cid));
                });
                var evalResult = NiuniuRule.evaluate(cards);
                if (evalResult && evalResult.name) {
                    var badge = document.createElement('div');
                    badge.className = 'niu-badge niu-' + evalResult.level;
                    badge.textContent = evalResult.name;
                    container.appendChild(badge);
                }
            } else if (count > 0) {
                // 暗牌背面
                TableSeatView.renderBacks(container, count, 'poker');
            }
        });
    }

    function renderMyCards() {
        var container = document.getElementById('myCards');
        if (!container) return;
        container.innerHTML = '';
        var cards = gameState.myCards || [];
        cards.forEach(function (cid) {
            container.appendChild(TableSeatView.createPokerCard(cid));
        });

        // 5 张牌时自动展示当前自身牛型
        if (cards.length >= 5) {
            var evalResult = NiuniuRule.evaluate(cards);
            if (evalResult && evalResult.name) {
                var badge = document.createElement('div');
                badge.className = 'niu-badge niu-' + evalResult.level;
                badge.textContent = evalResult.name + ' (' + evalResult.multiplier + '倍)';
                container.appendChild(badge);
            }
        }
    }

    function handleNotCard(data) {
        GameTable.noteRoundStarted();
        if (!data || !data.nCards) return;
        var myCards = [];
        data.nCards.forEach(function (nc) {
            if (!nc.cards) return;
            if (nc.roleId === userId) {
                nc.cards.forEach(function (c) { if (c.value) myCards.push(c.value); });
            } else {
                var oppCards = [];
                nc.cards.forEach(function (c) { oppCards.push(c.value || 0); });
                gameState.opponentCounts[nc.roleId] = nc.cards.length;
                if (oppCards.length && oppCards[0] > 0) {
                    gameState.opponentCards[nc.roleId] = oppCards;
                }
            }
        });
        if (myCards.length) gameState.myCards = myCards;

        renderMyCards();
        renderOpponentHands();
        renderPlayerLabels();
    }

    function handleNotState(data) {
        if (!data) return;
        var state = data.state;
        gameState.currentState = state;
        var stateEl = document.getElementById('tableState');
        if (stateEl) {
            stateEl.textContent = STATE_NAMES[state] || ('状态 ' + state);
        }

        if (state === TABLE_STATE.WAITING || state === 0) {
            showWaitingActions();
        } else if (state === TABLE_STATE.ROB || state === TABLE_STATE.IDLE_ROB || state === 4) {
            showRobActions();
        } else if (state === TABLE_STATE.BET || state === 8) {
            showBetActions();
        } else if (state === TABLE_STATE.CARD || state === 5) {
            showShowActions();
        } else if (state === TABLE_STATE.TABLE_OVER || state === TABLE_STATE.ROUND_OVER || state === 10 || state === 12) {
            showRoundOverActions();
        }
    }

    function handleAckOp(data) {
        gameState.opPending = false;
        var choice = data.choice;
        var opFrom = data.opFrom;

        if (choice === 2 || (choice === 0 && data.robMultiplier != null && gameState.currentState === 8)) {
            // 定庄广播
            gameState.bankerId = opFrom;
            gameState.bankerMultiplier = data.robMultiplier || data.currentMultiplier || 1;
            GameTable.showCenterMsg('庄家已确定: 玩家' + opFrom + ' (' + gameState.bankerMultiplier + '倍)', 1500);
            renderPlayerLabels();
            return;
        }

        // 抢庄操作响应
        if ((choice >= NIU_OP.ROB_1 && choice <= NIU_OP.ROB_4) || choice === NIU_OP.NO_ROB || choice === 1 || choice === 0) {
            var mult = data.robMultiplier != null ? data.robMultiplier : (choice >= NIU_OP.ROB_1 ? choice - NIU_OP.ROB_1 + 1 : 0);
            gameState.robMultipliers[opFrom] = mult;
            renderPlayerLabels();
            return;
        }

        // 闲家下注响应
        if ((choice >= NIU_OP.BET_1 && choice <= NIU_OP.BET_5) || choice === 7) {
            var betMult = data.currentMultiplier != null ? data.currentMultiplier : (choice >= NIU_OP.BET_1 ? choice - NIU_OP.BET_1 + 1 : 1);
            gameState.betMultipliers[opFrom] = betMult;
            renderPlayerLabels();
            return;
        }

        // 拼牛摊牌响应
        if (choice === NIU_OP.SHOW || choice === 6) {
            gameState.showedPlayers.add(opFrom);
            renderPlayerLabels();
            if (opFrom === userId) {
                var bar = document.getElementById('actionBar');
                if (bar) bar.innerHTML = '<span style="color:#e2e8f0;font-size:14px;">已摊牌，等待其他玩家…</span>';
            }
        }
    }

    function handleNotRoundResult(data) {
        if (!data) return;
        GameTable.noteRoundCompleted(data.round);

        // 揭晓所有玩家的手牌 (hands: [{ seat, handTiles }])
        if (data.hands && data.hands.length) {
            data.hands.forEach(function (h) {
                var p = playerBySeat(h.seat);
                if (p && h.handTiles && h.handTiles.length) {
                    if (p.roleId === userId) {
                        gameState.myCards = h.handTiles;
                    } else {
                        gameState.opponentCards[p.roleId] = h.handTiles;
                        gameState.opponentCounts[p.roleId] = h.handTiles.length;
                    }
                }
            });
        }
        renderMyCards();
        renderOpponentHands();

        // 浮动显示单局得分 (seatScores: [{ seat, score }])
        if (data.seatScores && data.seatScores.length) {
            data.seatScores.forEach(function (item) {
                var slot = slotForSeat(item.seat);
                showScoreFloat(slot, item.score);
            });
        }

        if (data.winType) {
            GameTable.showCenterMsg('本局胜者牛型: ' + data.winType, 2000);
        }

        showRoundOverActions();
    }

    function handleNotGameResult(data) {
        if (!data) return;
        renderOpponentHands();
        renderPlayerLabels();

        // 浮动显示本局得分
        if (data.rounds && data.rounds.length) {
            var latestRound = data.rounds[data.rounds.length - 1];
            if (latestRound && latestRound.seatScores) {
                latestRound.seatScores.forEach(function (item) {
                    var slot = slotForSeat(item.seat);
                    var score = item.score;
                    showScoreFloat(slot, score);
                });
            }
        }

        // 展示总结算面板
        GameTable.showScoreFinal(data, gameWs);
    }

    function showScoreFloat(slot, score) {
        var container = document.getElementById(slot === 'bottom' ? 'playerBottom' : ('player' + slot.charAt(0).toUpperCase() + slot.slice(1)));
        if (!container) return;
        var floatEl = document.createElement('div');
        floatEl.className = 'score-float ' + (score >= 0 ? 'win' : 'lose');
        floatEl.textContent = (score > 0 ? '+' : '') + score;
        container.appendChild(floatEl);
        setTimeout(function () { floatEl.remove(); }, 1800);
    }

    // ======================== 操作栏行为 ========================

    function showWaitingActions() {
        var bar = document.getElementById('actionBar');
        if (!bar) return;
        bar.style.display = 'flex';
        var html = '<button class="action-btn btn-prepare" id="btnPrepare">准备</button>';
        if (gameState.players.length < 4) {
            html += '<button class="action-btn" id="btnAddRobot" style="margin-left:8px;background:linear-gradient(135deg,#3b82f6,#2563eb);border-color:#1d4ed8;color:#fff;">+ 添加机器人</button>';
        }
        bar.innerHTML = html;

        var prepBtn = document.getElementById('btnPrepare');
        if (prepBtn) {
            prepBtn.onclick = function () {
                GameTable.doPrepare(sendWsMessage);
                bar.innerHTML = '<span style="color:#94a3b8;font-size:14px;">已准备，等待开局…</span>';
            };
        }

        var addBotBtn = document.getElementById('btnAddRobot');
        if (addBotBtn) {
            addBotBtn.onclick = function () {
                sendWsMessage('addRobot', {tableId: tableId}, function (resp) {
                    if (resp.code !== 0) {
                        GameTable.showCenterMsg(resp.msg || '添加机器人失败');
                    }
                });
            };
        }
    }

    function showRobActions() {
        var bar = document.getElementById('actionBar');
        if (!bar) return;
        bar.style.display = 'flex';
        bar.innerHTML = '<span style="color:#f59e0b;font-weight:bold;margin-right:8px;">抢庄:</span>'
            + '<button class="action-btn" data-rob="0">不抢</button>'
            + '<button class="action-btn btn-rob" data-rob="1">1倍</button>'
            + '<button class="action-btn btn-rob" data-rob="2">2倍</button>'
            + '<button class="action-btn btn-rob" data-rob="3">3倍</button>'
            + '<button class="action-btn btn-rob" data-rob="4">4倍</button>';

        bar.querySelectorAll('[data-rob]').forEach(function (btn) {
            btn.onclick = function () {
                var mult = parseInt(this.dataset.rob, 10);
                var choice = mult > 0 ? (NIU_OP.ROB_1 + mult - 1) : NIU_OP.NO_ROB;
                sendWsMessage('op', {choice: choice});
                bar.innerHTML = '<span style="color:#e2e8f0;font-size:14px;">已提交抢庄(' + (mult > 0 ? mult + '倍' : '不抢') + ')</span>';
            };
        });
    }

    function showBetActions() {
        var bar = document.getElementById('actionBar');
        if (!bar) return;
        if (gameState.bankerId === userId) {
            bar.style.display = 'flex';
            bar.innerHTML = '<span style="color:#f59e0b;font-size:14px;font-weight:bold;">👑 你是庄家，等待闲家下注…</span>';
            return;
        }
        bar.style.display = 'flex';
        bar.innerHTML = '<span style="color:#10b981;font-weight:bold;margin-right:8px;">下注:</span>'
            + '<button class="action-btn btn-bet" data-bet="1">1倍</button>'
            + '<button class="action-btn btn-bet" data-bet="2">2倍</button>'
            + '<button class="action-btn btn-bet" data-bet="3">3倍</button>'
            + '<button class="action-btn btn-bet" data-bet="4">4倍</button>'
            + '<button class="action-btn btn-bet" data-bet="5">5倍</button>';

        bar.querySelectorAll('[data-bet]').forEach(function (btn) {
            btn.onclick = function () {
                var mult = parseInt(this.dataset.bet, 10);
                var choice = NIU_OP.BET_1 + mult - 1;
                sendWsMessage('op', {choice: choice});
                bar.innerHTML = '<span style="color:#e2e8f0;font-size:14px;">已下注(' + mult + '倍)</span>';
            };
        });
    }

    function showShowActions() {
        var bar = document.getElementById('actionBar');
        if (!bar) return;
        if (gameState.showedPlayers.has(userId)) {
            bar.style.display = 'flex';
            bar.innerHTML = '<span style="color:#a78bfa;font-size:14px;">已摊牌，等待其他玩家…</span>';
            return;
        }
        var evalResult = NiuniuRule.evaluate(gameState.myCards);
        bar.style.display = 'flex';
        bar.innerHTML = '<button class="action-btn btn-show" id="btnShow">摊牌 (' + (evalResult.name || '亮牌') + ')</button>';
        document.getElementById('btnShow').onclick = function () {
            sendWsMessage('op', {choice: NIU_OP.SHOW});
            bar.innerHTML = '<span style="color:#a78bfa;font-size:14px;">已摊牌，等待比牌…</span>';
        };
    }

    function showRoundOverActions() {
        var bar = document.getElementById('actionBar');
        if (!bar) return;
        bar.style.display = 'flex';
        bar.innerHTML = '<button class="action-btn btn-prepare" id="btnNextRound">准备下一局</button>'
            + '<button class="action-btn" onclick="GameTable.backToLobby()" style="margin-left:8px;">返回房间</button>';
        document.getElementById('btnNextRound').onclick = function () {
            resetRoundState();
            GameTable.doPrepare(sendWsMessage);
            bar.innerHTML = '<span style="color:#94a3b8;font-size:14px;">已准备，等待开局…</span>';
        };
    }

    function resetRoundState() {
        gameState.myCards = [];
        gameState.opponentCards = {};
        gameState.opponentCounts = {};
        gameState.robMultipliers = {};
        gameState.betMultipliers = {};
        gameState.showedPlayers.clear();
        gameState.bankerId = 0;
        gameState.bankerMultiplier = 1;
        renderMyCards();
        renderOpponentHands();
        renderPlayerLabels();
    }

    w.NiuniuOp = {
        renderPlayerLabels: renderPlayerLabels,
        renderOpponentHands: renderOpponentHands,
        renderMyCards: renderMyCards,
        handleNotCard: handleNotCard,
        handleNotState: handleNotState,
        handleAckOp: handleAckOp,
        handleNotRoundResult: handleNotRoundResult,
        handleNotGameResult: handleNotGameResult,
        showWaitingActions: showWaitingActions,
        resetRoundState: resetRoundState
    };
})(window);
