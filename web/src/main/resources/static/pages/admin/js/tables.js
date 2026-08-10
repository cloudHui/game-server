(function (w) {
    'use strict';
    var pollTimer;
    var select = document.getElementById('robotMatchRoom');
    select.innerHTML = RoomConfig.robotTests().map(function (item) { return '<option value="' + item.roomId + '">' + item.name + '</option>'; }).join('');
    function load() {
        Admin.get('/tables').then(function (data) {
            var body = document.getElementById('tableBody'), rows = [];
            if (data.code !== 0) return void (body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>');
            document.getElementById('tablesMeta').textContent = '在线用户 ' + (data.onlineUsers || 0);
            (data.rooms || []).forEach(function (room) { (room.tables || []).forEach(function (table) {
                var game = RoomConfig.game(table.gameType);
                rows.push('<tr><td>' + table.tableId + '</td><td>' + table.roomId + '</td><td>' + game.name + '</td><td>' + table.state + '</td><td>' + table.playerCount + '</td><td>' + table.ownerId + '</td></tr>');
            }); });
            body.innerHTML = rows.length ? rows.join('') : '<tr><td colspan="6">暂无桌子</td></tr>';
            Admin.msg('tablesMsg', '共 ' + rows.length + ' 桌', true);
        }).catch(function () { Admin.msg('tablesMsg', '网络错误', false); });
    }
    function waitReplay(tableId, attempt) {
        clearTimeout(pollTimer);
        Admin.get('/replays?page=1&size=100').then(function (data) {
            var replay = (data.replays || []).find(function (item) { return String(item.tableId) === tableId; });
            if (replay) { Admin.msg('robotMatchMsg', '已进入验收桌 ' + tableId + '。', true); return w.openReplay(replay.date, replay.name); }
            if (attempt >= 60) return Admin.msg('robotMatchMsg', '对局已启动，回放稍后可在战绩/回放中查看。', false);
            pollTimer = setTimeout(function () { waitReplay(tableId, attempt + 1); }, 1000);
        }).catch(function () { pollTimer = setTimeout(function () { waitReplay(tableId, attempt + 1); }, 1500); });
    }
    w.loadTables = load;
    w.startRobotMatch = function () {
        var button = document.getElementById('robotMatchStart'); button.disabled = true;
        Admin.msg('robotMatchMsg', '正在创建真实机器人对局…');
        Admin.post('/robot-matches', {roomId: Number(select.value)}).then(function (data) {
            button.disabled = false;
            if (data.code !== 0) return Admin.msg('robotMatchMsg', data.msg || '启动失败', false);
            Admin.msg('robotMatchMsg', '验收桌 ' + data.tableId + ' 已启动，正在等待首个回放快照…', true);
            load(); waitReplay(String(data.tableId), 0);
        }).catch(function () { button.disabled = false; Admin.msg('robotMatchMsg', '网络错误', false); });
    };
})(window);
