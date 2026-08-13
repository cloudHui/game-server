const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const common = {PLAY:{cls:'btn-play',text:'出牌'}, PASS:{cls:'btn-pass',text:'过'}};
const viewSandbox = {
    window:{PokerCommonChoice:common}, gameState:{landlordId:0},
    pokerRankValue(){ return 0; }
};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/pages/games/doudizhu/doudizhu-view.js'),'utf8'), viewSandbox);
assert.strictEqual(viewSandbox.window.pokerOpChoiceMap[0], common.PASS);
assert.strictEqual(viewSandbox.window.pokerOpChoiceMap[0].text, '过');

let played = [];
let rendered = 0;
const opSandbox = {
    gameState:{myPosition:0,myCards:[315],selectedCards:new Set(),opPending:false,lastChoices:[]},
    highlightActivePlayer(){}, clearAllPlayedAreas(){}, clearPassHints(){}, hideActions(){},
    renderMyCards(){ rendered++; }, showOperationChoices(){ throw new Error('最后一张不应再显示操作栏'); },
    sendWsMessage(type,payload){ assert.strictEqual(type,'op'); played.push(payload.choice); },
    showCenterMsg(){}, setTimeout(fn){ fn(); },
    GameTable:{}, TABLE_STATE_DIS:99
};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/pages/games/doudizhu/doudizhu-op.js'),'utf8'), opSandbox);
opSandbox.handleNotOp({opSeat:0,wait:8,choice:[{choice:6},{choice:0}]});
assert.deepStrictEqual(played,[6],'最后一张必须自动选择并出牌');
assert.strictEqual(rendered,1);

const tractorSandbox = {window:{PokerCommonChoice:common},gameState:{}};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/pages/games/tractor/tractor-view.js'),'utf8'), tractorSandbox);
[0,3,4].forEach(choice => assert.strictEqual(tractorSandbox.window.pokerOpChoiceMap[choice],common.PASS));

console.log('doudizhu operation tests passed');
