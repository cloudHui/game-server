/**
 * 牌桌横屏适配：竖屏放不下单排手牌时强制横屏布局，并给出可用宽高。
 * 不改 WebSocket 协议；玩法页负责按 gameSize 缩放牌面。
 */
(function (w) {
    'use strict';

    function isPortrait() {
        return w.innerHeight > w.innerWidth;
    }

    function setForced(on) {
        document.documentElement.classList.toggle('force-landscape', !!on);
        syncGameVars();
        if (on && w.screen && w.screen.orientation && w.screen.orientation.lock) {
            try {
                w.screen.orientation.lock('landscape').catch(function () {
                });
            } catch (e) { /* 非安全上下文或浏览器不支持则忽略 */
            }
        }
    }

    function syncGameVars() {
        var forced = document.documentElement.classList.contains('force-landscape');
        var gw = forced ? w.innerHeight : w.innerWidth;
        var gh = forced ? w.innerWidth : w.innerHeight;
        document.documentElement.style.setProperty('--game-w', gw + 'px');
        document.documentElement.style.setProperty('--game-h', gh + 'px');
        return {w: gw, h: gh, forced: forced};
    }

    function gameSize() {
        return syncGameVars();
    }

    /**
     * 竖屏且宽度不足以舒适展示时强制横屏。
     * @param {number} minWidth 需要的最小内容宽度（px）
     */
    function ensureFits(minWidth) {
        var need = Number(minWidth) || 0;
        var force = isPortrait() && w.innerWidth < need;
        setForced(force);
        return gameSize();
    }

    /**
     * 扑克类牌桌：竖屏一律强制横屏（与麻将四人桌一致），避免手牌左侧裁切。
     * 麻将等可继续用 ensureFits 按内容宽度判断。
     */
    function forcePokerLandscape() {
        setForced(isPortrait());
        return gameSize();
    }

    function bind(onChange) {
        if (typeof onChange !== 'function') return;
        var timer = null;

        function fire() {
            if (timer) clearTimeout(timer);
            timer = setTimeout(onChange, 40);
        }

        w.addEventListener('resize', fire);
        w.addEventListener('orientationchange', fire);
    }

    w.GameLandscape = {
        isPortrait: isPortrait,
        setForced: setForced,
        gameSize: gameSize,
        ensureFits: ensureFits,
        forcePokerLandscape: forcePokerLandscape,
        bind: bind
    };
})(window);
