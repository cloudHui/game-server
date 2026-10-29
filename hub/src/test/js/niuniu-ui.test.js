const assert = require('assert');

// 挂载浏览器全局别名供前端 IIFE 模块导出使用
global.window = global;

require('../../main/resources/static/shared/room-config.js');
require('../../main/resources/static/pages/games/niuniu/niuniu-rule.js');
require('../../main/resources/static/shared/replay-model.js');

// 1. 校验 RoomConfig 已注册牛牛经典房 #5 与快速机器人房 #9020
const RC = global.RoomConfig;
assert.ok(RC, 'RoomConfig 应挂载在 window 上');
assert.deepStrictEqual(RC.game(5), { name: '牛牛', page: '/pages/games/niuniu/index.html', defaultSeats: 4 });
assert.strictEqual(RC.isOfficial(5, 5), true);
assert.strictEqual(RC.isOfficial(9020, 5), true);
assert.strictEqual(RC.isQuick(5), false);
assert.strictEqual(RC.isQuick(9020), true);
assert.strictEqual(RC.seats(5, 3), 4);
assert.strictEqual(RC.seats(9020, 3), 4);
assert.ok(RC.ruleTip(5, 5).includes('2~4'));
assert.ok(RC.ruleTip(9020, 5).includes('机器人'));
assert.deepStrictEqual(RC.officialIds(5), [5, 9020]);

// 2. 校验前端 NiuniuRule 牌型识别与 3+2 凑十分牌顺序
const Rule = global.NiuniuRule;
assert.ok(Rule, 'NiuniuRule 应挂载在 window 上');

// 牛牛：103(3) + 207(7) + 310(10) = 20 牛身，413(K) + 112(Q) = 20 牛头
const niuNiu = Rule.evaluate([413, 103, 112, 207, 310]);
assert.strictEqual(niuNiu.name, '牛牛');
assert.strictEqual(niuNiu.multiplier, 4);
assert.strictEqual(niuNiu.hasGroupSplit, true);
assert.strictEqual(niuNiu.orderedCards.length, 5);
const bodySum = niuNiu.orderedCards.slice(0, 3)
    .reduce((acc, cid) => acc + Rule.getNiuPoint(cid), 0);
assert.strictEqual(bodySum % 10, 0, '前3张牛身点数之和必须为10的倍数');

// 五小牛
const fiveSmall = Rule.evaluate([114, 214, 115, 215, 103]);
assert.strictEqual(fiveSmall.name, '五小牛');
assert.strictEqual(fiveSmall.multiplier, 8);

// 炸弹牛：4 张同点排在左侧
const bomb = Rule.evaluate([413, 108, 208, 308, 408]);
assert.strictEqual(bomb.name, '炸弹牛');
assert.strictEqual(bomb.multiplier, 7);
assert.deepStrictEqual(bomb.orderedCards.slice(0, 4), [108, 208, 308, 408]);
assert.strictEqual(bomb.orderedCards[4], 413);

// 3. 校验 ReplayModel 对服务端 PokerReplayRecorder 真实牛牛回放格式的解析推演
const sampleReplay = [
    '=== 回放文件 ===',
    '桌号: 902001',
    '玩法: 牛牛',
    '总局数: 4',
    '当前局: 1',
    '=== 玩家 ===',
    '座0: userId=1001, nick=Tester',
    '座1: userId=-100001, nick=BotA',
    '=== 初始发牌 ===',
    '座0: [110,212,103,207]',
    '座1: [210,312,203,308]',
    '[1][12:00:00.010] 回放状态 已发牌·进行中',
    '[2][12:00:01.100] 座0 抢庄 ×2',
    '[3][12:00:01.200] 座1 不抢',
    '[4][12:00:01.300] 定庄 座0 (×2)',
    '[5][12:00:02.100] 座1 下注 ×3',
    '[6][12:00:02.200] 座0 补发第5张 413',
    '[7][12:00:02.210] 座1 补发第5张 414',
    '[8][12:00:03.100] 座0 亮牌 [110, 212, 103, 207, 413] 牛牛',
    '[9][12:00:03.200] 座1 亮牌 [210, 312, 203, 308, 414] 牛九',
    '=== 结算 ===',
    '赢家: 座0',
    '结算值: 2',
    '类型: 牛牛',
    '座0: +24',
    '座1: -24'
].join('\n');

