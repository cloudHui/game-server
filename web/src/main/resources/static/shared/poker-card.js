/**
 * 扑克牌面：左上/右下角标一致（小号），中下部主点数放大。
 * 斗地主 / 跑得快 / 拖拉机共用。
 */
(function (w) {
    'use strict';

    function cardMeta(cardId) {
        if (cardId === 516) return {rank: '小', suit: '王', red: false, joker: true, label: '小王'};
        if (cardId === 517) return {rank: '大', suit: '王', red: true, joker: true, label: '大王'};
        var suitId = Math.floor(cardId / 100), value = cardId % 100;
        var suits = {1: '♦', 2: '♣', 3: '♥', 4: '♠'};
        var ranks = {11: 'J', 12: 'Q', 13: 'K', 14: 'A', 15: '2'};
        var rank = ranks[value] || String(value);
        var suit = suits[suitId] || '♦';
        return {
            rank: rank,
            suit: suit,
            red: suitId === 1 || suitId === 3,
            joker: false,
            label: rank
        };
    }

    function cornerHtml(meta) {
        if (meta.joker) {
            return '<span class="rank">' + meta.rank + '</span><span class="suit">' + meta.suit + '</span>';
        }
        return '<span class="rank">' + meta.rank + '</span><span class="suit">' + meta.suit + '</span>';
    }

    function createCardFace(cardId) {
        var meta = cardMeta(cardId);
        var face = document.createElement('div');
        face.className = 'card-face' + (meta.red ? ' red' : '') + (meta.joker ? ' joker-face' : '');

        var top = document.createElement('div');
        top.className = 'card-corner card-corner-tl';
        top.innerHTML = cornerHtml(meta);
        face.appendChild(top);

        var art = document.createElement('div');
        art.className = 'card-art rank-large';
        if (meta.joker) {
            art.innerHTML = '<span class="big-rank">' + meta.label + '</span>';
        } else {
            art.innerHTML = '<span class="big-rank">' + meta.rank + '</span>'
                + '<span class="big-suit">' + meta.suit + '</span>';
        }
        face.appendChild(art);

        var bottom = document.createElement('div');
        bottom.className = 'card-corner card-corner-br';
        bottom.innerHTML = cornerHtml(meta);
        face.appendChild(bottom);

        return face;
    }

    w.PokerCard = {cardMeta: cardMeta, createCardFace: createCardFace};
})(window);
