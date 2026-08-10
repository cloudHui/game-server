/**
 * 只读牌桌视图。座位结构沿用真实牌桌的 player-area/player-* 约定，
 * 回放页只负责时间轴和状态，不再维护另一套座位坐标。
 */
(function (w) {
    'use strict';

    function shell() {
        return '<div class="replay-table table-bg" id="replayTable">'
            + seatShell('top') + seatShell('left') + seatShell('right') + seatShell('bottom')
            + '<div class="replay-discards" id="replayDiscards"></div>'
            + '<div class="replay-center"><div id="replayEventCards" class="replay-event-cards"></div><div id="replayEvent" class="replay-event"></div><div id="replayDecision" class="replay-decision"></div><span id="replayStep"></span></div>'
            + '<div class="replay-waiting" id="replayWaiting" hidden><strong>正在进入实时对局</strong><span id="replayWaitingText">等待首个回放事件…</span></div></div>';
    }

    function seatShell(slot) {
        return '<div class="player-area player-' + slot + ' replay-seat" data-slot="' + slot + '">'
            + '<div class="player-info"><span class="name"></span></div>'
            + '<div class="replay-hand-row"><div class="replay-hand"></div><div class="replay-exposed"></div></div></div>';
    }

    function slotFor(seat, viewSeat, seatCount) {
        return TableSeatView.slotFor(seat, viewSeat, seatCount);
    }

    function seatElement(seat, viewSeat, seatCount) {
        return document.querySelector('#replayTable [data-slot="' + slotFor(seat, viewSeat, seatCount) + '"]');
    }

    w.ReplayTableView = {shell: shell, seatElement: seatElement};
})(window);
