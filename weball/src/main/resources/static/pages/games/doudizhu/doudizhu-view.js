/** 斗地主：角色角标与操作按钮 */
function roleBadgeHtml(roleId) {
    if (!gameState.landlordId) return '';
    if (roleId === gameState.landlordId) {
        return '<span class="avatar-mark landlord">地</span><span class="role-badge landlord">地主</span>';
    }
    return '<span class="avatar-mark farmer">农</span><span class="role-badge farmer">农民</span>';
}

window.pokerOpChoiceMap = {
    6: window.PokerCommonChoice.PLAY,
    0: window.PokerCommonChoice.PASS,
    1: {cls: 'btn-call', text: '叫地主'},
    2: {cls: 'btn-rob', text: '抢地主'},
    3: {cls: 'btn-pass', text: '不叫'},
    4: {cls: 'btn-pass', text: '不抢'},
    9: {cls: 'btn-call', text: '1分'},
    10: {cls: 'btn-call', text: '2分'},
    11: {cls: 'btn-call', text: '3分'}
};

window.pokerSuggestPlay = function () {
    var cards = gameState.myCards.slice().sort(function (a, b) { return a - b; });
    var last = gameState.lastPlayedCards || [];
    var pick = cards.length ? [cards[0]] : [];
    var groups = {};
    cards.forEach(function (id) { var r = id % 100; (groups[r] || (groups[r] = [])).push(id); });
    var ranks = Object.keys(groups).map(Number).sort(function (a, b) { return a - b; });
    var candidates = [];
    ranks.forEach(function (r) { [1, 2, 3, 4].forEach(function (n) {
        if (groups[r].length >= n) candidates.push(groups[r].slice(0, n));
    }); });
    for (var i = 0; i < ranks.length; i++) {
        for (var len = 5; i + len <= ranks.length; len++) {
            var seq = ranks.slice(i, i + len);
            if (seq[len - 1] >= 15 || seq.some(function (r, j) { return j && r !== seq[j - 1] + 1 || groups[r].length < 1; })) break;
            candidates.push(seq.map(function (r) { return groups[r][0]; }));
        }
        for (var pairLen = 3; i + pairLen <= ranks.length; pairLen++) {
            var pairSeq = ranks.slice(i, i + pairLen);
            if (pairSeq[pairLen - 1] >= 15 || pairSeq.some(function (r, j) {
                return groups[r].length < 2 || (j && r !== pairSeq[j - 1] + 1);
            })) break;
            candidates.push(pairSeq.reduce(function (out, r) {
                return out.concat(groups[r].slice(0, 2));
            }, []));
        }
    }
    if (last.length) {
        var need = last.length, lastRank = Math.max.apply(null, last.map(function (id) { return id % 100; }));
        var same = candidates.filter(function (c) { return c.length === need && Math.max.apply(null, c.map(function (id) { return id % 100; })) > lastRank; });
        if (same.length) pick = same[0];
        else if (groups[lastRank + 1]) pick = groups[lastRank + 1].slice(0, Math.min(need, groups[lastRank + 1].length));
        else { var bomb = candidates.filter(function (c) { return c.length === 4; }); if (bomb.length) pick = bomb[0]; }
    }
    applyPokerPickedCards(pick);
};
