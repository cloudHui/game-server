/**
 * 牛牛牌桌主入口：会话管理、WebSocket 消息路由与桌面初始化。
 */
var session = GameTable.loadSession();
var sessionId = session.sessionId;
var userId = session.userId;
var nickname = session.nickname;
var tableId = session.tableId;

var gameState = {
    myCards: [],
    players: [],
    myPosition: -1,
    opponentCounts: {},
    opponentCards: {},
    robMultipliers: {},
    betMultipliers: {},
    showedPlayers: new Set(),
    bankerId: 0,
    bankerMultiplier: 1,
    currentState: 0,
    roomId: session.roomId,
    opPending: false
};

GameTable.requireSessionOrRedirect(session);
document.getElementById('myName').textContent = nickname;

var gameWs = GameTable.createGameWs({
    sessionId: sessionId,
    tableId: tableId,
    onAuthed: function () {
        enterTable();
    },
    onPush: handleWsPush
});

function sendWsMessage(action, data, callback) {
    return gameWs.send(action, data, callback);
}

function handleWsPush(data) {
    if (!data) return;
    switch (data.action) {
        case 'seatUpdate':
            if (data.data && data.data.players) {
                updatePlayers(data.data.players);
            }
            break;
        case 'notCard':
            NiuniuOp.handleNotCard(data.data);
            break;
        case 'notState':
            NiuniuOp.handleNotState(data.data);
            break;
        case 'ackOp':
            if (data.data) NiuniuOp.handleAckOp(data.data);
            break;
        case 'notRoundResult':
            if (data.data) NiuniuOp.handleNotRoundResult(data.data);
            break;
        case 'notGameResult':
            NiuniuOp.handleNotGameResult(data.data);
            break;
        case 'tableDestroyed':
            GameTable.handleTableDestroyed(gameWs);
            break;
    }
}

function updatePlayers(players) {
    gameState.players = players || [];
    for (var i = 0; i < gameState.players.length; i++) {
        if (gameState.players[i].roleId === userId) {
            gameState.myPosition = gameState.players[i].position;
            break;
        }
    }
    NiuniuOp.renderPlayerLabels();
    NiuniuOp.renderOpponentHands();
}

function enterTable() {
    sendWsMessage('enterTable', {tableId: tableId}, function (resp) {
        if (resp.code === 0 && resp.data) {
            updatePlayers(resp.data.players || []);
            if (resp.data.tableInfo) {
                gameState.roomId = GameTable.applyTableInfo(resp.data.tableInfo, gameState.roomId);
            }
            NiuniuOp.showWaitingActions();
        } else {
            GameTable.showCenterMsg(resp.msg || '进入桌子失败');
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
        if (resp.code !== 0 || !resp.data) {
            GameTable.showCenterMsg((resp && resp.msg) || '刷新失败');
            return;
        }
        if (resp.data.players) updatePlayers(resp.data.players);
        if (resp.data.tableInfo) {
            gameState.roomId = GameTable.applyTableInfo(resp.data.tableInfo, gameState.roomId);
        }
    });
}

function backToLobby() {
    GameTable.backToLobby();
}

function exitRoom() {
    GameTable.exitRoom(sendWsMessage);
}

function closeSettle() {
    GameTable.closeSettle();
}
