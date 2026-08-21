/** 正式对局与回放共用的座位牌区渲染器；调用方只提供状态与是否可操作。 */
(function (w) {
    'use strict';

    function slotFor(seat, viewSeat, seatCount) {
        var rel = (seat - viewSeat + seatCount) % seatCount;
        if (rel === 0) return 'bottom';
        if (seatCount === 2) return 'top';
        if (seatCount === 3) return rel === 1 ? 'right' : 'left';
        return ['bottom', 'right', 'top', 'left'][rel];
    }

    function renderBacks(container, count, kind, limit) {
        if (!container) return;
        count = Math.max(0, Number(count) || 0);
        var show = limit ? Math.min(count, limit) : count;
        while (container.children.length > show) container.removeChild(container.lastChild);
        while (container.children.length < show) {
            var back = document.createElement('div');
            back.className = kind === 'mahjong' ? 'tile-back' : 'card-back';
            container.appendChild(back);
        }
    }

    function orderChi(tiles, claimTile) {
        var list = (tiles || []).slice();
        if (list.length !== 3 || claimTile == null) return list.sort(function (a, b) { return a - b; });
        var others = [], used = false;
        list.forEach(function (id) {
            if (!used && id === claimTile) used = true;
            else others.push(id);
        });
        if (others.length !== 2) return list.sort(function (a, b) { return a - b; });
        others.sort(function (a, b) { return a - b; });
        return [others[0], claimTile, others[1]];
    }

    function appendMahjongSet(container, set, opt) {
        opt = opt || {};
        var kind = set.kind || '';
        var tiles = kind === 'chi' ? orderChi(set.tiles, set.claimTile) : (set.tiles || []).slice();
        var gang = /Gang$/.test(kind);
        var group = document.createElement('div');
        group.className = 'exposed-set' + (kind === 'anGang' ? ' an-gang' : '') + (gang ? ' gang-stack' : '');
        function tile(id) { return w.MahjongTile.createTileEl(id, {small: true}); }
        function back() { var el = document.createElement('div'); el.className = 'tile-back'; return el; }
        function mark(el) {
            if (!el || set.fromSeat == null || set.fromSeat < 0) return;
            el.className += ' from-claim';
            if (opt.sourceArrow) el.dataset.sourceArrow = opt.sourceArrow(opt.ownerSeat, set.fromSeat);
        }
        if (gang) {
            var base = document.createElement('div'); base.className = 'gang-base';
            for (var i = 0; i < 3; i++) base.appendChild(kind === 'anGang' ? back() : tile(tiles[i] || tiles[0]));
            group.appendChild(base);
            var top = kind === 'anGang' && !opt.revealAnGang ? back() : tile(tiles[3] || tiles[0]);
            top.className += ' gang-top';
            if (kind === 'mingGang') mark(top);
            if (kind === 'buGang' && base.children[1]) mark(base.children[1]);
            group.appendChild(top);
        } else {
            var markIndex = kind === 'peng' ? 1 : (kind === 'chi' ? 1 : 0);
            tiles.forEach(function (id, index) { var el = tile(id); if (index === markIndex) mark(el); group.appendChild(el); });
        }
        container.appendChild(group);
    }

    function renderMahjongSets(container, sets, opt) {
        if (!container) return;
        container.innerHTML = '';
        (sets || []).forEach(function (set) { appendMahjongSet(container, set, opt); });
    }

    function renderFaces(container, cards, kind, interactive) {
        if (!container) return;
        container.innerHTML = '';
        var row = container;
        if (kind === 'poker') {
            row = document.createElement('div'); row.className = 'hand-row'; container.appendChild(row);
        }
        (cards || []).forEach(function (id) {
            var el;
            if (kind === 'mahjong') el = w.MahjongTile.createTileEl(id, {small: true});
            else el = createPokerCard(id, {});
            if (!interactive) el.setAttribute('aria-disabled', 'true');
            row.appendChild(el);
        });
    }

    function createPokerCard(id, opt) {
        opt = opt || {};
        var card = document.createElement('div');
        card.className = 'card' + (opt.selected ? ' selected' : '') + (opt.flash ? ' deal-in' : '');
        if (opt.index != null) { card.dataset.index = opt.index; card.style.zIndex = String(opt.index + 1); }
        card.appendChild(w.PokerCard.createCardFace(id));
        if (opt.onMouseDown) card.onmousedown = opt.onMouseDown;
        if (opt.onTouchStart) card.ontouchstart = opt.onTouchStart;
        return card;
    }

    w.TableSeatView = {
        slotFor: slotFor,
        renderBacks: renderBacks,
        orderChi: orderChi,
        appendMahjongSet: appendMahjongSet,
        renderMahjongSets: renderMahjongSets,
        renderFaces: renderFaces,
        createPokerCard: createPokerCard
    };
})(window);
