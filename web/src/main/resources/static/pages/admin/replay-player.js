(function(w){
    'use strict';
    var timer=null, liveTimer=null, liveUrl='', model=null, viewSeat=0, logTimer=null;
    function esc(s){return String(s||'').replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
    function isMj(){return /麻将|卡五星/.test(model.type)}
    function face(id){var box=document.createElement('span');if(isMj()&&w.MahjongTile)return w.MahjongTile.createTileEl(id,{small:true});if(w.PokerCard)return w.PokerCard.createCardFace(id);box.textContent=id;return box}
    function render(){
        var current=model.pos>=0?model.events[model.pos]:null, actor=current&&current.text.match(/^座(\d+)/);
        document.querySelectorAll('#replayTable .replay-seat').forEach(function(seat){seat.hidden=true;seat.removeAttribute('data-seat');seat.querySelector('.replay-hand').innerHTML='';seat.querySelector('.replay-exposed').innerHTML=''});
        for(var s=0;s<4;s++){
            if(s>=model.seatCount)continue;
            var seat=ReplayTableView.seatElement(s,viewSeat,model.seatCount);seat.hidden=false;seat.setAttribute('data-seat',s);seat.classList.toggle('active',!!actor&&+actor[1]===s);
            seat.classList.toggle('next',model.nextSeat===s);seat.classList.toggle('best',model.bestSeat===s);
            seat.querySelector('.name').textContent='座'+s+' '+(model.players[s]||'');
            var hand=seat.querySelector('.replay-hand');(model.hands[s]||[]).forEach(function(id){hand.appendChild(face(id))});
            var shown=seat.querySelector('.replay-exposed');(model.exposed[s]||[]).forEach(function(id){shown.appendChild(face(id))});
        }
        document.getElementById('replayEvent').textContent=current?('#'+current.index+' '+current.time+' '+current.text):'初始发牌';
        var decision=document.getElementById('replayDecision');if(decision){var context='';if(current){
            if(/收到选项/.test(current.text))context='客户端按钮：'+current.text.replace(/^座\d+ 收到选项\s*/, '');
            else if(/(?:机器人|玩家)选择/.test(current.text))context='实际操作：'+current.text;
            else if(/(?:当前|本轮)最大方/.test(current.text))context='规则判断：'+current.text;
            else if(/得分|结算|倍数|牌型|合法|抓分|抠底/.test(current.text))context='计分/牌型：'+current.text;
        }decision.textContent=context}
        var eventCards=document.getElementById('replayEventCards');eventCards.innerHTML='';if(current){var groups=current.text.match(/\[[\d,]+\]|(?:出牌|摸牌|胡) \d+/g)||[];if(groups.length){var nums=groups[groups.length-1].match(/\d+/g)||[];nums.map(Number).forEach(function(id){eventCards.appendChild(face(id))})}}
        var discards=document.getElementById('replayDiscards');discards.innerHTML='';model.discards.forEach(function(item){var card=face(item.id);card.setAttribute('data-seat',item.seat);discards.appendChild(card)});
        document.getElementById('replayRange').value=model.pos+1;document.getElementById('replayStep').textContent=(model.pos+1)+' / '+model.events.length;
        document.querySelectorAll('#replayLog div').forEach(function(x,i){x.classList.toggle('current',i===model.pos);if(i===model.pos)x.scrollIntoView({block:'nearest'})});
    }
    function seek(pos){w.ReplayModel.rebuild(model,pos);render()}
    function toggle(){if(!model)return;if(timer){clearInterval(timer);timer=null;document.getElementById('replayPlay').textContent='播放';return}document.getElementById('replayPlay').textContent='暂停';timer=setInterval(function(){if(model.pos>=model.events.length-1){toggle();return}seek(model.pos+1)},700/Number(document.getElementById('replaySpeed').value))}
    function open(data, url){
        if(timer){clearInterval(timer);timer=null}model=w.ReplayModel.parse(data.content,data.replayCode||data.date+'/'+data.name,data.gameType);
        var root=document.getElementById('replayPlayer');root.classList.add('active');document.getElementById('replayWaiting').hidden=true;document.getElementById('replayRange').max=model.events.length;document.getElementById('replayCodeLabel').textContent=model.code;
        document.getElementById('replayTable').classList.toggle('replay-mahjong',isMj());
        var selector=document.getElementById('replayViewSeat');selector.innerHTML='';for(var s=0;s<model.seatCount;s++){var option=document.createElement('option');option.value=s;option.textContent='座'+s+' '+(model.players[s]||'');selector.appendChild(option)}viewSeat=Math.min(viewSeat,model.seatCount-1);selector.value=viewSeat;
        document.getElementById('replayRaw').textContent=model.content;document.getElementById('replayLog').innerHTML=model.events.map(function(e){return '<div>#'+e.index+' '+esc(e.time)+' '+esc(e.text)+'</div>'}).join('');seek(-1);armLive(url,data.status);
    }
    function armLive(url,status){
        if(!url||status==='已结算'){if(liveTimer){clearInterval(liveTimer);liveTimer=null}liveUrl='';return}
        if(liveTimer&&liveUrl===url)return;if(liveTimer)clearInterval(liveTimer);liveUrl=url;
        liveTimer=setInterval(function(){fetch(liveUrl).then(function(r){return r.json()}).then(function(d){
            if(d.code!==0||!model||d.content===model.content)return;
            var oldPos=model.pos,wasEnd=oldPos>=model.events.length-1,wasPlaying=!!timer;open(d,liveUrl);seek(wasEnd?model.events.length-1:Math.min(oldPos,model.events.length-1));document.getElementById('replayPlay').textContent='播放';if(wasPlaying)toggle();
        }).catch(function(){})},2000);
    }
    function copyCode(){if(model)navigator.clipboard.writeText(model.code)}
    function latest(){if(model)seek(model.events.length-1)}
    function changeViewSeat(seat){if(!model)return;viewSeat=Math.max(0,Math.min(seat,model.seatCount-1));render()}
    function close(){if(timer){clearInterval(timer);timer=null}if(liveTimer){clearInterval(liveTimer);liveTimer=null}closeLog();liveUrl='';model=null;var card=document.getElementById('replayDetailCard');if(card){card.style.display='none';card.classList.remove('replay-overlay')}var root=document.getElementById('replayPlayer');if(root)root.classList.remove('active');document.body.classList.remove('replay-watching');document.dispatchEvent(new CustomEvent('replay-player-close'));if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen().catch(function(){});if(screen.orientation&&screen.orientation.unlock)try{screen.orientation.unlock()}catch(e){}}
    function requestLandscape(){var el=document.documentElement;if(el.requestFullscreen&&!document.fullscreenElement)el.requestFullscreen().catch(function(){});if(screen.orientation&&screen.orientation.lock)screen.orientation.lock('landscape').catch(function(){})}
    function theater(){var card=document.getElementById('replayDetailCard');if(card){card.style.display='block';card.classList.add('replay-overlay')}document.body.classList.add('replay-watching');requestLandscape()}
    function armLog(){if(logTimer)clearTimeout(logTimer);logTimer=setTimeout(closeLog,2000)}
    function openLog(){var mask=document.getElementById('replayLogMask');if(mask)mask.classList.add('show');armLog()}
    function closeLog(){var mask=document.getElementById('replayLogMask');if(mask)mask.classList.remove('show');if(logTimer){clearTimeout(logTimer);logTimer=null}}
    function toggleLog(){var mask=document.getElementById('replayLogMask');if(mask&&mask.classList.contains('show'))closeLog();else openLog()}
    function waiting(tableId){if(timer){clearInterval(timer);timer=null}if(liveTimer){clearInterval(liveTimer);liveTimer=null}model=null;theater();var root=document.getElementById('replayPlayer'),wait=document.getElementById('replayWaiting');root.classList.add('active');wait.hidden=false;document.getElementById('replayPlay').textContent='播放';document.getElementById('replayCodeLabel').textContent='';document.getElementById('replayLog').innerHTML='';document.getElementById('replayRaw').textContent='';document.getElementById('replayWaitingText').textContent='桌 '+tableId+' 已创建，等待首个回放事件…'}
    w.ReplayPlayer={open:function(data,url){open(data,url);theater()},waiting:waiting,toggle:toggle,seek:function(n){if(model)seek(n)},move:function(n){if(model)seek(model.pos+n)},latest:latest,viewSeat:changeViewSeat,close:close,copyCode:copyCode,toggleLog:toggleLog,closeLog:closeLog,_inspect:w.ReplayModel.inspect};
    if(typeof document!=='undefined')document.addEventListener('DOMContentLoaded',function(){var panel=document.querySelector('.replay-log-panel');if(panel){['scroll','touchmove','pointerdown'].forEach(function(name){panel.addEventListener(name,armLog,{passive:true})})}});
    if(typeof document!=='undefined')document.addEventListener('keydown',function(e){if(!document.body.classList.contains('replay-watching'))return;if(e.key==='Escape')close();else if(e.key===' '){e.preventDefault();toggle()}else if(model&&e.key==='ArrowLeft')seek(model.pos-1);else if(model&&e.key==='ArrowRight')seek(model.pos+1);else if(e.key==='End')latest()});
})(window);
