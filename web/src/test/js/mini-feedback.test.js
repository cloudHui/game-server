const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const path = require('path');

let spoken = [];
let scheduled = null;
const listeners = {};
const body = {last:null, appendChild(node) { node.isConnected = true; this.last = node; }};
const document = {
    hidden: false,
    body,
    createElement() { return {style:{}, setAttribute(){}, isConnected:false, textContent:''}; },
    addEventListener(type, fn) { listeners[type] = fn; }
};
function Utterance(text) { this.text = text; }
const window = {
    document,
    SpeechSynthesisUtterance: Utterance,
    speechSynthesis: {cancel(){}, speak(u){ spoken.push(u.text); }},
    addEventListener(type, fn) { listeners[type] = fn; }
};
function fakeSetTimeout(fn, delay) { scheduled = {fn, delay}; return 1; }
function fakeClearTimeout() { scheduled = null; }
const sandbox = {window, document, SpeechSynthesisUtterance:Utterance, Date,
    setTimeout:fakeSetTimeout, clearTimeout:fakeClearTimeout};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/shared/mini-feedback.js'), 'utf8'), sandbox);

window.MiniFeedback.readGoal('选择最大的数字');
window.MiniFeedback.readGoal('选择最大的数字');
assert.deepStrictEqual(spoken, ['选择最大的数字'], '短时间相同语音应去重');
window.MiniFeedback.praise('很棒！');
assert.strictEqual(spoken[1], '很棒！');
assert.strictEqual(body.last.textContent, '很棒！');
assert.strictEqual(body.last.style.opacity, '1');
assert.strictEqual(scheduled.delay, 1000, '成功提示必须显示整整 1 秒再渐隐');
scheduled.fn();
assert.strictEqual(body.last.style.opacity, '0');
assert.strictEqual(typeof window.MiniFeedback.stop, 'function');
window.MiniFeedback.setEnabled(false);
window.MiniFeedback.readGoal('不应朗读');
assert.strictEqual(spoken.length, 2, '关闭朗读后不应继续加入语音');
console.log('mini-feedback tests passed');
