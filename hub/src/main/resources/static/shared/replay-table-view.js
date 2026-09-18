/**
 * 全仿真牌桌视图构建器 (ReplayTableView)
 *
 * 核心职责：
 * 1. 构建与真实对局 1:1 对齐的只读牌桌 DOM 结构；
 * 2. 提供门前独立出牌区 (played-top/left/right/bottom) 与麻将中央牌池 (discarded-center)；
 * 3. 提供顶部专用区（斗地主三张底牌、麻将庄家与赖子、拖拉机主牌与墩分）；
 * 4. 彻底移除旧版遮挡视线的中央黑底审计调试框，保障沉浸式打牌视效。
 */
(function (global) {
    'use strict';

    /**
     * 生成全仿真对局牌桌骨架 HTML。
     *
     * @returns {string} 牌桌 HTML 字符串
     */
    function shell() {
        return '<div class="replay-table table-bg" id="replayTable">'
            // 1. 顶部状态栏与特殊牌区（底牌/赖子/主牌）
            + '<div class="replay-top-bar" id="replayTopBar">'
            + '  <div class="replay-room-meta" id="replayRoomMeta"></div>'
            + '  <div class="replay-special-cards" id="replaySpecialCards"></div>'
            + '  <div class="replay-view-tags" id="replayViewTags"></div>'
            + '</div>'

            // 2. 四方座位区域 (沿用正式对局 player-area player-{slot} 约定)
            + seatShell('top')
            + seatShell('left')
            + seatShell('right')
            + seatShell('bottom')

            // 3. 门前独立出牌区 (扑克对局出牌与不出提示落在各家门前)
            + '<div class="played-cards played-top" id="playedTop"></div>'
            + '<div class="played-cards played-left" id="playedLeft"></div>'
            + '<div class="played-cards played-right" id="playedRight"></div>'
            + '<div class="played-cards played-bottom" id="playedBottom"></div>'

            // 4. 麻将中央堂子弃牌池与牌墙
            + '<div class="replay-mj-center" id="replayMjCenter" style="display:none;">'
            + '  <div class="discarded-area discarded-center" id="replayMjDiscards"></div>'
            + '</div>'

            // 5. 底部轻量动作提示浮条 (不遮挡中央牌局)
            + '<div class="replay-action-toast" id="replayActionToast"></div>'

            // 6. 实时观战等待遮罩
            + '<div class="replay-waiting" id="replayWaiting" hidden>'
            + '  <strong>正在进入实时对局</strong>'
            + '  <span id="replayWaitingText">等待首个回放事件…</span>'
            + '</div>'
            + '</div>';
    }

    /**
     * 生成单个玩家座位的 HTML 结构。
     * 包含玩家信息、角色/地主角标、叫分气泡、手牌区与副露区。
     *
     * @param {string} slot 方位 ('top' | 'left' | 'right' | 'bottom')
     * @returns {string} 单座位 HTML 字符串
     */
    function seatShell(slot) {
        return '<div class="player-area player-' + slot + ' replay-seat" data-slot="' + slot + '">'
            + '<div class="player-info">'
            + '  <span class="role-badge"></span>'
            + '  <span class="name"></span>'
            + '  <span class="tile-count-badge"></span>'
            + '  <span class="bid-bubble" style="display:none;"></span>'
            + '</div>'
            + '<div class="replay-hand-row">'
            + '  <div class="replay-hand"></div>'
            + '  <div class="replay-exposed"></div>'
            + '</div>'
            + '</div>';
    }

    /**
     * 根据当前主视角 viewSeat 计算任意座位的相对方位 slot。
     *
     * @param {number} seat 目标座位
     * @param {number} viewSeat 当前主视角座位
     * @param {number} seatCount 牌桌总人数 (2~4)
     * @returns {string} 方位名称 ('bottom' | 'right' | 'top' | 'left')
     */
    function slotFor(seat, viewSeat, seatCount) {
        if (typeof TableSeatView !== 'undefined' && TableSeatView.slotFor) {
            return TableSeatView.slotFor(seat, viewSeat, seatCount);
        }
        var rel = (seat - viewSeat + seatCount) % seatCount;
        if (rel === 0) return 'bottom';
        if (seatCount === 2) return 'top';
        if (seatCount === 3) return rel === 1 ? 'right' : 'left';
        return ['bottom', 'right', 'top', 'left'][rel];
    }

    /**
     * 获取指定座位的 DOM 元素。
     *
     * @param {number} seat 目标座位索引
     * @param {number} viewSeat 当前主视角座位
     * @param {number} seatCount 总座位数
     * @returns {Element|null} 座位 DOM 节点
     */
    function seatElement(seat, viewSeat, seatCount) {
        var slot = slotFor(seat, viewSeat, seatCount);
        return document.querySelector('#replayTable [data-slot="' + slot + '"]');
    }

    /**
     * 获取指定方位对应的门前出牌区 DOM 元素。
     *
     * @param {string} slot 方位 ('top' | 'left' | 'right' | 'bottom')
     * @returns {Element|null} 出牌区 DOM 节点
     */
    function playedElementForSlot(slot) {
        if (!slot) return null;
        var id = 'played' + slot.charAt(0).toUpperCase() + slot.slice(1);
        return document.getElementById(id);
    }

    var ReplayTableView = {
        shell: shell,
        seatShell: seatShell,
        slotFor: slotFor,
        seatElement: seatElement,
        playedElementForSlot: playedElementForSlot
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = ReplayTableView;
    }
    if (global) {
        global.ReplayTableView = ReplayTableView;
    }
})(typeof window !== 'undefined' ? window : this);
