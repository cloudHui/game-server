(function (w) {
    'use strict';
    function load() {
        Admin.get('/invites').then(function (data) {
            var body = document.getElementById('inviteBody'), list = data.invites || [];
            if (data.code !== 0) return void (body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>');
            body.innerHTML = list.length ? list.map(function (item) {
                var expiry = item.expiresAt ? new Date(item.expiresAt).toLocaleString() : '永久';
                var state = item.valid ? '<span class="badge badge-ok">可用</span>' : '<span class="badge badge-bad">失效</span>';
                return '<tr><td class="token">' + item.token + '</td><td>' + (item.note || '') + '</td><td>' + item.usedCount + '/' + item.maxUses + '</td><td>' + expiry + '</td><td>' + state + '</td><td class="actions"><button class="btn btn-ghost" onclick="copyInvite(\'' + item.token + '\')">复制链接</button>' + (item.valid ? '<button class="btn btn-danger" onclick="revokeInvite(\'' + item.token + '\')">作废</button>' : '') + '</td></tr>';
            }).join('') : '<tr><td colspan="6">暂无邀请码</td></tr>';
            Admin.msg('listMsg', '共 ' + list.length + ' 条', true);
        }).catch(function () { Admin.msg('listMsg', '网络错误', false); });
    }
    w.loadInvites = load;
    w.createInvite = function () {
        var days = Number(document.getElementById('expiresDays').value);
        Admin.msg('createMsg', '创建中…');
        Admin.post('/invites', {note: document.getElementById('note').value.trim(), maxUses: Number(document.getElementById('maxUses').value) || 1, expiresDays: isNaN(days) ? 7 : days}).then(function (data) {
            if (data.code !== 0 || !data.invite) return Admin.msg('createMsg', data.msg || '创建失败', false);
            document.getElementById('note').value = ''; Admin.msg('createMsg', '已创建: ' + data.invite.token, true); load();
        }).catch(function () { Admin.msg('createMsg', '网络错误', false); });
    };
    w.copyInvite = function (token) {
        var url = location.origin + appUrl('/?invite=' + encodeURIComponent(token));
        if (!navigator.clipboard) return prompt('复制链接', url);
        navigator.clipboard.writeText(url).then(function () { Admin.msg('listMsg', '已复制: ' + url, true); });
    };
    w.revokeInvite = function (token) {
        if (!confirm('作废邀请码 ' + token + ' ?')) return;
        Admin.post('/invites/revoke', {token: token}).then(function (data) { if (data.code === 0) load(); else Admin.msg('listMsg', data.msg || '失败', false); });
    };
    load();
})(window);
