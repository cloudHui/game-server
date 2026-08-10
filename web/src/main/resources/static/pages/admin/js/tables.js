(function (w) {
    'use strict';
    var pollTimer, select = document.getElementById('robotMatchRoom');
    select.innerHTML = RoomConfig.robotTests().map(function (item) { return '<option value="' + item.roomId + '">' + item.name + '</option>'; }).join('');
    function checked(id) { return document.getElementById(id).checked ? 1 : 0; }
    function watchTable(tableId, attempt) {
        clearTimeout(pollTimer);
        Admin.get('/replays?page=1&size=100').then(function (data) {
            var replay=(data.replays||[]).find(function(item){return String(item.tableId)===String(tableId)&&item.status!=='已结算';})
                ||(data.replays||[]).find(function(item){return String(item.tableId)===String(tableId);});
            if(replay)return w.openReplay(replay.date,replay.name);
            if((attempt||0)>=60)return Admin.msg('tablesMsg','桌 '+tableId+' 已存在，但还没有生成首个回放事件。',false);
            pollTimer=setTimeout(function(){watchTable(tableId,(attempt||0)+1)},1000);
        }).catch(function(){pollTimer=setTimeout(function(){watchTable(tableId,(attempt||0)+1)},1500)});
    }
    function load(){Admin.get('/tables').then(function(data){
        var body=document.getElementById('tableBody'),rows=[];if(data.code!==0){body.innerHTML='<tr><td colspan="6">'+(data.msg||'加载失败')+'</td></tr>';return}
        document.getElementById('tablesMeta').textContent='在线用户 '+(data.onlineUsers||0);
        (data.rooms||[]).forEach(function(room){(room.tables||[]).forEach(function(table){var game=RoomConfig.game(table.gameType);rows.push('<tr><td>'+table.tableId+'</td><td>'+game.name+'</td><td>'+table.state+'</td><td>'+table.playerCount+'</td><td>'+table.ownerId+'</td><td><button class="btn btn-ghost" onclick="watchLiveTable('+table.tableId+')">进入观看</button></td></tr>')})});
        body.innerHTML=rows.length?rows.join(''):'<tr><td colspan="6">暂无桌子</td></tr>';Admin.msg('tablesMsg','共 '+rows.length+' 桌',true);
    }).catch(function(){Admin.msg('tablesMsg','网络错误',false)})}
    w.loadTables=load;w.watchLiveTable=function(tableId){Admin.msg('tablesMsg','正在进入桌 '+tableId+'…');watchTable(tableId,0)};
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
            Admin.msg('robotMatchMsg', '测试已启动：桌号 ' + data.tableId + '，正在进入实时对局。', true);load();watchTable(data.tableId,0);
        }).catch(function () { button.disabled = false; Admin.msg('robotMatchMsg', '网络错误', false); });
    };
    w.changeRobotRules();
})(window);
