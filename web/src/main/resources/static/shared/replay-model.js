/** 回放文本解析与状态推进；不依赖 DOM，供播放器和测试复用。 */
(function (w) {
    'use strict';

    function cloneHands(hands) {
        var copy = {};
        Object.keys(hands).forEach(function (seat) { copy[seat] = hands[seat].slice(); });
        return copy;
    }

    function isMahjong(model) {
        return /麻将|卡五星/.test(model.type || '');
    }

    /** 与正常牌桌一致：麻将按牌值，扑克按点数后花色排列。 */
    function sortHand(model, cards) {
        cards.sort(function (a, b) {
            if (isMahjong(model)) return a - b;
            var va = a % 100, vb = b % 100;
            if (va !== vb) return va - vb;
            return Math.floor(a / 100) - Math.floor(b / 100);
        });
        return cards;
    }

    function parse(content, code, gameType) {
        var hands = {}, events = [], players = {};
        (content || '').split(/\r?\n/).forEach(function (line) {
            var player = line.match(/^座(\d+): userId=(-?\d+), nick=(.*)$/);
            if (player) { players[player[1]] = player[3]; return; }
            var hand = line.match(/^座(\d+): \[([\d,]*)\]$/);
            if (hand) { hands[hand[1]] = hand[2] ? hand[2].split(',').map(Number) : []; return; }
            var event = line.match(/^\[(\d+)\](?:\[([^\]]+)\])? (.*)$/);
            if (event) events.push({index: +event[1], time: event[2] || '', text: event[3]});
        });
        return {
            content: content, code: code, type: gameType || '', players: players,
            seatCount: Math.max(1, Object.keys(players).length, Object.keys(hands).length),
            initial: hands, events: events, pos: -1, hands: {}, exposed: {}, discards: [],
            lastDiscard: 0, nextSeat: -1, bestSeat: -1
        };
    }

    function removeOne(model, seat, id) {
        var index = (model.hands[seat] || []).indexOf(id);
        if (index >= 0) model.hands[seat].splice(index, 1);
    }

    function applyEvent(model, event) {
        var match = event.text.match(/^座(\d+) (?:超时)?(?:出牌|摸牌) (\[[\d,]*\]|\d+)/);
        if (!match) return;
        var seat = match[1];
        var ids = match[2][0] === '['
            ? (match[2].slice(1, -1) ? match[2].slice(1, -1).split(',').map(Number) : [])
            : [+match[2]];
        model.hands[seat] = model.hands[seat] || [];
        if (event.text.indexOf('摸牌') >= 0) model.hands[seat] = model.hands[seat].concat(ids);
        else {
            ids.forEach(function (id) { removeOne(model, seat, id); model.discards.push({seat: +seat, id: id}); });
            model.lastDiscard = ids.length === 1 ? ids[0] : 0;
        }
    }

    function applyFullEvent(model, event) {
        applyEvent(model, event);
        var meld = event.text.match(/^座(\d+) (吃|碰|明杠|暗杠|补杠) (\[[\d,]*\]|\d+)/);
        if (meld) {
            var seat = meld[1], kind = meld[2];
            var ids = meld[3][0] === '[' ? meld[3].slice(1, -1).split(',').filter(Boolean).map(Number) : [+meld[3]];
            model.exposed[seat] = model.exposed[seat] || [];
            if (kind === '吃') {
                var skipped = false;
                ids.forEach(function (id) {
                    if (!skipped && id === model.lastDiscard) skipped = true;
                    else removeOne(model, seat, id);
                });
                model.exposed[seat] = model.exposed[seat].concat(ids);
            } else {
                var removeCount = kind === '碰' ? 2 : (kind === '明杠' ? 3 : (kind === '暗杠' ? 4 : 1));
                for (var i = 0; i < removeCount; i++) removeOne(model, seat, ids[0]);
                for (var j = 0; j < (kind === '碰' ? 3 : 4); j++) model.exposed[seat].push(ids[0]);
            }
            if (kind !== '暗杠' && model.discards.length && model.discards[model.discards.length - 1].id === model.lastDiscard) model.discards.pop();
        }
        var bottom = event.text.match(/^座(\d+) 获得底牌 \[([\d,]+)\]/);
        if (bottom) model.hands[bottom[1]] = (model.hands[bottom[1]] || []).concat(bottom[2].split(',').map(Number));
        var bury = event.text.match(/^座(\d+) 扣底 \[([\d,]+)\]/);
        if (bury) bury[2].split(',').map(Number).forEach(function (id) { removeOne(model, bury[1], id); });
        var next = event.text.match(/^下一操作位 座(\d+)/);
        if (next) model.nextSeat = +next[1];
        var best = event.text.match(/^(?:当前|本轮)最大方 座(\d+)/);
        if (best) model.bestSeat = +best[1];
    }

    function rebuild(model, pos) {
        model.hands = cloneHands(model.initial);
        model.exposed = {};
        model.discards = [];
        model.lastDiscard = 0;
        model.nextSeat = -1;
        model.bestSeat = -1;
        model.pos = Math.max(-1, Math.min(pos, model.events.length - 1));
        for (var i = 0; i <= model.pos; i++) applyFullEvent(model, model.events[i]);
        Object.keys(model.hands).forEach(function (seat) { sortHand(model, model.hands[seat]); });
        return model;
    }

    function inspect(content, gameType, pos) {
        var model = rebuild(parse(content, 'test', gameType), pos);
        return {hands: cloneHands(model.hands), exposed: cloneHands(model.exposed), nextSeat: model.nextSeat, bestSeat: model.bestSeat, pos: model.pos};
    }

    w.ReplayModel = {parse: parse, rebuild: rebuild, inspect: inspect, sortHand: sortHand};
})(window);
