/** 牌桌模板与页面元数据的前端唯一来源。服务端规则仍以 RobotRoomTemplates 为准。 */
(function (w) {
    'use strict';
    var rooms = {
        1: {gameType: 1, seats: 4}, 11: {gameType: 1, seats: 2}, 12: {gameType: 1, seats: 3},
        2: {gameType: 2, seats: 3},
        9001: {gameType: 1, seats: 4, quick: true, testName: '麻将（四人）'},
        9002: {gameType: 2, seats: 3, quick: true, testName: '斗地主（叫地主）'},
        9003: {gameType: 2, seats: 3, quick: true, testName: '斗地主（抢地主）'},
        9010: {gameType: 3, seats: 3, quick: true, testName: '跑得快（三人）'},
        9011: {gameType: 4, seats: 4, quick: true, testName: '拖拉机（四人两副）'}
    };
    var games = {
        1: {name: '麻将', page: '/pages/games/mahjong/index.html', defaultSeats: 4},
        2: {name: '斗地主', page: '/pages/games/doudizhu/index.html', defaultSeats: 3},
        3: {name: '跑得快', page: '/pages/games/paodekuai/index.html', defaultSeats: 3},
        4: {name: '拖拉机', page: '/pages/games/tractor/index.html', defaultSeats: 4}
    };
    function room(roomId) { return rooms[Number(roomId)] || null; }
    function ruleTip(roomId, gameType) {
        roomId = Number(roomId); var item = room(roomId), seats = item ? item.seats : (games[gameType] || games[2]).defaultSeats;
        var tips = {
            1: '经典麻将 · ' + seats + '人满开 · 手动准备下一局',
            2: '经典斗地主 · ' + seats + '人 · 手动准备下一局',
            11: '麻将 · ' + seats + '人满开 · 13张手牌 · 支持吃/碰/杠/胡',
            12: '麻将 · ' + seats + '人满开 · 13张手牌 · 支持吃/碰/杠/胡',
            9001: '快速麻将 · 4局 · ' + seats + '人满开 · 13张手牌 · 吃/碰/杠/胡',
            9002: '经典斗地主 · 4局 · ' + seats + '人 · 17张手牌 · 3张底牌 · 轮流叫/抢地主',
            9003: '电脑快速房间 · 4局 · 叫地主后逆时针抢/再抢 · 抢一次倍数翻倍',
            9010: '跑得快 · 4局 · ' + seats + '人 · 16张手牌 · 方块3先出',
            9011: '拖拉机 · 4局 · ' + seats + '人 · 两副牌 · 级牌升级'
        };
        if (tips[roomId]) return tips[roomId];
        if (gameType === 3) return '跑得快 · ' + seats + '人 · 16张手牌';
        if (gameType === 4) return '拖拉机 · ' + seats + '人 · 两副牌';
        if (gameType === 2) return '经典斗地主 · ' + seats + '人 · 17张手牌 · 3张底牌';
        return '麻将 · ' + seats + '人满开 · 13张手牌 · 支持吃/碰/杠/胡';
    }
    w.RoomConfig = {
        game: function (gameType) { return games[Number(gameType)] || games[2]; },
        seats: function (roomId, fallback) { var item = room(roomId); return item ? item.seats : fallback; },
        isOfficial: function (roomId, gameType) { var item = room(roomId); return !!item && (!gameType || item.gameType === Number(gameType)); },
        isQuick: function (roomId) { var item = room(roomId); return !!(item && item.quick); },
        ruleTip: ruleTip,
        officialIds: function (gameType) { return Object.keys(rooms).map(Number).filter(function (id) { return rooms[id].gameType === Number(gameType); }); },
        robotTests: function () { return Object.keys(rooms).map(Number).filter(function (id) { return rooms[id].quick; }).map(function (id) { return {roomId: id, name: rooms[id].testName}; }); }
    };
})(window);
