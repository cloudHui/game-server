(function (w) {
    'use strict';
    var reactivatingToken = '';
    function load() {
        Admin.get('/invites').then(function (data) {
            var body = document.getElementById('inviteBody'), list = data.invites || [];
            if (data.code !== 0) return void (body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>');
            body.innerHTML = list.length ? list.map(function (item) {
                var expiry = item.expiresAt ? new Date(item.expiresAt).toLocaleString() : '永久';
                var state = item.valid ? '<span class="badge badge-ok">可用</span>' : '<span class="badge badge-bad">失效</span>';
                return '<tr><td class="token">' + item.token + '</td><td>' + (item.note || '') + '</td><td>' + item.usedCount + '/' + item.maxUses + '</td><td>' + expiry + '</td><td>' + state + '</td><td class="actions"><button class="btn btn-ghost" onclick="copyInvite(\'' + item.token + '\')">复制链接</button>' + (item.valid ? '<button class="btn btn-danger" onclick="revokeInvite(\'' + item.token + '\')">作废</button>' : '<button class="btn btn-primary" onclick="reactivateInvite(\'' + item.token + '\',this)">重新激活</button>') + '</td></tr>';
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
        if (!navigator.clipboard) return AppDialog.prompt('邀请链接', url, '复制链接');
        navigator.clipboard.writeText(url).then(function () { Admin.msg('listMsg', '已复制: ' + url, true); });
    };
    w.revokeInvite = async function (token) {
        if (!await AppDialog.confirm('作废邀请码 ' + token + ' ?')) return;
        Admin.post('/invites/revoke', {token: token}).then(function (data) { if (data.code === 0) load(); else Admin.msg('listMsg', data.msg || '失败', false); });
    };
    w.reactivateInvite = function (token, anchor) {
        reactivatingToken = token;
        document.getElementById('reactivateExpiresDays').value = '7';
        document.getElementById('reactivateAdditionalUses').value = '1';
        document.getElementById('reactivateInviteError').textContent = '';
        var dialog = document.getElementById('reactivateInviteDialog');
        dialog.hidden = false;
        dialog.classList.remove('place-left', 'place-bottom');
        var rect = anchor.getBoundingClientRect(), width = dialog.offsetWidth, height = dialog.offsetHeight, gap = 10;
        var left = rect.right + gap, top = rect.top + (rect.height - height) / 2;
        if (left + width > window.innerWidth - 8) { left = rect.left - width - gap; dialog.classList.add('place-left'); }
        if (left < 8) { left = Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)); top = rect.bottom + gap; dialog.classList.add('place-bottom'); }
        top = Math.max(8, Math.min(top, window.innerHeight - height - 8));
        dialog.style.left = left + 'px'; dialog.style.top = top + 'px';
        setTimeout(function () { document.getElementById('reactivateExpiresDays').focus(); }, 0);
    };
    w.closeReactivateInvite = function () {
        document.getElementById('reactivateInviteDialog').hidden = true;
        reactivatingToken = '';
    };
    document.addEventListener('click', function (event) {
        var dialog = document.getElementById('reactivateInviteDialog');
        if (!dialog.hidden && !dialog.contains(event.target) && !event.target.closest('[onclick*="reactivateInvite"]')) w.closeReactivateInvite();
    });
    w.submitReactivateInvite = function () {
        var days = Number(document.getElementById('reactivateExpiresDays').value);
        var uses = Number(document.getElementById('reactivateAdditionalUses').value);
        var error = document.getElementById('reactivateInviteError');
        if (!Number.isInteger(days) || days < 0) { error.textContent = '有效期必须是大于等于 0 的整数'; return; }
        if (!Number.isInteger(uses) || uses < 1) { error.textContent = '追加次数必须是大于 0 的整数'; return; }
        var submit = document.getElementById('reactivateInviteSubmit');
        submit.disabled = true; submit.textContent = '提交中…'; error.textContent = '';
        Admin.post('/invites/reactivate', {token: reactivatingToken, expiresDays: days, additionalUses: uses}).then(function (data) {
            if (data.code === 0) { w.closeReactivateInvite(); Admin.msg('listMsg', '邀请码已重新激活', true); load(); }
            else error.textContent = data.msg || '重新激活失败';
        }).catch(function () { error.textContent = '网络错误，请稍后重试'; }).finally(function () { submit.disabled = false; submit.textContent = '确定'; });
    };
    load();
})(window);
