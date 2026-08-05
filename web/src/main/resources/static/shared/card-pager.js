/** 卡片入口分页：按钮翻页，不拦截页面或游戏内的触控手势。 */
(function () {
    'use strict';

    function positive(value, fallback) {
        value = parseInt(value, 10);
        return value > 0 ? value : fallback;
    }

    function pageSize(grid) {
        if (window.matchMedia('(max-width: 600px)').matches) {
            return positive(grid.dataset.pageMobile, 1);
        }
        if (window.matchMedia('(max-width: 900px)').matches) {
            return positive(grid.dataset.pageTablet, 2);
        }
        if (window.innerHeight <= 700) {
            return positive(grid.dataset.pageShort, 3);
        }
        return positive(grid.dataset.pageDesktop, 6);
    }

    function setup(grid, index) {
        var items = Array.prototype.filter.call(grid.children, function (item) {
            return item.matches('article, .game-card, .card');
        });
        if (!items.length) return;

        var key = 'card-page:' + location.pathname + ':' + index;
        var current = positive(sessionStorage.getItem(key), 1);
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
            var size = pageSize(grid);
            var pages = Math.max(1, Math.ceil(items.length / size));
            current = Math.min(Math.max(current, 1), pages);
            items.forEach(function (item, itemIndex) {
                item.hidden = itemIndex < (current - 1) * size || itemIndex >= current * size;
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
        window.addEventListener('resize', render);
        render();
    }

    document.querySelectorAll('[data-card-pager]').forEach(setup);
})();
