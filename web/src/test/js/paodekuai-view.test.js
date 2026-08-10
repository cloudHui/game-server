const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

let picked = [];
const sandbox = {
    window: {},
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

console.log('paodekuai-view tests passed');
