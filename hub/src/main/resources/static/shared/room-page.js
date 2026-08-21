/**
 * 牌类/麻将房间页公共逻辑。
 * 由 body[data-game-type] 区分：1麻将 2斗地主 3跑得快 4拖拉机。
 */
(function () {
    var sessionId = localStorage.getItem('sessionId');
    var gameType = parseInt(document.body.dataset.gameType, 10);
    var meta = RoomConfig.game(gameType);
    var gameName = meta.name;
    var roomPage = 1;
    function roomPageSize() {
        if (window.innerWidth < 600) return 1;
        if (window.innerWidth < 1000) return 2;
        return 4;
    }
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
        return RoomConfig.seats(roomId, meta.defaultSeats);
    }

    function ruleTip(room) {
        return RoomConfig.ruleTip(room.roomId, gameType);
    }

    function isOfficial(roomId) {
        return RoomConfig.isOfficial(roomId, gameType);
    }

    function isQuickRoom(roomId) {
        return RoomConfig.isQuick(roomId);
    }

    window.loadRooms = function () {
        var list = document.getElementById('roomList');
        list.innerHTML = '<div class="loading">正在加载' + gameName + '房间…</div>';
        fetch(appUrl('/api/rooms?sessionId=' + encodeURIComponent(sessionId)))
            .then(function (r) {
                return r.json();
            })
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
        var pageSize = roomPageSize();
        var pageCount = Math.max(1, Math.ceil(rooms.length / pageSize));
        roomPage = Math.min(Math.max(roomPage, 1), pageCount);
        var start = (roomPage - 1) * pageSize;
        rooms.slice(start, start + pageSize).forEach(function (room) {
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
        var pager = document.getElementById('roomPager');
        if (pageCount > 1) {
            if (!pager) {
                pager = document.createElement('nav');
                pager.id = 'roomPager';
                pager.className = 'room-pager';
                list.parentNode.appendChild(pager);
            }
            pager.innerHTML = '<button type="button" data-room-page="prev">上一页</button>'
                + '<span>' + roomPage + ' / ' + pageCount + '</span>'
                + '<button type="button" data-room-page="next">下一页</button>';
            pager.querySelector('[data-room-page="prev"]').disabled = roomPage === 1;
            pager.querySelector('[data-room-page="next"]').disabled = roomPage === pageCount;
            pager.querySelector('[data-room-page="prev"]').onclick = function () {
                roomPage--;
                renderRooms(rooms);
            };
            pager.querySelector('[data-room-page="next"]').onclick = function () {
                roomPage++;
                renderRooms(rooms);
            };
        } else if (pager) {
            pager.remove();
        }
    }

    function createAndEnter(roomId) {
        fetch(appUrl('/api/rooms/create'), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({sessionId: sessionId, mode: 'fixed', roomId: roomId, gameType: gameType})
        })
            .then(function (r) {
                return r.json();
            })
            .then(function (data) {
                if (data.code === 0 && data.tableId) {
                    goTable(data.tableId, data.roomId || roomId);
                    return;
                }
                return fetch(appUrl('/api/rooms?sessionId=' + encodeURIComponent(sessionId)))
                    .then(function (r) {
                        return r.json();
                    })
                    .then(function (listData) {
                        var mine = null;
                        (listData.rooms || []).forEach(function (room) {
                            if (Number(room.roomId) === Number(roomId) && room.myTableId) {
                                mine = room.myTableId;
                            }
                        });
                        if (mine) goTable(mine, roomId);
                        else AppDialog.alert((data && data.msg) || '创建房间失败');
                    });
            })
            .catch(function () {
                AppDialog.alert('网络错误，请重试');
            });
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
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({sessionId: sessionId})
        }).catch(function () {
        });
        localStorage.clear();
        window.location.href = appUrl('/');
    };

    loadRooms();
})();
