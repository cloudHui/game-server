const assert = require('assert');
const ArenaView = require('../../main/resources/static/pages/arena/arena-view.js');
const ArenaAudio = require('../../main/resources/static/pages/arena/arena-audio.js');

// 1. 测试 ArenaAudio
assert.equal(ArenaAudio.isMuted(), false);
ArenaAudio.setMuted(true);
assert.equal(ArenaAudio.isMuted(), true);
ArenaAudio.setMuted(false);

// 2. 测试 ArenaView.renderResourcesHtml
const state = {
    liquid: 5000, coins: 2500, fate: 12, stones: 800, beastShards: 15,
    dungeonCleared: 5, beastTowerCleared: 2, equippedSkill: 'pierce',
    tasks: {
        login: { progress: 1, claimed: false },
        dungeon: { progress: 3, claimed: true }
    }
};

const resHtml = ArenaView.renderResourcesHtml(state);
assert(resHtml.includes('灵液 5000'));
assert(resHtml.includes('灵币 2500'));
assert(resHtml.includes('神兽碎片 15'));

// 3. 测试 ArenaView.renderTowerHtml
const towerHtml = ArenaView.renderTowerHtml(state, 1);
assert(towerHtml.includes('通天塔 1 层'));
assert(towerHtml.includes('第 1 ~ 15 层'));

// 4. 测试 ArenaView.renderTasksHtml
const taskCfg = {
    login: ['登录问道', 1, '灵液 200', 10],
    dungeon: ['挑战通天塔三次', 3, '灵液 500', 20]
};
const taskRes = ArenaView.renderTasksHtml(state, taskCfg);
assert(taskRes.html.includes('登录问道'));
assert.equal(taskRes.activity, 20); // 只有 dungeon claimed

// 5. 测试 ArenaView.renderSkillsHtml
const skillsList = [
    { id: 'silence', name: '太虚封魔咒', tag: '🔕 沉默流', desc: 'desc1' },
    { id: 'pierce', name: '九天破甲决', tag: '⚔️ 破甲流', desc: 'desc2' }
];
const skillsHtml = ArenaView.renderSkillsHtml('pierce', skillsList);
assert(skillsHtml.includes('active'));
assert(skillsHtml.includes('九天破甲决'));

console.log('PASS: ArenaView 视图模板与 ArenaAudio 引擎单元测试');
