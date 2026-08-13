const fs=require('fs');const vm=require('vm');const assert=require('assert');const path=require('path');
function eventTarget(){const handlers={};return{handlers,addEventListener(t,f){handlers[t]=f;},removeEventListener(t,f){if(handlers[t]===f)delete handlers[t];}};}
const window=eventTarget();window.window=window;
const pad=eventTarget();
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/pages/mini/tank/tank-input.js'),'utf8'),{window});
const input=window.TankInput.create(pad);
let prevented=false;
window.handlers.keydown({keyCode:39,preventDefault(){prevented=true;}});
assert.strictEqual(input.keys[39],true);assert.strictEqual(prevented,true);
pad.handlers.pointerdown({preventDefault(){},target:{closest(){return{dataset:{dir:'0'}};}}});
assert.strictEqual(input.keys[38],true);assert.strictEqual(input.keys[39],false);
pad.handlers.pointerdown({preventDefault(){},target:{closest(){return{dataset:{fire:'1'}};}}});
assert.strictEqual(input.keys[32],true);
pad.handlers.pointercancel();assert.strictEqual(input.keys[32],false);assert.strictEqual(input.keys[38],false);
input.destroy();assert.strictEqual(window.handlers.keydown,undefined);assert.strictEqual(pad.handlers.pointerdown,undefined);
console.log('tank-input tests passed');
