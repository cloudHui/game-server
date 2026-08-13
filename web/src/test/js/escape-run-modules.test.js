const fs=require('fs');const path=require('path');const assert=require('assert');
const root=path.resolve(__dirname,'../../main/resources/static/pages/mini/escape-run/src');
const visited=new Set();
function walk(file){
    file=path.resolve(file);if(visited.has(file))return;visited.add(file);
    const source=fs.readFileSync(file,'utf8');
    for(const match of source.matchAll(/from\s+["']([^"']+)["']/g)){
        if(!match[1].startsWith('.'))continue;
        const target=path.resolve(path.dirname(file),match[1]);
        assert(fs.existsSync(target),`缺少模块依赖：${file} -> ${target}`);walk(target);
    }
}
walk(path.join(root,'main.js'));
assert(visited.size>20,'应遍历到完整的小汽车模块依赖图');
const runner=fs.readFileSync(path.join(root,'game/runner.js'),'utf8');
assert(runner.includes('installRunnerGates(Runner)'));assert(runner.includes('installRunnerRendering(Runner)'));
assert(runner.split('\n').length<300,'Runner 主流程不应重新膨胀为大类');
const visibleFiles=['game/menu.js','game/onboarding.js','game/map.js','game/garage.js','game/goals.js','game/results.js','game/trophies.js','game/parentzone.js'];
const forbidden=[/text:\s*["'`]Play Again/,/text:\s*["'`]Adventure Map/,/text:\s*["'`]Trophy Room/,
    /text:\s*["'`]For Grown-ups/,/["'`]How to play["'`]/,/text:\s*["'`]Garage/,
    /["'`]New road unlocked![/"'`]/,/["'`]Updating…[/"'`]/];
for(const rel of visibleFiles){const source=fs.readFileSync(path.join(root,rel),'utf8');for(const pattern of forbidden)assert(!pattern.test(source),`${rel} 仍含英文界面文案：${pattern}`);}
console.log(`escape-run module graph tests passed (${visited.size} modules)`);
