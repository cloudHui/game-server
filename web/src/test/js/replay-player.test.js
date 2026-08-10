const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const sandbox = {window: {}};
['../../main/resources/static/shared/replay-model.js',
    '../../main/resources/static/pages/admin/replay-player.js'].forEach(file => {
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, file), 'utf8'), sandbox);
});

const replay = [
    '座0: userId=-100001, nick=RobotAlpha',
    '座1: userId=-100002, nick=RobotBeta',
    '座0: [11,12,12,12,21]',
    '座1: [11,11,31,32,33]',
    '[1][10:00:00.001] 座0 出牌 11',
    '[2][10:00:00.002] 座1 碰 11 (座0出)',
    '[3][10:00:00.003] 下一操作位 座1',
    '[4][10:00:00.004] 当前最大方 座0',
    '[5][10:00:00.005] 座1 玩家选择 出牌 [31]',
    '[6][10:00:00.006] 座1 出牌 [31]'
].join('\n');
const state = sandbox.window.ReplayPlayer._inspect(replay, '荆门麻将', 5);
assert.deepStrictEqual(Array.from(state.hands[0]), [12, 12, 12, 21]);
assert.deepStrictEqual(Array.from(state.hands[1]), [32, 33]);
assert.deepStrictEqual(Array.from(state.exposed[1]), [11, 11, 11]);
assert.strictEqual(state.nextSeat, 1);
assert.strictEqual(state.bestSeat, 0);

const poker = [
    '座0: [101,102,103]',
    '[1] 座0 获得底牌 [201,202,203]',
    '[2] 座0 扣底 [102,202]',
    '[3] 下一操作位 座2',
    '[4] 本轮最大方 座3'
].join('\n');
const pokerState = sandbox.window.ReplayPlayer._inspect(poker, '拖拉机', 3);
assert.deepStrictEqual(Array.from(pokerState.hands[0]), [101, 201, 103, 203]);
assert.strictEqual(pokerState.nextSeat, 2);
assert.strictEqual(pokerState.bestSeat, 3);

const pdk = '座0: [211,313,406,312,106,311,403,315,105,207,208,104,405,109,408,205]';
const pdkState = sandbox.window.ReplayPlayer._inspect(pdk, '跑得快', -1);
assert.deepStrictEqual(Array.from(pdkState.hands[0]),
    [403,104,105,205,405,106,406,207,208,408,109,211,311,312,313,315]);

console.log('replay-player tests passed');
