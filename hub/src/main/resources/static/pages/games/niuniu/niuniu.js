/**
 * 牛牛牌桌入口：会话、状态与 WebSocket 编排。
 */
var session = GameTable.loadSession();
var sessionId = session.sessionId;
var userId = session.userId;
var nickname = session.nickname;
var tableId = session.tableId;
var gameState = {
    seatNum: 4,
    myCards: [],
    selectedCards: new Set(),
    selectedCardIndexes: new Set(),
    players: [],
    myPosition: -1,
    opponentCounts: {},
    landlordId: 0,
    robMultiplier: 1,
    baseScore: 1,
    robMultipliers: {},
    betMultipliers: {},
    showedPlayers: new Set(),
    roomId: session.roomId,
    autoNextRound: false,
    currentState: 1,
    myReady: false,
    opPending: false
};

GameTable.requireSessionOrRedirect(session);
document.getElementById('myName').textContent = nickname;
var gameWs = GameTable.createGameWs({
    sessionId: sessionId,
    tableId: tableId,
    onAuthed: enterTable,
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
                if (data.data.tableInfo) {
                    gameState.roomId = GameTable.applyTableInfo(data.data.tableInfo, gameState.roomId);
                }
                updatePlayers(data.data.players);
                if (gameState.currentState === 1) showWaitingActions();
            }
            break;
        case 'notCard':
            handleNotCard(data.data);
            break;
        case 'notState':
            handleNotState(data.data);
            break;
        case 'ackOp':
            if (data.data) handleAckOp(data.data);
            break;
        case 'notRoundResult':
            handleNotRoundResult(data.data);
            break;
        case 'notGameResult':
            handleNotGameResult(data.data);
            break;
        case 'tableDestroyed':
            GameTable.handleTableDestroyed(gameWs);
            break;
    }
}

function enterTable() {
    sendWsMessage('enterTable', {tableId: tableId}, function (resp) {
        if (resp && resp.code === 0 && resp.data) {
            if (resp.data.tableInfo) {
                gameState.roomId = GameTable.applyTableInfo(resp.data.tableInfo, gameState.roomId);
            }
            gameState.autoNextRound = GameTable.isQuickRobotRoom(gameState.roomId);
            updatePlayers(resp.data.players || []);
            if (gameState.currentState === 1) showWaitingActions();
        } else {
            showCenterMsg((resp && resp.msg) || '进入桌子失败');
        }
    });
}

if (window.GameLandscape) GameLandscape.bind(layoutMyCards);
layoutMyCards();
gameWs.connect();