const model = global.ReplayModel.parse(sampleReplay, '902001_1', '牛牛');
assert.strictEqual(model.type, '牛牛');
assert.strictEqual(model.snapshots[0].hands['0'].length, 4, '初始快照应为4张明牌');
const finalSnap = model.snapshots[model.snapshots.length - 1];
assert.strictEqual(finalSnap.landlordSeat, 0, '定庄后应记录庄家座位0');
assert.strictEqual(finalSnap.hands['0'].length, 5, '补发第5张后座0手牌应增至5张');
assert.strictEqual(finalSnap.hands['1'].length, 5, '补发第5张后座1手牌应增至5张');
assert.strictEqual(finalSnap.played['0'].length, 5, '亮牌后应解析含空格分隔的5张摊牌数组');
assert.strictEqual(finalSnap.bids['0'], '牛牛', '亮牌后应记录座0牛型标签');
assert.strictEqual(finalSnap.bids['1'], '牛九', '亮牌后应记录座1牛型标签');

// 4. 校验断线重连乱序（ackOp / notState 早于 enterTable 座位列表到达）时阶段气泡与庄家按钮兜底恢复
const vm = require('vm');
const fs = require('fs');
const path = require('path');
const bidHints = {};
const sandbox = {
    window: {},
    userId: 1001,
    tableId: 500001,
    TABLE_STATE_DIS: 12,
    NiuniuRule: Rule,
    gameWs: {},
    gameState: {
        seatNum: 4, myCards: [], players: [], myPosition: -1, opponentCounts: {},
        landlordId: 0, robMultiplier: 1, baseScore: 1,
        robMultipliers: {}, betMultipliers: {}, showedPlayers: new Set(),
        autoNextRound: false, currentState: 1, myReady: false, opPending: false
    },
    document: {
        getElementById: () => ({ textContent: '', innerHTML: '', style: {}, appendChild: () => {} }),
        querySelectorAll: () => [],
        createElement: () => ({ className: '', textContent: '', style: {} })
    },
    GameTable: { renderRoundInfo: () => {}, noteRoundStarted: () => {}, noteRoundCompleted: () => {} },
    renderOpponentHands: () => {},
    renderMyCards: () => {},
    clearAllPlayedAreas: () => { Object.keys(bidHints).forEach((k) => delete bidHints[k]); },
    showBidHint: (rid, text) => { bidHints[rid] = text; },
    showOperationChoices: (choices) => { sandbox.lastChoices = choices; },
    hideActions: () => { sandbox.lastChoices = null; },
    stopOperationCountdown: () => {},
    startOperationCountdown: () => {},
    closeSettle: () => {},
    showCenterMsg: () => {},
    findPlayerName: (rid) => 'P' + rid,
    snapshotOperationWait: () => 5
};
vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, '../../main/resources/static/pages/games/niuniu/niuniu-op.js'), 'utf8'),
    sandbox
);
// 模拟服务端 syncGameState 顺序：先推 ackOp（1001 当庄，2002 下 3 倍）与 notState(6)，后推 enterTable 玩家列表
sandbox.handleAckOp({ choice: 2, opFrom: 1001, robMultiplier: 2, baseScore: 1 });
sandbox.handleAckOp({ choice: 27, opFrom: 2002 });
sandbox.handleNotState({ state: 6, wait: 10, currentRound: 1, totalRounds: 4 });
assert.strictEqual(sandbox.lastChoices, null, '庄家重连进入闲家加倍阶段不得弹出下注按钮');
assert.strictEqual(bidHints[2002], undefined, 'enterTable 尚未到达时座位为空，暂不绘制气泡');
sandbox.updatePlayers([{ roleId: 1001, position: 0 }, { roleId: 2002, position: 1 }]);
assert.strictEqual(bidHints[2002], '下3倍', 'enterTable 到达后必须自动补绘闲家已下注气泡');

console.log('PASS: 牛牛房间配置、3+2 牌型分牌、重连乱序恢复与回放推演单元测试');
