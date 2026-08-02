var sessionId = localStorage.getItem('sessionId');
var isAdmin = localStorage.getItem('isAdmin') === '1';
if (!sessionId) {
    window.location.href = appUrl('/');
}
if (!isAdmin) {
    alert('需要管理员账号');
    window.location.href = appUrl('/pages/lobby/index.html');
}

function switchTab(name) {
    var tabs = document.querySelectorAll('.tab');
    for (var i = 0; i < tabs.length; i++) {
        tabs[i].className = 'tab' + (tabs[i].getAttribute('data-tab') === name ? ' active' : '');
    }
    var panels = document.querySelectorAll('.panel');
    for (var j = 0; j < panels.length; j++) {
        panels[j].className = 'panel' + (panels[j].id === 'panel-' + name ? ' active' : '');
    }
    if (name === 'users') loadUsers();
    if (name === 'tables') loadTables();
    if (name === 'records') loadReplays();
}

function setMsg(id, text, ok) {
    var el = document.getElementById(id);
    el.textContent = text || '';
    el.className = 'msg' + (ok === true ? ' ok' : ok === false ? ' err' : '');
}

function createInvite() {
    var note = document.getElementById('note').value.trim();
    var maxUses = parseInt(document.getElementById('maxUses').value, 10) || 1;
    var expiresDays = parseInt(document.getElementById('expiresDays').value, 10);
    if (isNaN(expiresDays)) expiresDays = 7;
    setMsg('createMsg', '创建中...');
    fetch(appUrl('/api/admin/invites'), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            sessionId: sessionId,
            note: note,
            maxUses: maxUses,
            expiresDays: expiresDays
        })
    })
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            if (data.code === 0 && data.invite) {
                setMsg('createMsg', '已创建: ' + data.invite.token, true);
                document.getElementById('note').value = '';
                loadInvites();
            } else {
                setMsg('createMsg', data.msg || '创建失败', false);
            }
        })
        .catch(function () {
            setMsg('createMsg', '网络错误', false);
        });
}

function loadInvites() {
    fetch(appUrl('/api/admin/invites?sessionId=' + encodeURIComponent(sessionId)))
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            var body = document.getElementById('inviteBody');
            if (data.code !== 0) {
                body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>';
                return;
            }
            var list = data.invites || [];
            if (!list.length) {
                body.innerHTML = '<tr><td colspan="6">暂无邀请码</td></tr>';
                return;
            }
            var html = '';
            for (var i = 0; i < list.length; i++) {
                var it = list[i];
                var exp = it.expiresAt ? new Date(it.expiresAt).toLocaleString() : '永久';
                var st = it.valid
                    ? '<span class="badge badge-ok">可用</span>'
                    : '<span class="badge badge-bad">失效</span>';
                html += '<tr><td class="token">' + it.token + '</td><td>' + (it.note || '') + '</td>'
                    + '<td>' + it.usedCount + '/' + it.maxUses + '</td><td>' + exp + '</td><td>' + st + '</td>'
                    + '<td class="actions"><button class="btn btn-ghost" onclick="copyInvite(\'' + it.token + '\')">复制链接</button>'
                    + (it.valid ? '<button class="btn btn-danger" onclick="revokeInvite(\'' + it.token + '\')">作废</button>' : '')
                    + '</td></tr>';
            }
            body.innerHTML = html;
            setMsg('listMsg', '共 ' + list.length + ' 条', true);
        })
        .catch(function () {
            setMsg('listMsg', '网络错误', false);
        });
}

function copyInvite(token) {
    var url = location.origin + appUrl('/?invite=' + encodeURIComponent(token));
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(function () {
            setMsg('listMsg', '已复制: ' + url, true);
        });
    } else {
        prompt('复制链接', url);
    }
}

function revokeInvite(token) {
    if (!confirm('作废邀请码 ' + token + ' ?')) return;
    fetch(appUrl('/api/admin/invites/revoke'), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({sessionId: sessionId, token: token})
    })
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            if (data.code === 0) {
                setMsg('listMsg', '已作废', true);
                loadInvites();
            } else {
                setMsg('listMsg', data.msg || '失败', false);
            }
        });
}

function loadUsers() {
    fetch(appUrl('/api/admin/users?sessionId=' + encodeURIComponent(sessionId)))
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            var body = document.getElementById('userBody');
            if (data.code !== 0) {
                body.innerHTML = '<tr><td colspan="7">' + (data.msg || '加载失败') + '</td></tr>';
                return;
            }
            document.getElementById('usersMeta').textContent = '在线 ' + (data.online || 0) + ' 人';
            var list = data.users || [];
            if (!list.length) {
                body.innerHTML = '<tr><td colspan="7">暂无玩家</td></tr>';
                return;
            }
            var html = '';
            for (var i = 0; i < list.length; i++) {
                var u = list[i];
                var online = u.online
                    ? '<span class="badge badge-ok">在线</span>'
                    : '<span class="badge">离线</span>';
                var en = u.enabled
                    ? '<span class="badge badge-ok">启用</span>'
                    : '<span class="badge badge-bad">禁用</span>';
                var login = u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '-';
                html += '<tr><td>' + u.id + '</td><td>' + u.username + '</td><td>' + (u.nickname || '') + '</td>'
                    + '<td>' + online + '</td><td>' + en + '</td><td>' + login + '</td><td class="actions">'
                    + (u.username === 'admin' ? '' :
                        (u.enabled
                            ? '<button class="btn btn-danger" onclick="setEnabled(' + u.id + ',false)">禁用</button>'
                            : '<button class="btn" onclick="setEnabled(' + u.id + ',true)">启用</button>'))
                    + '</td></tr>';
            }
            body.innerHTML = html;
            setMsg('usersMsg', '共 ' + list.length + ' 人', true);
        })
        .catch(function () {
            setMsg('usersMsg', '网络错误', false);
        });
}

