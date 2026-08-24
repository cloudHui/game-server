const assert = require('assert');
const BattlePlayer = require('../../main/resources/static/pages/arena/battle-player.js');

const events = [{text: '第一击'}, {text: '第二击'}, {text: '胜负已分'}];
const shown = [];
const scheduled = [];
const player = new BattlePlayer({
    render: event => shown.push(event.text),
    schedule: (fn, delay) => { scheduled.push({fn, delay}); return scheduled.length; },
    cancel: () => {}
});

player.load(events);
player.setSpeed(2);
player.play();
assert.deepEqual(shown, ['第一击']);
assert.equal(scheduled[0].delay, 80);

player.pause();
scheduled[0].fn();
assert.deepEqual(shown, ['第一击']);

player.play();
scheduled[1].fn();
assert.deepEqual(shown, ['第一击', '第二击', '胜负已分']);
assert.equal(player.status().finished, true);

shown.length = 0;
player.replay();
assert.deepEqual(shown, ['第一击']);
player.skip();
assert.deepEqual(shown, ['第一击', '第二击', '胜负已分']);
assert.equal(player.status().finished, true);

console.log('PASS: 战报暂停、倍速、继续、跳过、重播');
