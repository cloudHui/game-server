/** 卡片入口分页：按钮翻页，不拦截页面或游戏内的触控手势。 */
(function () {
    'use strict';

    var PAGE_SIZE = 3;

    function setup(grid, index) {
        var items = Array.prototype.filter.call(grid.children, function (item) {
            return item.matches('article, .game-card, .card');
        });
        if (items.length <= PAGE_SIZE) return;

        var key = 'card-page:' + location.pathname + ':' + index;
        var current = parseInt(sessionStorage.getItem(key), 10) || 1;
        var pager = document.createElement('nav');
        var previous = document.createElement('button');
        var status = document.createElement('span');
        var next = document.createElement('button');
        pager.className = 'card-pager';
        pager.setAttribute('aria-label', '卡片分页');
        previous.type = next.type = 'button';
        previous.textContent = '上一页';
        next.textContent = '下一页';
        status.className = 'card-pager-status';
        status.setAttribute('aria-live', 'polite');
        pager.appendChild(previous);
        pager.appendChild(status);
        pager.appendChild(next);
        grid.parentNode.insertBefore(pager, grid.nextSibling);

        function render() {
            var pages = Math.ceil(items.length / PAGE_SIZE);
            current = Math.min(Math.max(current, 1), pages);
            items.forEach(function (item, itemIndex) {
                item.hidden = itemIndex < (current - 1) * PAGE_SIZE || itemIndex >= current * PAGE_SIZE;
            });
            previous.disabled = current === 1;
            next.disabled = current === pages;
            status.textContent = current + ' / ' + pages;
            pager.hidden = pages === 1;
            sessionStorage.setItem(key, String(current));
        }

        previous.onclick = function () {
            current--;
            render();
        };
        next.onclick = function () {
            current++;
            render();
        };
        render();
    }

    document.querySelectorAll('[data-card-pager]').forEach(setup);
})();
