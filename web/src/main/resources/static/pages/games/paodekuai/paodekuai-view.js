/** 跑得快：仅出牌/不出；拖选优先提起最长合法牌型 */
function pokerSettleTag() { return '余牌'; }

window.pokerOpChoiceMap = {
    6: { cls: 'btn-play', text: '出牌' },
    0: { cls: 'btn-pass', text: '不出' }
};

/**
 * 拖选松手后：在经过的牌里优先提最长合法牌型
 * （顺/连对/三带二/三带一/炸/三/对），不成型则只提最小单张。
 */
window.pokerSmartSelectFromDrag = function (dragged) {
    if (!dragged || !dragged.length) return dragged;
    if (dragged.length === 1) return dragged;
    var byRank = {};
    for (var i = 0; i < dragged.length; i++) {
        var r = dragged[i] % 100;
        if (!byRank[r]) byRank[r] = [];
        byRank[r].push(dragged[i]);
    }
    var ranks = Object.keys(byRank).map(Number).sort(function (a, b) { return a - b; });

    var candidates = [];
    pushStraight(candidates, byRank, ranks, 1, 5);
    pushStraight(candidates, byRank, ranks, 2, 2);
    pushTripleWing(candidates, byRank, ranks, 2);
    pushTripleWing(candidates, byRank, ranks, 1);
    pushBomb(candidates, byRank, ranks);
    pushGroup(candidates, byRank, ranks, 3);
    pushGroup(candidates, byRank, ranks, 2);

    if (!candidates.length) {
        return [smallestCard(dragged)];
    }
    candidates.sort(function (a, b) {
        if (b.length !== a.length) return b.length - a.length;
        return maxRank(a) - maxRank(b);
    });
    return candidates[0];
};

function cardRank(id) { return id % 100; }

function smallestCard(cards) {
    var min = cards[0];
    for (var i = 1; i < cards.length; i++) {
        if (cardRank(cards[i]) < cardRank(min)
            || (cardRank(cards[i]) === cardRank(min) && cards[i] < min)) {
            min = cards[i];
        }
    }
    return min;
}

function maxRank(cards) {
    var m = 0;
    for (var i = 0; i < cards.length; i++) m = Math.max(m, cardRank(cards[i]));
    return m;
}

function take(byRank, rank, n) {
    var g = byRank[rank];
    if (!g || g.length < n) return null;
    return g.slice(0, n);
}

/** 连续段：copies=1 顺子，copies=2 连对；不含 2 */
function pushStraight(out, byRank, ranks, copies, minLen) {
    var usable = [];
    for (var i = 0; i < ranks.length; i++) {
        if (ranks[i] >= 15) continue;
        if ((byRank[ranks[i]] || []).length >= copies) usable.push(ranks[i]);
    }
    var start = 0;
    while (start < usable.length) {
        var end = start + 1;
        while (end < usable.length && usable[end] === usable[end - 1] + 1) end++;
        var run = end - start;
        if (run >= minLen) {
            for (var len = run; len >= minLen; len--) {
                for (var s = start; s + len <= end; s++) {
                    var cards = [];
                    for (var k = 0; k < len; k++) {
                        cards = cards.concat(take(byRank, usable[s + k], copies));
                    }
                    out.push(cards);
                }
            }
        }
        start = end;
    }
}

function pushTripleWing(out, byRank, ranks, wingNeed) {
    for (var i = 0; i < ranks.length; i++) {
        var t = ranks[i];
        var triple = take(byRank, t, 3);
        if (!triple) continue;
        for (var j = 0; j < ranks.length; j++) {
            if (ranks[j] === t) continue;
            var wing = take(byRank, ranks[j], wingNeed);
            if (!wing) continue;
            out.push(triple.concat(wing));
        }
    }
}

function pushBomb(out, byRank, ranks) {
    for (var i = 0; i < ranks.length; i++) {
        var bomb = take(byRank, ranks[i], 4);
        if (bomb) out.push(bomb);
    }
}

function pushGroup(out, byRank, ranks, n) {
    for (var i = 0; i < ranks.length; i++) {
        var g = take(byRank, ranks[i], n);
        if (g) out.push(g);
    }
}
