// 麻将牌桌入口：会话/状态/WS 编排；渲染见 mahjong-view.js，操作见 mahjong-op.js
var session = GameTable.loadSession();
var sessionId = session.sessionId;
var userId = session.userId;
var nickname = session.nickname;
var tableId = session.tableId;

var OP = {
    PASS: 0,
    PREPARE: 7,
    DISCARD: 13,
    MJ_PENG: 14,
    MJ_GANG: 15,
    MJ_CHI: 16,
    MJ_HU: 17,
    MJ_PASS: 18
};

/** 与大厅模板 seatNum 对齐：1/9001→4，11→2，12→3 */
function resolveSeatNum(roomId) {
    var saved = parseInt(localStorage.getItem('seatNum') || '0', 10);
    if (saved === 2 || saved === 3 || saved === 4) return saved;
    if (roomId === 11) return 2;
    if (roomId === 12) return 3;
    return 4;
}

var gameState = {
    myTiles: [],
    selectedTile: -1,
    drawnTileId: 0,
    players: [],
    myPosition: -1,
    seatNum: resolveSeatNum(session.roomId),
    discardedTiles: [],
    discardSeats: [],
    discardLayouts: [],
    lastDiscardEventAt: 0,
    syncingHistory: true,
    wallLeft: 0,
    lastClaimTile: 0,
    lastDiscardTile: 0,
    lastDiscardSeat: -1,
    activeSeat: -1,
    opPending: false,
    pendingDiscardTile: 0,
    exposedBySeat: {},
    roomId: session.roomId,
    autoNextRound: GameTable.isQuickRobotRoom(session.roomId)
};

GameTable.requireSessionOrRedirect(session);
document.getElementById('myName').textContent = nickname;
document.getElementById('roomInfo').textContent = '桌号: ' + tableId;

var gameWs = GameTable.createGameWs({
    sessionId: sessionId,
    onAuthed: function () {
        console.info('[麻将连接认证]', { tableId: tableId, userId: userId,
            handCount: gameState.myTiles.length });
        if (typeof resetMahjongViewForReconnect === 'function') resetMahjongViewForReconnect();
        enterTable();
    },
    onPush: handleWsPush
});
function sendWsMessage(action, data, callback) { return gameWs.send(action, data, callback); }

function handleWsPush(data) {
    switch (data.action) {
        case 'seatUpdate':
            if (data.data && data.data.players) updatePlayers(data.data.players);
            break;
        case 'notCard': handleNotCard(data.data); break;
        case 'notOp': handleNotOp(data.data); break;
        case 'notState': handleNotState(data.data); break;
        case 'notResult': handleNotResult(data.data); break;
        case 'notMjState': handleNotMjState(data.data); break;
        case 'notRoundResult': handleNotRoundResult(data.data); break;
        case 'notGameResult': handleNotGameResult(data.data); break;
    }
}

function enterTable() {
    sendWsMessage('enterTable', { tableId: tableId }, function(resp) {
        gameState.syncingHistory = false;
        if (resp.code === 0 && resp.data) {
            updatePlayers(resp.data.players || []);
            if (resp.data.tableInfo) {
                document.getElementById('roomInfo').textContent =
                    '桌号: ' + resp.data.tableInfo.tableId;
            }
            showActionButtons('waiting');
        } else {
            showCenterMsg(resp.msg || '进入桌子失败');
        }
    });
}

window.addEventListener('resize', layoutMyHand);
window.addEventListener('orientationchange', function () { setTimeout(layoutMyHand, 60); });
if (window.GameLandscape) GameLandscape.bind(layoutMyHand);
layoutMyHand();
gameWs.connect();
