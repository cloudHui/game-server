(function (w) {
    'use strict';
    var page = 1;
    var types = {mahjong: ['麻将', '荆门麻将', '卡五星'], poker: ['斗地主', '跑得快', '拖拉机']};
    function showReplay(data, url) {
        if (data.code !== 0) return Admin.msg('replayMsg', data.msg || '读取失败', false);
        document.getElementById('replayDetailTitle').textContent = '回放 ' + data.date + '/' + data.name;
        ReplayPlayer.open(data, url);
    }
    function loadRecords() {
        Admin.get('/records?page=1&size=20').then(function (data) {
            var rows = data.records || [];
            document.getElementById('recordBody').innerHTML = rows.length ? rows.map(function (row) {
                return '<tr><td>' + row.tableId + '</td><td>' + row.round + '</td><td>' + row.userId + '</td><td>' + row.score + '</td><td>' + row.totalScore + '</td><td>' + row.winType + '</td></tr>';
            }).join('') : '<tr><td colspan="6">暂无落库战绩</td></tr>';
        });
    }
    function load(requestedPage) {
        page = Math.max(requestedPage || 1, 1);
        var category = document.getElementById('replayCategory').value;
        var gameType = document.getElementById('replayGameType').value;
        Admin.get('/replays?page=' + page + '&size=20&category=' + encodeURIComponent(category) + '&gameType=' + encodeURIComponent(gameType)).then(function (data) {
            var body = document.getElementById('replayBody'), list = data.replays || [];
            if (data.code !== 0) return void (body.innerHTML = '<tr><td colspan="7">' + (data.msg || '加载失败') + '</td></tr>');
            body.innerHTML = list.length ? list.map(function (row) {
                return '<tr><td>' + row.date + '</td><td class="token">' + row.name + '</td><td>' + (row.tableId || '-') + '</td><td>' + (row.round || '-') + '</td><td>' + (row.gameType || '-') + '</td><td>' + (row.status || '-') + '</td><td><button class="btn btn-ghost" onclick="openReplay(\'' + row.date + '\',\'' + row.name + '\')">查看</button></td></tr>';
            }).join('') : '<tr><td colspan="7">暂无回放</td></tr>';
            document.getElementById('replayPageLabel').textContent = '第 ' + data.page + ' 页 / 共 ' + data.total + ' 条';
            Admin.msg('replayMsg', '本页 ' + list.length + ' 条', true); loadRecords();
        }).catch(function () { Admin.msg('replayMsg', '网络错误', false); });
    }
    w.loadReplays = load;
    w.changeReplayPage = function (delta) { load(page + delta); };
    w.changeReplayCategory = function () {
        var list = types[document.getElementById('replayCategory').value] || [];
        document.getElementById('replayGameType').innerHTML = '<option value="">全部子玩法</option>' + list.map(function (type) { return '<option value="' + type + '">' + type + '</option>'; }).join('');
        load(1);
    };
    w.openReplayCode = function () {
        var code = document.getElementById('replayCode').value.trim(); if (!code) return;
        var path = '/replays/code?code=' + encodeURIComponent(code), url = appUrl('/api/admin' + path + '&sessionId=' + encodeURIComponent(Admin.sessionId));
        Admin.get(path).then(function (data) { showReplay(data, url); });
    };
    w.openReplay = function (date, name) {
        var path = '/replays/detail?date=' + encodeURIComponent(date) + '&name=' + encodeURIComponent(name);
        var url = appUrl('/api/admin' + path + '&sessionId=' + encodeURIComponent(Admin.sessionId));
        Admin.get(path).then(function (data) { showReplay(data, url); });
    };
})(window);
