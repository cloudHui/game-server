const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

let picked = [];
const sandbox = {
    window: {PokerCommonChoice: {PLAY:{cls:'btn-play',text:'出牌'},PASS:{cls:'btn-pass',text:'过'}}},
    gameState: {
        myCards: [104, 205, 106, 306, 407, 108],
        lastPlayedCards: [105, 305]
    },
    applyPokerPickedCards(cards) { picked = Array.from(cards); }
};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/pages/games/paodekuai/paodekuai-view.js'), 'utf8'), sandbox);

sandbox.window.pokerSuggestPlay();
assert.deepStrictEqual(picked, [106, 306]);
assert.strictEqual(picked[0] % 100, picked[1] % 100);
assert.strictEqual(sandbox.window.pokerOpChoiceMap[0], sandbox.window.PokerCommonChoice.PASS);
assert.strictEqual(sandbox.window.pokerOpChoiceMap[0].text, '过');

console.log('paodekuai-view tests passed');
