/** 拖拉机：庄家角标与亮主/扣底/出牌 */
function roleBadgeHtml(roleId) {
    if (!gameState.landlordId) return '';
    if (roleId === gameState.landlordId) {
        return '<span class="avatar-mark landlord">庄</span><span class="role-badge landlord">庄</span>';
    }
    return '';
}

function pokerSettleTag(roleId, landlordId) {
    return roleId === landlordId ? '庄' : '闲';
}

function updatePlayers(players) {
    gameState.players = players;
    gameState.seatNum = 4;
    for (var i = 0; i < players.length; i++) {
        if (players[i].roleId === userId) {
            gameState.myPosition = players[i].position;
            break;
        }
    }
    renderOpponentHands();
}

window.pokerOpChoiceMap = {
    6: window.PokerCommonChoice.PLAY,
    13: {cls: 'btn-play', text: '放回8张'},
    1: {cls: 'btn-call', text: '亮主'},
    2: {cls: 'btn-rob', text: '反主'},
    3: window.PokerCommonChoice.PASS,
    0: window.PokerCommonChoice.PASS,
    4: window.PokerCommonChoice.PASS
};
