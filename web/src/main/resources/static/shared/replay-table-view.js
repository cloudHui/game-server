/**
 * 只读牌桌视图。座位结构沿用真实牌桌的 player-area/player-* 约定，
 * 回放页只负责时间轴和状态，不再维护另一套座位坐标。
 */
(function (w) {
    'use strict';

    function shell() {
        return '<div class="replay-table table-bg" id="replayTable">'
            + '<div class="player-area player-top replay-seat" data-slot="top"><div class="player-info"><span class="name"></span></div><div class="replay-hand"></div><div class="replay-exposed"></div></div>'
            + '<div class="player-area player-left replay-seat" data-slot="left"><div class="player-info"><span class="name"></span></div><div class="replay-hand"></div><div class="replay-exposed"></div></div>'
            + '<div class="player-area player-right replay-seat" data-slot="right"><div class="player-info"><span class="name"></span></div><div class="replay-hand"></div><div class="replay-exposed"></div></div>'
            + '<div class="player-area player-bottom replay-seat" data-slot="bottom"><div class="player-info"><span class="name"></span></div><div class="replay-hand"></div><div class="replay-exposed"></div></div>'
            + '<div class="replay-discards" id="replayDiscards"></div>'
            + '<div class="replay-center"><div id="replayEventCards" class="replay-event-cards"></div><div id="replayEvent" class="replay-event"></div><div id="replayDecision" class="replay-decision"></div><span id="replayStep"></span></div>'
            + '<div class="replay-waiting" id="replayWaiting" hidden><strong>正在进入实时对局</strong><span id="replayWaitingText">等待首个回放事件…</span></div></div>';
    }

    function slotFor(seat, viewSeat, seatCount) {
        var rel = (seat - viewSeat + seatCount) % seatCount;
        if (rel === 0) return 'bottom';
        if (seatCount === 2) return 'top';
        if (seatCount === 3) return rel === 1 ? 'right' : 'left';
        return ['bottom', 'right', 'top', 'left'][rel];
    }

    function seatElement(seat, viewSeat, seatCount) {
        return document.querySelector('#replayTable [data-slot="' + slotFor(seat, viewSeat, seatCount) + '"]');
    }

    w.ReplayTableView = {shell: shell, seatElement: seatElement};
})(window);
