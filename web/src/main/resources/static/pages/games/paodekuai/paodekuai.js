/**
 * 跑得快牌桌入口：会话/状态/WS 编排。
 */
// ==================== 状态 ====================
var session = GameTable.loadSession();
var sessionId = session.sessionId;
var userId = session.userId;
var nickname = session.nickname;
var tableId = session.tableId;
var gameState = {
    myCards: [],
    selectedCards: new Set(),
    players: [],
    myPosition: -1,
    lastPlayedCards: [],
    currentOp: null,
    opponentCounts: {},
    landlordId: 0,
    bottomCards: [],
    roomId: session.roomId,
    autoNextRound: false,
    lastChoices: [],
    opPending: false
};

GameTable.requireSessionOrRedirect(session);
document.getElementById('myName').textContent = nickname;
document.getElementById('roomInfo').textContent = '桌号: ' + tableId;

var gameWs = GameTable.createGameWs({
    sessionId: sessionId,
    onAuthed: function () {
        enterTable();
    },
    onPush: handleWsPush
});

function sendWsMessage(action, data, callback) {
    return gameWs.send(action, data, callback);
}

function handleWsPush(data) {
    switch (data.action) {
        case 'seatUpdate':
            if (data.data && data.data.players) updatePlayers(data.data.players);
            break;
        case 'notCard':
            handleNotCard(data.data);
            break;
        case 'notOp':
            handleNotOp(data.data);
            break;
        case 'ackOp':
            if (data.data) handleAckOp(data.data);
            break;
        case 'notState':
            handleNotState(data.data);
            break;
        case 'notResult':
            handleNotResult(data.data);
            break;
        case 'notRoundResult':
            handlePdkRoundResult(data.data);
            break;
        case 'notGameResult':
            handleNotGameResult(data.data);
            break;
    }
}


function enterTable() {
    sendWsMessage('enterTable', {tableId: tableId}, function (resp) {
        if (resp.code === 0 && resp.data) {
            updatePlayers(resp.data.players || []);
            if (resp.data.tableInfo) {
                document.getElementById('roomInfo').textContent =
                    '桌号: ' + resp.data.tableInfo.tableId;
                if (resp.data.tableInfo.roomId) {
                    gameState.roomId = resp.data.tableInfo.roomId;
                    localStorage.setItem('roomId', String(gameState.roomId));
                }
                if (resp.data.tableInfo.landlord) {
                    gameState.landlordId = resp.data.tableInfo.landlord;
                }
            }
            gameState.autoNextRound = GameTable.isQuickRobotRoom(gameState.roomId);
            showActionButtons('waiting');
        } else {
            showCenterMsg(resp.msg || '进入桌子失败');
        }
    });
}

if (window.GameLandscape) GameLandscape.bind(layoutMyCards);
layoutMyCards();
gameWs.connect();
