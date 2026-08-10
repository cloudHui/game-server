const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const output = [];
function element(tag) {
    return {tagName: tag.toUpperCase(), dataset: {}, style: {}, classList: {add() {}, remove() {}},
        appendChild() {}, setAttribute() {}, addEventListener() {}, querySelector() { return null; }};
}
const document = {
    currentScript: {src: 'https://game.example.com/secret/shared/app-base.js'}, hidden: false,
    head: {appendChild() {}}, body: {appendChild() {}},
    createElement: element, getElementById() { return null; }, querySelector() { return null; }, addEventListener() {}
};
const window = {
    location: {origin: 'https://game.example.com', host: 'game.example.com', pathname: '/secret/pages/admin/admin.html'},
    navigator: {onLine: true}, document,
    console: {log(...a) { output.push(a); }, info(...a) { output.push(a); }, warn(...a) { output.push(a); }, error(...a) { output.push(a); }, debug(...a) { output.push(a); }},
    addEventListener() {}, fetch() {}, XMLHttpRequest: function () {}, WebSocket: function () {}
};
window.XMLHttpRequest.prototype.send = function () {};
window.WebSocket.prototype.send = function () {};
const sandbox = {window, document, URL, WeakMap, Promise, queueMicrotask, setTimeout, clearTimeout};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/shared/app-base.js'), 'utf8'), sandbox);

const clean = window.AppErrorPrivacy.sanitizeText('请求 https://game.example.com/secret/api/users?sessionId=abc&token=xyz 失败');
assert(!clean.includes('game.example.com'));
assert(!clean.includes('/secret'));
assert(!clean.includes('abc'));
assert(!clean.includes('xyz'));
assert(clean.includes('[已隐藏]'));

window.console.error({url: 'wss://game.example.com/secret/ws?token=abc', sessionId: 'abc'});
assert(!JSON.stringify(output).includes('game.example.com'));
assert(!JSON.stringify(output).includes('abc'));
console.log('app-base privacy tests passed');
