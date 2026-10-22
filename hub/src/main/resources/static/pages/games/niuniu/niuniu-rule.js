/**
 * 牛牛前端牌型判定器（与服务端 NiuniuEvaluator 规则保持 100% 对齐）。
 */
(function (w) {
    'use strict';

    var COMBS_3 = [
        [0, 1, 2], [0, 1, 3], [0, 1, 4], [0, 2, 3], [0, 2, 4],
        [0, 3, 4], [1, 2, 3], [1, 2, 4], [1, 3, 4], [2, 3, 4]
    ];

    function getNiuPoint(cardId) {
        if (!cardId) return 0;
        var val = cardId % 100;
        if (val === 14) return 1; // A=1
        if (val === 15) return 2; // 2=2
        if (val >= 10 && val <= 13) return 10; // 10, J, Q, K
        if (val >= 16) return 10;
        return val; // 3 ~ 9
    }

    function getCardRank(cardId) {
        if (!cardId) return 0;
        var val = cardId % 100;
        if (val === 14) return 1; // A
        if (val === 15) return 2; // 2
        if (val >= 3 && val <= 13) return val; // 3 ~ 10, J(11), Q(12), K(13)
        if (val === 16) return 14;
        if (val === 17) return 15;
        return val;
    }

    function getCardSuit(cardId) {
        return Math.floor(cardId / 100);
    }

    function evaluate(cards) {
        if (!cards || cards.length < 5) return {name: '', multiplier: 1, type: 0, level: 'none'};

        // 1. 五小牛: 5张牌点数均小于5且总点数 <= 10
        var allSmall = true, sumVal = 0;
        for (var i = 0; i < 5; i++) {
            var pt = getNiuPoint(cards[i]);
            sumVal += pt;
            if (pt >= 5) allSmall = false;
        }
        if (allSmall && sumVal <= 10) {
            return {name: '五小牛', multiplier: 8, type: 14, level: 'special'};
        }

        // 2. 炸弹牛: 4张同点数
        var rankCounts = {};
        for (var j = 0; j < 5; j++) {
            var r = getCardRank(cards[j]);
            rankCounts[r] = (rankCounts[r] || 0) + 1;
            if (rankCounts[r] === 4) {
                return {name: '炸弹牛', multiplier: 7, type: 13, level: 'special'};
            }
        }

        // 3. 五花牛 / 四花牛
        var jqkCount = 0, tenCount = 0;
        for (var k = 0; k < 5; k++) {
            var rk = getCardRank(cards[k]);
            if (rk >= 11 && rk <= 13) jqkCount++;
            else if (rk === 10) tenCount++;
        }
        if (jqkCount === 5) {
            return {name: '五花牛', multiplier: 6, type: 12, level: 'special'};
        }
        if (jqkCount === 4 && tenCount === 1) {
            return {name: '四花牛', multiplier: 5, type: 11, level: 'special'};
        }

        // 4. 经典找牛 (C(5, 3))
        var bestNiu = -1;
        for (var c = 0; c < COMBS_3.length; c++) {
            var idxs = COMBS_3[c];
            var sum3 = getNiuPoint(cards[idxs[0]]) + getNiuPoint(cards[idxs[1]]) + getNiuPoint(cards[idxs[2]]);
            if (sum3 % 10 === 0) {
                var remSum = 0;
                for (var x = 0; x < 5; x++) {
                    if (x !== idxs[0] && x !== idxs[1] && x !== idxs[2]) {
                        remSum += getNiuPoint(cards[x]);
                    }
                }
                var remNiu = remSum % 10;
                if (remNiu === 0) remNiu = 10; // 牛牛
                if (remNiu > bestNiu) bestNiu = remNiu;
            }
        }

        if (bestNiu === 10) return {name: '牛牛', multiplier: 4, type: 10, level: 'niu'};
        if (bestNiu === 9) return {name: '牛九', multiplier: 3, type: 9, level: 'high'};
        if (bestNiu === 8) return {name: '牛八', multiplier: 2, type: 8, level: 'high'};
        if (bestNiu === 7) return {name: '牛七', multiplier: 2, type: 7, level: 'high'};
        if (bestNiu >= 1 && bestNiu <= 6) {
            var names = ['', '牛一', '牛二', '牛三', '牛四', '牛五', '牛六'];
            return {name: names[bestNiu], multiplier: 1, type: bestNiu, level: 'low'};
        }
        return {name: '无牛', multiplier: 1, type: 0, level: 'none'};
    }

    w.NiuniuRule = {
        getNiuPoint: getNiuPoint,
        getCardRank: getCardRank,
        getCardSuit: getCardSuit,
        evaluate: evaluate
    };
})(window);
