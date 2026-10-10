(function (w) {
    'use strict';
    if (location.protocol === 'file:') {
        if (!localStorage.getItem('sessionId')) localStorage.setItem('sessionId', 'mock_admin_session_12345');
        if (!localStorage.getItem('isAdmin')) localStorage.setItem('isAdmin', '1');
        if (!localStorage.getItem('username')) localStorage.setItem('username', 'admin');
    }
    var sessionId = localStorage.getItem('sessionId');
    if (!sessionId) return void (location.href = appUrl('/'));
    if (localStorage.getItem('isAdmin') !== '1') {
        AppDialog.alert('需要管理员账号').then(function () { location.href = appUrl('/pages/lobby/index.html'); });
        return;
    }

    function request(path, options) {
        options = options || {};
        if (options.method === 'POST') {
            options.headers = {'Content-Type': 'application/json'};
            options.body = JSON.stringify(Object.assign({sessionId: sessionId}, options.data || {}));
            delete options.data;
        } else {
            path += (path.indexOf('?') >= 0 ? '&' : '?') + 'sessionId=' + encodeURIComponent(sessionId);
        }
        return fetch(appUrl('/api/admin' + path), options).then(function (response) { return response.json(); });
    }

    function msg(id, text, ok) {
        var el = document.getElementById(id); if (!el) return;
        el.textContent = text || '';
        el.className = 'msg' + (ok === true ? ' ok' : ok === false ? ' err' : '');
    }

    w.Admin = {
        sessionId: sessionId,
        get: function (path) { return request(path); },
        post: function (path, data) { return request(path, {method: 'POST', data: data}); },
        msg: msg
    };
    w.setMsg = msg;

    /* ==========================================================================
       RuoYi 多标签页 (TagsView) 与 侧边栏菜单元数据
       ========================================================================== */
    var TAB_CONFIG = {
        metrics: { title: '系统监控大盘', parent: '监控总览', icon: '📊' },
        invites: { title: '邀请码管理', parent: '用户与权限', icon: '🎫' },
        users: { title: '玩家用户列表', parent: '用户与权限', icon: '👥' },
        arpu: { title: 'ARPU 数值查询', parent: '用户与权限', icon: '💰' },
        tables: { title: '实时桌况 / 测试', parent: '游戏运营', icon: '⚡' },
        records: { title: '战绩 / 回放记录', parent: '游戏运营', icon: '📜' },
        arena: { title: '剑气除魔运营', parent: '游戏运营', icon: '⚔️' },
        photos: { title: '图片资源管理', parent: '系统工具箱', icon: '🖼️' },
        sequence: { title: '序列均值分析', parent: '系统工具箱', icon: '🎯', isIframe: true, url: './sequence.html' },
        terminal: { title: '服务器终端', parent: '系统工具箱', icon: '💻', isIframe: true, url: './terminal.html' },
        learning: { title: '学习管理中心', parent: '系统工具箱', icon: '📚', isIframe: true, url: '../learning/admin.html' }
    };

    var openedTabs = ['metrics'];
    var currentActiveTab = 'metrics';

    function renderTags() {
        var container = document.getElementById('tagsViewList');
        if (!container) return;
        container.innerHTML = openedTabs.map(function (tabId) {
            var cfg = TAB_CONFIG[tabId] || { title: tabId };
            var isActive = tabId === currentActiveTab;
            var closeBtn = (tabId !== 'metrics')
                ? '<i class="tags-close" onclick="closeTag(event, \'' + tabId + '\')" title="关闭页签">×</i>'
                : '';
            return '<div class="tags-view-item ' + (isActive ? 'active' : '') + '" data-tab="' + tabId + '" onclick="switchTab(\'' + tabId + '\')">'
                + '<span class="tags-dot"></span>'
                + '<span>' + cfg.title + '</span>'
                + closeBtn
                + '</div>';
        }).join('');

        // 保持激活标签可视
        var activeEl = container.querySelector('.tags-view-item.active');
        if (activeEl && activeEl.scrollIntoView) {
            activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
        }
    }

    w.switchTab = function (name) {
        if (!TAB_CONFIG[name]) name = 'metrics';
        currentActiveTab = name;

        if (openedTabs.indexOf(name) < 0) {
            openedTabs.push(name);
        }
        renderTags();

        // 侧边栏高亮
        document.querySelectorAll('.menu-item').forEach(function (el) {
            el.classList.toggle('active', el.dataset.tab === name);
        });

        // 兼容旧 .tab 选择器
        document.querySelectorAll('.tab').forEach(function (el) {
            el.classList.toggle('active', el.dataset.tab === name);
        });

        // 面板切换
        document.querySelectorAll('.panel').forEach(function (el) {
            el.classList.toggle('active', el.id === 'panel-' + name);
        });

        // 更新 RuoYi 面包屑
        var cfg = TAB_CONFIG[name];
        var parentEl = document.getElementById('breadcrumbParent');
        var activeEl = document.getElementById('breadcrumbActive');
        if (parentEl && activeEl && cfg) {
            parentEl.textContent = cfg.parent;
            activeEl.textContent = cfg.title;
        }

        // 懒加载 iframe 工具
        if (cfg && cfg.isIframe) {
            var frame = document.getElementById('iframe-' + name);
            if (frame && !frame.getAttribute('src')) {
                frame.setAttribute('src', cfg.url);
            }
        }

        // 调用对应业务加载器
        var loaders = {
            users: w.loadUsers,
            arena: w.loadArenaPlayers,
            tables: w.loadTables,
            records: w.loadReplays,
            photos: w.loadPhotoAdmin,
            metrics: w.loadMetrics,
            invites: w.loadInvites
        };
        if (loaders[name]) loaders[name]();
    };

    w.closeTag = function (event, tabId) {
        if (event) event.stopPropagation();
        if (tabId === 'metrics') return;
        var idx = openedTabs.indexOf(tabId);
        if (idx >= 0) {
            openedTabs.splice(idx, 1);
            if (currentActiveTab === tabId) {
                var nextTab = openedTabs[Math.max(0, idx - 1)] || 'metrics';
                w.switchTab(nextTab);
            } else {
                renderTags();
            }
        }
    };

    w.closeOtherTags = function () {
        openedTabs = ['metrics'];
        if (currentActiveTab !== 'metrics') {
            openedTabs.push(currentActiveTab);
        }
        renderTags();
        var menu = document.getElementById('tagsActionMenu');
        if (menu) menu.classList.remove('show');
    };

    w.closeAllTags = function () {
        openedTabs = ['metrics'];
        w.switchTab('metrics');
        var menu = document.getElementById('tagsActionMenu');
        if (menu) menu.classList.remove('show');
    };

    w.refreshCurrentTab = function () {
        var cfg = TAB_CONFIG[currentActiveTab];
        if (cfg && cfg.isIframe) {
            var frame = document.getElementById('iframe-' + currentActiveTab);
            if (frame) {
                frame.src = cfg.url;
            }
        } else {
            var loaders = {
                users: w.loadUsers,
                arena: w.loadArenaPlayers,
                tables: w.loadTables,
                records: w.loadReplays,
                photos: w.loadPhotoAdmin,
                metrics: w.loadMetrics,
                invites: w.loadInvites
            };
            if (loaders[currentActiveTab]) loaders[currentActiveTab]();
        }
    };

    w.toggleSidebarCollapse = function () {
        var wrapper = document.getElementById('appWrapper');
        if (!wrapper) return;
        wrapper.classList.toggle('sidebar-collapsed');
        var isCollapsed = wrapper.classList.contains('sidebar-collapsed');
        localStorage.setItem('hub_sidebar_collapsed', isCollapsed ? '1' : '0');
    };

    w.toggleFullScreen = function () {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(function () {});
        } else {
            if (document.exitFullscreen) {
                document.exitFullscreen();
            }
        }
    };

    w.toggleUserDropdown = function (e) {
        if (e) e.stopPropagation();
        var menu = document.getElementById('avatarMenu');
        if (menu) menu.classList.toggle('show');
    };

    w.toggleTagsDropdown = function (e) {
        if (e) e.stopPropagation();
        var menu = document.getElementById('tagsActionMenu');
        if (menu) menu.classList.toggle('show');
    };

    w.doLogout = function () {
        localStorage.clear();
        location.href = appUrl('/');
    };

    // 全局事件监听：关闭下拉菜单
    document.addEventListener('click', function () {
        var menu = document.getElementById('avatarMenu');
        if (menu) menu.classList.remove('show');
        var tagsMenu = document.getElementById('tagsActionMenu');
        if (tagsMenu) tagsMenu.classList.remove('show');
    });

    // 页面初始化恢复状态
    document.addEventListener('DOMContentLoaded', function () {
        var wrapper = document.getElementById('appWrapper');
        if (wrapper && localStorage.getItem('hub_sidebar_collapsed') === '1') {
            wrapper.classList.add('sidebar-collapsed');
        }

        var uname = localStorage.getItem('username') || 'admin';
        var navUname = document.getElementById('navUsername');
        if (navUname) navUname.textContent = uname;

        // 根据 URL query 或者默认展示监控大盘
        var params = new URLSearchParams(location.search);
        var initialTab = params.get('tab') || 'metrics';
        w.switchTab(initialTab);
    });

})(window);
