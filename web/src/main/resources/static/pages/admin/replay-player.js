(function(w){
    'use strict';
    var timer=null, liveTimer=null, liveUrl='', model=null;
    function esc(s){return String(s||'').replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
    function parse(content, code, gameType){
        var lines=(content||'').split(/\r?\n/), hands={}, events=[], players={};
        lines.forEach(function(line){
            var p=line.match(/^座(\d+): userId=(-?\d+), nick=(.*)$/); if(p){players[p[1]]=p[3];return}
            var h=line.match(/^座(\d+): \[([\d,]*)\]$/); if(h){hands[h[1]]=h[2]?h[2].split(',').map(Number):[];return}
            var e=line.match(/^\[(\d+)\](?:\[([^\]]+)\])? (.*)$/); if(e)events.push({index:+e[1],time:e[2]||'',text:e[3]})
        });
        var seatCount=Math.max(1,Object.keys(players).length,Object.keys(hands).length);
        return {content:content,code:code,type:gameType||'',players:players,seatCount:seatCount,initial:hands,events:events,pos:-1,hands:{},exposed:{},lastDiscard:0,nextSeat:-1,bestSeat:-1};
    }
    function cloneHands(h){var x={};Object.keys(h).forEach(function(k){x[k]=h[k].slice()});return x}
    function applyEvent(e){
        var m=e.text.match(/^座(\d+) (?:超时)?(?:出牌|摸牌) (\[[\d,]*\]|\d+)/); if(!m)return;
        var seat=m[1], ids=m[2][0]==='['?(m[2].slice(1,-1)?m[2].slice(1,-1).split(',').map(Number):[]):[+m[2]];
        model.hands[seat]=model.hands[seat]||[];
        if(e.text.indexOf('摸牌')>=0) model.hands[seat]=model.hands[seat].concat(ids);
        else {ids.forEach(function(id){removeOne(seat,id)});model.lastDiscard=ids.length===1?ids[0]:0}
    }
    function removeOne(seat,id){var i=(model.hands[seat]||[]).indexOf(id);if(i>=0)model.hands[seat].splice(i,1)}
    function applyFullEvent(e){
        applyEvent(e);var x=e.text.match(/^座(\d+) (吃|碰|明杠|暗杠|补杠) (\[[\d,]*\]|\d+)/);if(x){
            var seat=x[1],kind=x[2],ids=x[3][0]==='['?x[3].slice(1,-1).split(',').filter(Boolean).map(Number):[+x[3]];model.exposed[seat]=model.exposed[seat]||[];
            if(kind==='吃'){var skipped=false;ids.forEach(function(id){if(!skipped&&id===model.lastDiscard)skipped=true;else removeOne(seat,id)});model.exposed[seat]=model.exposed[seat].concat(ids)}
            else {var n=kind==='碰'?2:(kind==='明杠'?3:(kind==='暗杠'?4:1));for(var i=0;i<n;i++)removeOne(seat,ids[0]);for(var j=0;j<(kind==='碰'?3:4);j++)model.exposed[seat].push(ids[0])}
        }
        var b=e.text.match(/^座(\d+) 获得底牌 \[([\d,]+)\]/);if(b)model.hands[b[1]]=(model.hands[b[1]]||[]).concat(b[2].split(',').map(Number));
        var bury=e.text.match(/^座(\d+) 扣底 \[([\d,]+)\]/);if(bury)bury[2].split(',').map(Number).forEach(function(id){removeOne(bury[1],id)});
        var next=e.text.match(/^下一操作位 座(\d+)/);if(next)model.nextSeat=+next[1];
        var best=e.text.match(/^(?:当前|本轮)最大方 座(\d+)/);if(best)model.bestSeat=+best[1];
    }
    function isMj(){return /麻将|卡五星/.test(model.type)}
    function face(id){var box=document.createElement('span');if(isMj()&&w.MahjongTile)return w.MahjongTile.createTileEl(id,{small:true});if(w.PokerCard)return w.PokerCard.createCardFace(id);box.textContent=id;return box}
    function render(){
        var current=model.pos>=0?model.events[model.pos]:null, actor=current&&current.text.match(/^座(\d+)/);
        for(var s=0;s<4;s++){
            var seat=document.querySelector('#replayPlayer .s'+s);seat.style.display=s<model.seatCount?'block':'none';seat.classList.toggle('active',!!actor&&+actor[1]===s);
            seat.classList.toggle('next',model.nextSeat===s);seat.classList.toggle('best',model.bestSeat===s);
            seat.querySelector('.replay-seat-name').textContent='座'+s+' '+(model.players[s]||'');
            var hand=seat.querySelector('.replay-hand');hand.innerHTML='';(model.hands[s]||[]).forEach(function(id){hand.appendChild(face(id))});
            if((model.exposed[s]||[]).length){var shown=document.createElement('div');shown.className='replay-exposed';model.exposed[s].forEach(function(id){shown.appendChild(face(id))});hand.appendChild(shown)}
        }
        document.getElementById('replayEvent').textContent=current?('#'+current.index+' '+current.time+' '+current.text):'初始发牌';
        var decision=document.getElementById('replayDecision');if(decision){var context='';if(current){
            if(/收到选项/.test(current.text))context='客户端按钮：'+current.text.replace(/^座\d+ 收到选项\s*/, '');
            else if(/(?:机器人|玩家)选择/.test(current.text))context='实际操作：'+current.text;
            else if(/(?:当前|本轮)最大方/.test(current.text))context='规则判断：'+current.text;
            else if(/得分|结算|倍数|牌型|合法|抓分|抠底/.test(current.text))context='计分/牌型：'+current.text;
        }decision.textContent=context}
        var eventCards=document.getElementById('replayEventCards');eventCards.innerHTML='';if(current){var groups=current.text.match(/\[[\d,]+\]|(?:出牌|摸牌|胡) \d+/g)||[];if(groups.length){var nums=groups[groups.length-1].match(/\d+/g)||[];nums.map(Number).forEach(function(id){eventCards.appendChild(face(id))})}}
        document.getElementById('replayRange').value=model.pos+1;document.getElementById('replayStep').textContent=(model.pos+1)+' / '+model.events.length;
        document.querySelectorAll('#replayLog div').forEach(function(x,i){x.classList.toggle('current',i===model.pos);if(i===model.pos)x.scrollIntoView({block:'nearest'})});
    }
    function rebuild(pos){model.hands=cloneHands(model.initial);model.exposed={};model.lastDiscard=0;model.nextSeat=-1;model.bestSeat=-1;model.pos=Math.max(-1,Math.min(pos,model.events.length-1));for(var i=0;i<=model.pos;i++)applyFullEvent(model.events[i])}
    function seek(pos){rebuild(pos);render()}
    function toggle(){if(timer){clearInterval(timer);timer=null;document.getElementById('replayPlay').textContent='播放';return}document.getElementById('replayPlay').textContent='暂停';timer=setInterval(function(){if(model.pos>=model.events.length-1){toggle();return}seek(model.pos+1)},700/Number(document.getElementById('replaySpeed').value))}
    function open(data, url){
        if(timer){clearInterval(timer);timer=null}model=parse(data.content,data.replayCode||data.date+'/'+data.name,data.gameType);
        var root=document.getElementById('replayPlayer');root.classList.add('active');document.getElementById('replayRange').max=model.events.length;document.getElementById('replayCodeLabel').textContent=model.code;
        document.getElementById('replayRaw').textContent=model.content;document.getElementById('replayLog').innerHTML=model.events.map(function(e){return '<div>#'+e.index+' '+esc(e.time)+' '+esc(e.text)+'</div>'}).join('');seek(-1);armLive(url,data.status);
    }
    function armLive(url,status){
        if(!url||status==='已结算'){if(liveTimer){clearInterval(liveTimer);liveTimer=null}liveUrl='';return}
        if(liveTimer&&liveUrl===url)return;if(liveTimer)clearInterval(liveTimer);liveUrl=url;
        liveTimer=setInterval(function(){fetch(liveUrl).then(function(r){return r.json()}).then(function(d){
            if(d.code!==0||!model||d.content===model.content)return;
            var oldPos=model.pos,wasEnd=oldPos>=model.events.length-1;open(d,liveUrl);seek(wasEnd?model.events.length-1:Math.min(oldPos,model.events.length-1));
        }).catch(function(){})},2000);
    }
    function copyCode(){navigator.clipboard.writeText(model.code)}
    function latest(){if(model)seek(model.events.length-1)}
    function close(){if(timer){clearInterval(timer);timer=null}if(liveTimer){clearInterval(liveTimer);liveTimer=null}liveUrl='';var card=document.getElementById('replayDetailCard');if(card){card.style.display='none';card.classList.remove('replay-overlay')}var root=document.getElementById('replayPlayer');if(root)root.classList.remove('theater');document.body.classList.remove('replay-watching')}
    function theater(){var card=document.getElementById('replayDetailCard'),root=document.getElementById('replayPlayer');if(card){card.style.display='block';card.classList.add('replay-overlay')}if(root)root.classList.add('theater');document.body.classList.add('replay-watching')}
    function inspect(content,gameType,pos){model=parse(content,'test',gameType);rebuild(pos);return {hands:cloneHands(model.hands),exposed:cloneHands(model.exposed),nextSeat:model.nextSeat,bestSeat:model.bestSeat,pos:model.pos}}
    w.ReplayPlayer={open:function(data,url){open(data,url);theater()},toggle:toggle,seek:function(n){seek(n)},move:function(n){seek(model.pos+n)},latest:latest,close:close,copyCode:copyCode,_inspect:inspect};
    if(typeof document!=='undefined')document.addEventListener('keydown',function(e){if(!document.body.classList.contains('replay-watching'))return;if(e.key==='Escape')close();else if(e.key===' '){e.preventDefault();toggle()}else if(e.key==='ArrowLeft')seek(model.pos-1);else if(e.key==='ArrowRight')seek(model.pos+1);else if(e.key==='End')latest()});
})(window);
