/** 拖拉机结算展示 */
function handleNotResult(data) {
    if (!data) return;
    if (data.landlord_id) gameState.landlordId = data.landlord_id;
    var title = data.win_team === 0 ? '庄家方胜' : '闲家方胜';
    if (data.spring) title += ' · 光牌';
    var meta = '闲家得分 ' + (data.base_score || 0)
        + ' · 升降 ' + (data.rob_multiplier || 0)
        + ' · 结算 ' + (data.settle_factor || 0);
    var rows = '';
    var players = data.rPlayers || [];
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        var tag = (p.roleId === data.landlord_id) ? '庄' : '闲';
        rows += '<div class="row"><span>' + findPlayerName(p.roleId) + '（' + tag + '）</span>'
            + '<span>余牌 ' + ((p.cards && p.cards.length) || 0) + '</span></div>';
    }
    showSettle(title, meta, rows, players, data.landlord_id);
    showCenterMsg(title, 2500);
    showActionButtons('prepare');
}

function handleTractorRoundResult(data) {
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
