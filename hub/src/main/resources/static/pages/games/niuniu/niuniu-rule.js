/**
 * 牛牛前端牌型判定与 3+2 凑十分牌工具（与服务端 NiuniuRules 对齐）。
 */
(function (w) {
    'use strict';

    var COMBS_3 = [
        [0, 1, 2], [0, 1, 3], [0, 1, 4], [0, 2, 3], [0, 2, 4],
        [0, 3, 4], [1, 2, 3], [1, 2, 4], [1, 3, 4], [2, 3, 4]
    ];
    var NIU_NAMES = ['无牛', '牛一', '牛二', '牛三', '牛四', '牛五', '牛六', '牛七', '牛八', '牛九', '牛牛'];
    var NIU_MULTS = [1, 1, 1, 1, 1, 1, 1, 2, 2, 3, 4];

    function getNiuPoint(cardId) {
        if (!cardId) return 0;
        var v = cardId % 100;
        return v === 14 ? 1 : (v === 15 ? 2 : (v >= 10 ? 10 : v));
    }

    function getCardRank(cardId) {
        if (!cardId) return 0;
        var v = cardId % 100;
        return v === 14 ? 1 : (v === 15 ? 2 : (v >= 16 ? v - 2 : v));
    }

    function getCardSuit(cardId) {
        return Math.floor(cardId / 100);
    }

    function evaluate(cards) {
        if (!cards || cards.length < 5) {
            return {name: '', multiplier: 1, type: 0, level: 'none', hasGroupSplit: false, orderedCards: (cards || []).slice()};
        }
        var copy = cards.slice(0, 5);

        // 1. 五小牛
        var small = true, sum = 0;
        for (var i = 0; i < 5; i++) {
            var pt = getNiuPoint(copy[i]);
            sum += pt;
            if (pt >= 5) small = false;
        }
        if (small && sum <= 10) {
            return {name: '五小牛', multiplier: 8, type: 14, level: 'special', hasGroupSplit: false, orderedCards: copy};
        }

        // 2. 炸弹牛（四条排前，单张垫后）
        var counts = {}, bombRank = 0;
        for (var j = 0; j < 5; j++) {
            var r = getCardRank(copy[j]);
            counts[r] = (counts[r] || 0) + 1;
            if (counts[r] >= 4) bombRank = r;
        }
        if (bombRank > 0) {
            var quads = copy.filter(function (c) { return getCardRank(c) === bombRank; });
            var rest = copy.filter(function (c) { return getCardRank(c) !== bombRank; });
            return {name: '炸弹牛', multiplier: 7, type: 13, level: 'special', hasGroupSplit: false, orderedCards: quads.concat(rest)};
        }

        // 3. 五花牛 / 四花牛
        var jqk = 0, ten = 0;
        for (var k = 0; k < 5; k++) {
            var rk = getCardRank(copy[k]);
            if (rk >= 11 && rk <= 13) jqk++;
            else if (rk === 10) ten++;
        }
        if (jqk === 5) return {name: '五花牛', multiplier: 6, type: 12, level: 'special', hasGroupSplit: false, orderedCards: copy};
        if (jqk === 4 && ten === 1) return {name: '四花牛', multiplier: 5, type: 11, level: 'special', hasGroupSplit: false, orderedCards: copy};

        // 4. 经典 3+2 凑十寻优
        var best = 0, ordered = copy;
        for (var c = 0; c < COMBS_3.length; c++) {
            var idx = COMBS_3[c];
            if ((getNiuPoint(copy[idx[0]]) + getNiuPoint(copy[idx[1]]) + getNiuPoint(copy[idx[2]])) % 10 !== 0) continue;
            var head = [], rem = 0;
            for (var x = 0; x < 5; x++) {
                if (x !== idx[0] && x !== idx[1] && x !== idx[2]) {
                    head.push(copy[x]);
                    rem += getNiuPoint(copy[x]);
                }
            }
            var niu = rem % 10 === 0 ? 10 : rem % 10;
            if (niu > best) {
                best = niu;
                ordered = [copy[idx[0]], copy[idx[1]], copy[idx[2]]].concat(head);
            }
        }
        if (best > 0) {
            var lv = best === 10 ? 'niu' : (best >= 7 ? 'high' : 'low');
            return {name: NIU_NAMES[best], multiplier: NIU_MULTS[best], type: best, level: lv, hasGroupSplit: true, orderedCards: ordered};
        }
        return {name: '无牛', multiplier: 1, type: 0, level: 'none', hasGroupSplit: false, orderedCards: copy};
    }

    w.NiuniuRule = {getNiuPoint: getNiuPoint, getCardRank: getCardRank, getCardSuit: getCardSuit, evaluate: evaluate};
})(window);