function setEnabled(userId, enabled) {
    fetch(appUrl('/api/admin/users/enable'), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({sessionId: sessionId, userId: userId, enabled: enabled})
    })
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            if (data.code === 0) loadUsers();
            else setMsg('usersMsg', data.msg || '失败', false);
        });
}

function loadTables() {
    fetch(appUrl('/api/admin/tables?sessionId=' + encodeURIComponent(sessionId)))
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            var body = document.getElementById('tableBody');
            if (data.code !== 0) {
                body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>';
                return;
            }
            document.getElementById('tablesMeta').textContent = '在线用户 ' + (data.onlineUsers || 0);
            var rooms = data.rooms || [];
            var rows = [];
            for (var i = 0; i < rooms.length; i++) {
                var room = rooms[i];
                var tables = room.tables || [];
                for (var j = 0; j < tables.length; j++) {
                    var t = tables[j];
                    var gt = t.gameType === 1 ? '麻将' : (t.gameType === 2 ? '斗地主' : t.gameType);
                    rows.push('<tr><td>' + t.tableId + '</td><td>' + t.roomId + '</td><td>' + gt
                        + '</td><td>' + t.state + '</td><td>' + t.playerCount + '</td><td>'
                        + t.ownerId + '</td></tr>');
                }
            }
            body.innerHTML = rows.length ? rows.join('') : '<tr><td colspan="6">暂无桌子</td></tr>';
            setMsg('tablesMsg', '共 ' + rows.length + ' 桌', true);
        })
        .catch(function () {
            setMsg('tablesMsg', '网络错误', false);
        });
}

var replayPage = 1;

function loadReplays(page) {
    replayPage = Math.max(page || 1, 1);
    fetch(appUrl('/api/admin/replays?sessionId=' + encodeURIComponent(sessionId) + '&page=' + replayPage + '&size=20'))
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            var body = document.getElementById('replayBody');
            if (data.code !== 0) {
                body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>';
                return;
            }
            var list = data.replays || [];
            if (!list.length) {
                body.innerHTML = '<tr><td colspan="6">暂无回放（对局结算后才会生成）</td></tr>';
                setMsg('replayMsg', '0 条', true);
                loadRecords();
                return;
            }
            var html = '';
            for (var i = 0; i < list.length; i++) {
                var r = list[i];
                html += '<tr><td>' + r.date + '</td><td class="token">' + r.name + '</td><td>'
                    + (r.tableId || '-') + '</td><td>' + (r.round || '-') + '</td><td>'
                    + (r.gameType || '-') + '</td><td><button class="btn btn-ghost" onclick="openReplay(\''
                    + r.date + '\',\'' + r.name + '\')">查看</button></td></tr>';
            }
            body.innerHTML = html;
            document.getElementById('replayPageLabel').textContent = '第 ' + data.page + ' 页 / 共 ' + data.total + ' 条';
            setMsg('replayMsg', '本页 ' + list.length + ' 条', true);
            loadRecords();
        })
        .catch(function () {
            setMsg('replayMsg', '网络错误', false);
        });
}

function openReplayCode() {
    var code = document.getElementById('replayCode').value.trim();
    if (!code) return;
    fetch(appUrl('/api/admin/replays/code?sessionId=' + encodeURIComponent(sessionId) + '&code=' + encodeURIComponent(code)))
        .then(function (r) {
            return r.json();
        }).then(function (data) {
        if (data.code !== 0) {
            setMsg('replayMsg', data.msg || '读取失败', false);
            return;
        }
        document.getElementById('replayDetailCard').style.display = 'block';
        document.getElementById('replayDetailTitle').textContent = '回放 ' + data.date + '/' + data.name;
        document.getElementById('replayContent').textContent = data.content || '';
    });
}

function loadRecords() {
    fetch(appUrl('/api/admin/records?sessionId=' + encodeURIComponent(sessionId) + '&page=1&size=20'))
        .then(function (r) {
            return r.json();
        }).then(function (data) {
        var body = document.getElementById('recordBody'), rows = data.records || [];
        body.innerHTML = rows.length ? rows.map(function (r) {
            return '<tr><td>' + r.tableId + '</td><td>' + r.round + '</td><td>' + r.userId
                + '</td><td>' + r.score + '</td><td>' + r.totalScore + '</td><td>' + r.winType + '</td></tr>';
        }).join('') : '<tr><td colspan="6">暂无落库战绩</td></tr>';
    });
}

function openReplay(date, name) {
    fetch(appUrl('/api/admin/replays/detail?sessionId=' + encodeURIComponent(sessionId)
        + '&date=' + encodeURIComponent(date) + '&name=' + encodeURIComponent(name)))
        .then(function (r) {
            return r.json();
        })
        .then(function (data) {
            if (data.code !== 0) {
                setMsg('replayMsg', data.msg || '读取失败', false);
                return;
            }
            document.getElementById('replayDetailCard').style.display = 'block';
            document.getElementById('replayDetailTitle').textContent =
                '回放 ' + data.date + '/' + data.name;
            document.getElementById('replayContent').textContent = data.content || '';
        });
}

function doLogout() {
    localStorage.clear();
    window.location.href = appUrl('/');
}

loadInvites();
