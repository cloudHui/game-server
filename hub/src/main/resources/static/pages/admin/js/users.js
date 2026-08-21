(function (w) {
    'use strict';
    function badge(text, ok) { return '<span class="badge' + (ok ? ' badge-ok' : ' badge-bad') + '">' + text + '</span>'; }
    function load() {
        Admin.get('/users').then(function (data) {
            var body = document.getElementById('userBody'), list = data.users || [];
            if (data.code !== 0) return void (body.innerHTML = '<tr><td colspan="7">' + (data.msg || '加载失败') + '</td></tr>');
            document.getElementById('usersMeta').textContent = '在线 ' + (data.online || 0) + ' 人';
            body.innerHTML = list.length ? list.map(function (user) {
                var action = user.username === 'admin' ? '' : '<button class="btn ' + (user.enabled ? 'btn-danger' : '') + '" onclick="setEnabled(' + user.id + ',' + !user.enabled + ')">' + (user.enabled ? '禁用' : '启用') + '</button>';
                return '<tr><td>' + user.id + '</td><td>' + user.username + '</td><td>' + (user.nickname || '') + '</td><td>' + badge(user.online ? '在线' : '离线', user.online) + '</td><td>' + badge(user.enabled ? '启用' : '禁用', user.enabled) + '</td><td>' + (user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : '-') + '</td><td class="actions">' + action + '</td></tr>';
            }).join('') : '<tr><td colspan="7">暂无玩家</td></tr>';
            Admin.msg('usersMsg', '共 ' + list.length + ' 人', true);
        }).catch(function () { Admin.msg('usersMsg', '网络错误', false); });
    }
    w.loadUsers = load;
    w.setEnabled = function (userId, enabled) { Admin.post('/users/enable', {userId: userId, enabled: enabled}).then(function (data) { if (data.code === 0) load(); else Admin.msg('usersMsg', data.msg || '失败', false); }); };
})(window);
