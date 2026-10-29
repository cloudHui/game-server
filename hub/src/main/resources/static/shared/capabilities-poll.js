/**
 * 一体化 Hub 环境下的能力就绪与防御性放行脚本。
 * Hub 服务在同一进程内启动所有组件，所有对局与联网能力均永久就绪。
 * 负责主动清除 DOM 中历史残留的「敬请期待」标签与点击拦截。
 */
(function (w) {
    var READY_CAP = {
        onlineTables: true,
        miniOnline: true,
        miniLocal: true,
        learning: true,
        ready: true,
        lobby: true,
        game: true,
        gate: true
    };
    w.__CAPABILITIES__ = READY_CAP;
    w.checkCapabilities = function () {
        return Promise.resolve(READY_CAP);
    };

    function unlockAll() {
        // 清理所有敬请期待提示和等待样式
        document.querySelectorAll('.waiting-tip').forEach(function (el) {
            el.remove();
        });
        document.querySelectorAll('.is-waiting').forEach(function (el) {
            el.classList.remove('is-waiting');
        });
        document.querySelectorAll('[data-need-online]').forEach(function (el) {
            el.removeAttribute('data-need-online');
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', unlockAll);
    } else {
        unlockAll();
    }
})(window);
