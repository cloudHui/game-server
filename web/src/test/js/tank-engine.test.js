const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const path = require('path');
const asset = relative => path.resolve(__dirname, '../../main/resources/static', relative);

const sandbox = {window: {}};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(asset('pages/mini/tank/tank-engine.js'), 'utf8'), sandbox);
vm.runInContext(fs.readFileSync(asset('pages/mini/tank/tank-map.js'), 'utf8'), sandbox);
vm.runInContext(fs.readFileSync(asset('pages/mini/tank/tank-config.js'), 'utf8'), sandbox);
const engine = sandbox.window.TankEngine;
assert(Object.isFrozen(sandbox.window.TankConfig), '坦克平衡配置应只读');

function emptyMap() {
    return Array.from({length: 5}, (_, row) => Array.from({length: 5}, (_, col) =>
        row === 0 || col === 0 || row === 4 || col === 4 ? 2 : 0));
}

const map = emptyMap();
map[2][2] = 1;
map[1][2] = 4;
const opts = {map, tile: 40, rows: 5, cols: 5, radius: 12, enemies: [], player: null};

assert.strictEqual(engine.terrainBlocks(1), true, '砖墙应阻挡坦克');
assert.strictEqual(engine.terrainBlocks(3), true, '水面应阻挡坦克');
assert.strictEqual(engine.terrainBlocks(4), false, '树林应允许坦克通过');
assert.strictEqual(engine.terrainBlocks(6), false, '冰面应允许坦克通过');
assert.strictEqual(engine.collides(opts, 100, 100, null), true, '砖块中心应发生碰撞');
assert.strictEqual(engine.collides(opts, 100, 60, null), false, '树林中心应允许通过');

const moving = {x: 60, y: 60, dir: 1, speed: 100};
assert.strictEqual(engine.move(opts, moving, 0.1, [[0,-1],[1,0],[0,1],[-1,0]]), true);
assert(moving.x > 60, '空地移动应更新坐标');
const dirs=[[0,-1],[1,0],[0,1],[-1,0]];
dirs.forEach((_,dir)=>{
    const entity={x:140,y:140,dir,speed:40};
    assert.strictEqual(engine.move({...opts,map:emptyMap()},entity,0.1,dirs),true,`方向 ${dir} 应可在空地移动`);
});
const cornerMap=emptyMap();cornerMap[2][2]=1;
const cornerTank={x:60,y:92,dir:1,speed:100};
assert.strictEqual(engine.move({...opts,map:cornerMap},cornerTank,0.1,dirs),true,'轻微擦到砖角时应执行贴边辅助');
assert(cornerTank.y>92,'贴边辅助应把坦克吸附向通道中心');

const occupied = {x: 60, y: 60};
const safe = engine.safeSpawn([[60,60],[140,60]], {...opts, enemies:[occupied]});
assert.deepStrictEqual(Array.from(safe), [140,60], '复活应避开被敌人占用的位置');
assert.strictEqual(engine.findSafeSpawn([[60,60]],{...opts,enemies:[occupied]}),null,'没有安全位置时不应强行生成拾取物');

const levelMap = sandbox.window.TankMap.create(1, 13, 13);
assert.strictEqual(levelMap[0][0], 2, '地图边界应为钢墙');
assert.strictEqual(levelMap[4][3], 3, '地图应包含水面');
assert.strictEqual(levelMap[3][8], 4, '地图应包含树林');
assert.strictEqual(levelMap[6][2], 6, '地图应包含冰面');
assert.strictEqual(levelMap[11][6], 0, '基地核心位置应保持空地');
for (let level=1;level<=20;level++) {
    const generated=sandbox.window.TankMap.create(level,13,13);
    assert.strictEqual(sandbox.window.TankMap.hasRoute(generated,[9,6],[[1,1],[1,6],[1,11]]),true,
        `第 ${level} 关应至少连通一个敌人出生区`);
}

console.log('tank-engine tests passed');
