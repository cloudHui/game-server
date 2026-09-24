(function (w) {
    'use strict';

    var pollTimer, watchGeneration = 0;
    var select = document.getElementById('robotMatchRoom');
    if (select && w.RoomConfig) {
        select.innerHTML = RoomConfig.robotTests().map(function (item) {
            return '<option value="' + item.roomId + '">' + item.name + '</option>';
        }).join('');
    }

    function checked(id) {
        var el = document.getElementById(id);
        return el && el.checked ? 1 : 0;
    }

    function watchTable(tableId, attempt, generation) {
        if (generation !== watchGeneration) return;
        clearTimeout(pollTimer);
        Admin.get('/replays?page=1&size=100').then(function (data) {
            if (generation !== watchGeneration) return;
            var replay = (data.replays || []).find(function (item) {
                return String(item.tableId) === String(tableId) && item.status !== '已结算';
            }) || (data.replays || []).find(function (item) {
                return String(item.tableId) === String(tableId);
            });
            if (replay) return w.openReplay(replay.date, replay.name);
            if ((attempt || 0) >= 60) return Admin.msg('tablesMsg', '桌 ' + tableId + ' 已存在，但还没有生成首个回放事件。', false);
            pollTimer = setTimeout(function () {
                watchTable(tableId, (attempt || 0) + 1, generation);
            }, 1000);
        }).catch(function () {
            if (generation === watchGeneration) {
                pollTimer = setTimeout(function () {
                    watchTable(tableId, (attempt || 0) + 1, generation);
                }, 1500);
            }
        });
    }

    w.watchLiveTable = function (tableId) {
        Admin.msg('tablesMsg', '正在进入桌 ' + tableId + '…');
        ReplayPlayer.waiting(tableId);
        watchGeneration++;
        watchTable(tableId, 0, watchGeneration);
    };

    w.changeRobotRules = function () {
        if (!select) return;
        var roomId = Number(select.value);
        var mjRules = document.getElementById('robotMahjongRules');
        if (mjRules) {
            mjRules.style.display = roomId === 9001 ? 'flex' : 'none';
        }
    };

    w.startRobotMatch = function () {
        var button = document.getElementById('robotMatchStart');
        if (button) button.disabled = true;
        var payload = {
            roomId: Number(select.value),
            totalRounds: Number(document.getElementById('robotMatchRounds').value),
            baseScore: Number(document.getElementById('robotBaseScore').value),
            maxFan: Number(document.getElementById('robotMaxFan').value),
            allowChi: checked('robotAllowChi'),
            allowDianPao: checked('robotAllowDianPao'),
            allowGang: checked('robotAllowGang'),
            allowSevenPairs: checked('robotAllowSevenPairs'),
            allowMultiHu: checked('robotAllowMultiHu')
        };
        Admin.msg('robotMatchMsg', '正在创建机器人测试…');
        Admin.post('/robot-matches', payload).then(function (data) {
            if (button) button.disabled = false;
            if (data.code !== 0) return Admin.msg('robotMatchMsg', data.msg || '启动失败', false);
            Admin.msg('robotMatchMsg', '测试已启动：桌号 ' + data.tableId + '，正在进入实时对局。', true);
            if (w.TableInspector) w.TableInspector.loadList(false);
            ReplayPlayer.waiting(data.tableId);
            watchGeneration++;
            watchTable(data.tableId, 0, watchGeneration);
        }).catch(function () {
            if (button) button.disabled = false;
            Admin.msg('robotMatchMsg', '网络错误', false);
        });
    };

    // 适配 core.js 的 tab 加载器
    w.loadTables = function () {
        if (w.TableInspector) {
            w.TableInspector.loadList(false);
        }
    };

    w.changeRobotRules();
    document.addEventListener('replay-player-close', function () {
        watchGeneration++;
        clearTimeout(pollTimer);
    });
})(window);
