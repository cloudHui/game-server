const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const source = fs.readFileSync(
    path.join(__dirname, '../../main/resources/static/pages/admin/js/arena.js'),
    'utf8'
);

function createContext() {
    const elements = {
        arenaPlayerBody: {
            innerHTML: '',
            querySelectorAll: () => []
        },
        arenaMsg: {textContent: '', className: ''}
    };
    const context = {
        Admin: {
            sessionId: '<REDACTED>',
            get: () => Promise.resolve({
                code: 0,
                players: [{userId: 1, nickname: '测试玩家', username: 'test'}]
            }),
            msg: (id, text, ok) => {
                elements[id].textContent = text || '';
                elements[id].className = ok === true ? 'msg ok' : ok === false ? 'msg err' : 'msg';
            }
        },
        AdminTools: {
            byId: id => elements[id],
            escapeHtml: value => String(value == null ? '' : value),
            nonNegativeInteger: raw => Number(raw)
        },
        appUrl: path => path,
        document: {getElementById: id => elements[id]},
        arenaPlayerBody: elements.arenaPlayerBody,
        arenaMsg: elements.arenaMsg
    };
    context.window = context;
    return context;
}

(async function () {
    const context = createContext();
    vm.createContext(context);
    vm.runInContext(source, context);
    context.loadArenaPlayers();
    await new Promise(resolve => setImmediate(resolve));

    assert.equal(context.arenaMsg.className, 'msg ok');
    assert.ok(!context.arenaMsg.textContent.includes('失败'));
    assert.ok(context.arenaPlayerBody.innerHTML.includes('测试玩家'));
    console.log('PASS: 剑气除魔玩家列表渲染');
})().catch(error => {
    console.error(error.stack || error.message);
    process.exitCode = 1;
});
