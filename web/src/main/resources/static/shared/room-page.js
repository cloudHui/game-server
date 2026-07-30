/**
 * 牌类/麻将房间页公共逻辑。
 * 由 body[data-game-type] 区分：1麻将 2斗地主 3跑得快 4拖拉机。
 */
(function () {
    var sessionId = localStorage.getItem('sessionId');
    var gameType = parseInt(document.body.dataset.gameType, 10);
    var META = {
        1: { name: '麻将', official: [1, 11, 12, 9001], page: '/pages/games/mahjong/index.html', defaultSeats: 4 },
        2: { name: '斗地主', official: [2, 9002, 9003], page: '/pages/games/doudizhu/index.html', defaultSeats: 3 },
        3: { name: '跑得快', official: [9010], page: '/pages/games/paodekuai/index.html', defaultSeats: 3 },
        4: { name: '拖拉机', official: [9011], page: '/pages/games/tractor/index.html', defaultSeats: 4 }
    };
    var meta = META[gameType] || META[2];
    var gameName = meta.name;
    var OFFICIAL = meta.official;
    var SEAT_BY_ROOM = {
        1: 4, 11: 2, 12: 3, 9001: 4,
        2: 3, 9002: 3, 9003: 3,
        9010: 3, 9011: 4
    };
    if (!sessionId) {
        window.location.href = appUrl('/');
        return;
    }
    document.getElementById('userDisplay').textContent =
        localStorage.getItem('nickname') || localStorage.getItem('username') || '玩家';

    function errorText(message) {
        return '<div class="empty error">' + (message || '加载失败，请稍后重试') + '</div>';
    }

    function seatOf(roomId) {
        return SEAT_BY_ROOM[Number(roomId)] || meta.defaultSeats;
    }

    function ruleTip(room) {
        var seats = seatOf(room.roomId);
        if (room.roomId === 9003) return '电脑快速房间 · 叫地主后逆时针抢/再抢 · 抢一次倍数翻倍';
        if (room.roomId === 9002) return '经典斗地主 · ' + seats + '人 · 17张手牌 · 3张底牌 · 轮流叫/抢地主';
        if (room.roomId === 9001) return '快速麻将 · ' + seats + '人满开 · 13张手牌 · 吃/碰/杠/胡 · 自动连局';
        if (room.roomId === 2) return '经典斗地主 · ' + seats + '人 · 手动准备下一局';
        if (room.roomId === 1) return '经典麻将 · ' + seats + '人满开 · 手动准备下一局';
        if (room.roomId === 11 || room.roomId === 12) return '麻将 · ' + seats + '人满开 · 13张手牌 · 支持吃/碰/杠/胡';
        if (room.roomId === 9010) return '跑得快 · ' + seats + '人 · 16张手牌 · 方块3先出 · 自动连局';
        if (room.roomId === 9011) return '拖拉机 · ' + seats + '人 · 两副牌 · 级牌升级 · 自动连局';
        if (gameType === 3) return '跑得快 · ' + seats + '人 · 16张手牌';
        if (gameType === 4) return '拖拉机 · ' + seats + '人 · 两副牌';
        if (gameType === 2) return '经典斗地主 · ' + seats + '人 · 17张手牌 · 3张底牌';
        return '麻将 · ' + seats + '人满开 · 13张手牌 · 支持吃/碰/杠/胡';
    }

    function isOfficial(roomId) {
        return OFFICIAL.indexOf(Number(roomId)) >= 0;
    }

    function isQuickRoom(roomId) {
        return roomId === 9001 || roomId === 9002 || roomId === 9003
            || roomId === 9010 || roomId === 9011;
    }

    window.loadRooms = function () {
        var list = document.getElementById('roomList');
        list.innerHTML = '<div class="loading">正在加载' + gameName + '房间…</div>';
        fetch(appUrl('/api/rooms?sessionId=' + encodeURIComponent(sessionId)))
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.code === 401) {
                    window.location.href = appUrl('/');
                    return;
                }
                if (data.code !== 0) {
                    list.innerHTML = errorText(data.msg);
                    return;
                }
                var rooms = (data.rooms || []).filter(function (room) {
                    return Number(room.gameType) === gameType && isOfficial(room.roomId);
                });
                rooms.sort(function (a, b) {
                    var sa = seatOf(a.roomId), sb = seatOf(b.roomId);
                    if (sa !== sb) return sa - sb;
                    return Number(a.roomId) - Number(b.roomId);
                });
                renderRooms(rooms);
            })
            .catch(function () {
                list.innerHTML = errorText('网络错误，请点击右上角重试');
            });
    };

    function renderRooms(rooms) {
        var list = document.getElementById('roomList');
        if (!rooms.length) {
            list.innerHTML = '<div class="empty">暂无' + gameName + '房间模板</div>';
            return;
        }
        list.innerHTML = '';
        rooms.forEach(function (room) {
            var card = document.createElement('article');
            card.className = 'room-card';
            var title = document.createElement('h2');
            title.textContent = gameName + ' · ' + seatOf(room.roomId) + '人 · #' + room.roomId;
            if (isQuickRoom(room.roomId)) {
                var fast = document.createElement('span');
                fast.className = 'quick-room-badge';
                fast.textContent = '快速房间';
                title.appendChild(fast);
            }
            card.appendChild(title);
            var desc = document.createElement('p');
            desc.textContent = ruleTip(room);
            card.appendChild(desc);

            var tableList = document.createElement('div');
            tableList.className = 'table-list';
            var need = seatOf(room.roomId);
            (room.tables || []).forEach(function (table) {
                var row = document.createElement('div');
                row.className = 'table-item';
                var info = document.createElement('span');
                info.className = 'table-info';
                var names = (table.players || []).map(function (p) {
                    return p.nickName || ('玩家' + p.roleId);
                });
                info.textContent = '桌号 ' + table.tableId + ' · ' + (names.join('、') || '空桌')
                    + ' · ' + table.playerCount + '/' + need + '人';
                var state = document.createElement('span');
                state.className = 'table-status ' + (table.stat === 0 ? 'waiting' : 'playing');
                state.textContent = table.stat === 0 ? '等待中' : '游戏中';
                row.appendChild(info);
                row.appendChild(state);
                tableList.appendChild(row);
            });
            card.appendChild(tableList);

            var button = document.createElement('button');
            button.className = 'join';
            button.textContent = room.myTableId ? '返回房间' : '创建房间';
            button.onclick = function () {
                room.myTableId ? goTable(room.myTableId, room.roomId) : createAndEnter(room.roomId);
            };
            card.appendChild(button);
            list.appendChild(card);
        });
    }

    function createAndEnter(roomId) {
        fetch(appUrl('/api/rooms/create'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: sessionId, mode: 'fixed', roomId: roomId, gameType: gameType })
        })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.code === 0 && data.tableId) {
                    goTable(data.tableId, data.roomId || roomId);
                    return;
                }
                return fetch(appUrl('/api/rooms?sessionId=' + encodeURIComponent(sessionId)))
                    .then(function (r) { return r.json(); })
                    .then(function (listData) {
                        var mine = null;
                        (listData.rooms || []).forEach(function (room) {
                            if (Number(room.roomId) === Number(roomId) && room.myTableId) {
                                mine = room.myTableId;
                            }
                        });
                        if (mine) goTable(mine, roomId);
                        else alert((data && data.msg) || '创建房间失败');
                    });
            })
            .catch(function () { alert('网络错误，请重试'); });
    }

    function goTable(tableId, roomId) {
        localStorage.setItem('tableId', tableId);
        localStorage.setItem('roomId', roomId);
        localStorage.setItem('gameType', gameType);
        localStorage.setItem('seatNum', String(seatOf(roomId)));
        window.location.href = appUrl(meta.page);
    }

    window.logout = function () {
        fetch(appUrl('/api/logout'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: sessionId })
        }).catch(function () {});
        localStorage.clear();
        window.location.href = appUrl('/');
    };

    loadRooms();
})();
