(function (w) {
    'use strict';
    var sessionId = localStorage.getItem('sessionId');
    if (!sessionId) return void (location.href = appUrl('/'));
    function msg(text) { document.getElementById('msg').textContent = text || ''; }
    function load() {
        fetch(appUrl('/api/replays?sessionId=' + encodeURIComponent(sessionId) + '&page=1&size=20'))
            .then(function (response) { return response.json(); }).then(function (data) {
            if (data.code !== 0) return msg(data.msg);
            var rows = data.replays || [];
            document.getElementById('rows').innerHTML = rows.length ? rows.map(function (row) {
                return '<tr><td>' + row.date + '</td><td>' + row.replayCode + '</td><td>' + row.tableId + '</td><td>' + row.gameType + '</td><td>' + (row.status || '-') + '</td><td><button onclick="openReplayCode(\'' + row.replayCode + '\')">查看</button></td></tr>';
            }).join('') : '<tr><td colspan="6">暂无回放</td></tr>';
            msg('共 ' + data.total + ' 条');
        }).catch(function () { msg('网络错误'); });
    }
    function open(code) {
        document.getElementById('code').value = code || document.getElementById('code').value.trim();
        code = document.getElementById('code').value.trim(); if (!code) return;
        var url = appUrl('/api/replays/code?sessionId=' + encodeURIComponent(sessionId) + '&code=' + encodeURIComponent(code));
        fetch(url).then(function (response) { return response.json(); }).then(function (data) {
            if (data.code !== 0) return msg(data.msg);
            document.getElementById('replayDetailTitle').textContent = '回放 ' + data.replayCode;
            ReplayPlayer.open(data, url);
        }).catch(function () { msg('网络错误'); });
    }
    w.openReplayCode = open;
    load();
})(window);
