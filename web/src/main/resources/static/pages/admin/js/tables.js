(function (w) {
    'use strict';
    var select = document.getElementById('robotMatchRoom');
    select.innerHTML = RoomConfig.robotTests().map(function (item) { return '<option value="' + item.roomId + '">' + item.name + '</option>'; }).join('');
    function checked(id) { return document.getElementById(id).checked ? 1 : 0; }
    w.changeRobotRules = function () {
        var roomId = Number(select.value);
        document.getElementById('robotMahjongRules').style.display = roomId === 9001 ? 'flex' : 'none';
    };
    w.startRobotMatch = function () {
        var button = document.getElementById('robotMatchStart'); button.disabled = true;
        var payload = {
            roomId: Number(select.value),
            totalRounds: Number(document.getElementById('robotMatchRounds').value),
            baseScore: Number(document.getElementById('robotBaseScore').value),
            maxFan: Number(document.getElementById('robotMaxFan').value),
            allowChi: checked('robotAllowChi'), allowDianPao: checked('robotAllowDianPao'),
            allowGang: checked('robotAllowGang'), allowSevenPairs: checked('robotAllowSevenPairs'),
            allowMultiHu: checked('robotAllowMultiHu')
        };
        Admin.msg('robotMatchMsg', '正在创建机器人测试…');
        Admin.post('/robot-matches', payload).then(function (data) {
            button.disabled = false;
            if (data.code !== 0) return Admin.msg('robotMatchMsg', data.msg || '启动失败', false);
            Admin.msg('robotMatchMsg', '测试已启动：桌号 ' + data.tableId + '，共 ' + payload.totalRounds + ' 局。完成后可到“战绩/回放”查看。', true);
        }).catch(function () { button.disabled = false; Admin.msg('robotMatchMsg', '网络错误', false); });
    };
    w.changeRobotRules();
})(window);
