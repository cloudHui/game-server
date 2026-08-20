(function (w) {
    'use strict';
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
    w.Admin = {sessionId: sessionId, get: function (path) { return request(path); }, post: function (path, data) { return request(path, {method: 'POST', data: data}); }, msg: msg};
    w.setMsg = msg;
    w.switchTab = function (name) {
        document.querySelectorAll('.tab').forEach(function (el) { el.classList.toggle('active', el.dataset.tab === name); });
        document.querySelectorAll('.panel').forEach(function (el) { el.classList.toggle('active', el.id === 'panel-' + name); });
        var loaders = {users: w.loadUsers, arena: w.loadArenaPlayers, tables: w.loadTables, records: w.loadReplays, photos: w.loadPhotoAdmin};
        if (loaders[name]) loaders[name]();
    };
    w.doLogout = function () { localStorage.clear(); location.href = appUrl('/'); };
})(window);
