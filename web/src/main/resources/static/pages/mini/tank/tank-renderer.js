(function (w) {
    'use strict';
    var terrain, foreground, cachedMap, cachedVersion = -1;
    var PICKUP_ICONS = {life:'❤',shield:'◉',freeze:'❄',rapid:'⚡',armor:'▣',grenade:'✹',pierce:'➤',spread:'三',speed:'»'};

    function layer(width, height) {
        var canvas = document.createElement('canvas');
        canvas.width = width; canvas.height = height;
        return canvas;
    }

    function rebuild(state) {
        var width = state.canvas.width, height = state.canvas.height;
        if (!terrain || terrain.width !== width || terrain.height !== height) {
            terrain = layer(width,height); foreground = layer(width,height);
        }
        var ground = terrain.getContext('2d'), front = foreground.getContext('2d');
        ground.clearRect(0,0,width,height); front.clearRect(0,0,width,height);
        for (var row=0;row<state.rows;row++) for(var col=0;col<state.cols;col++) {
            var value=state.walls[row][col], x=col*state.tile, y=row*state.tile;
            if (!value) continue;
            if (value === 4) {
                front.fillStyle='rgba(46,125,50,.78)';
                front.fillRect(x+1,y+1,state.tile-2,state.tile-2);
                continue;
            }
            ground.fillStyle=value===2?'#78909c':value===3?'#1976d2':value===6?'#b3e5fc':value===5?'#a1887f':'#8d6e63';
            ground.fillRect(x+1,y+1,state.tile-2,state.tile-2);
            if(value===1||value===5){ground.strokeStyle='rgba(0,0,0,.25)';ground.strokeRect(x+1,y+1,state.tile-2,state.tile-2);}
            if(value===3){ground.strokeStyle='rgba(255,255,255,.45)';ground.beginPath();ground.moveTo(x+5,y+13);ground.lineTo(x+35,y+13);ground.stroke();}
        }
        cachedMap=state.walls; cachedVersion=state.mapVersion;
    }

    function tank(ctx, entity) {
        ctx.save(); ctx.translate(entity.x,entity.y); ctx.rotate(entity.dir*Math.PI/2);
        if(!entity.enemy&&entity.shield>0&&Math.floor(entity.shield*8)%2===0)ctx.globalAlpha=.48;
        ctx.fillStyle=entity.enemy?'#c62828':'#f9a825';ctx.fillRect(-14,-14,28,28);
        ctx.fillStyle=entity.enemy?'#ff8a80':'#fff59d';ctx.fillRect(-4,-22,8,16);
        ctx.fillStyle='#333';ctx.fillRect(-16,-10,6,20);ctx.fillRect(10,-10,6,20);
        if(entity.carrier){ctx.strokeStyle='#ffeb3b';ctx.lineWidth=3;ctx.strokeRect(-17,-17,34,34);}
        if(!entity.enemy&&entity.shield>0){ctx.strokeStyle='rgba(128,216,255,.9)';ctx.lineWidth=3;ctx.beginPath();ctx.arc(0,0,20,0,Math.PI*2);ctx.stroke();}
        ctx.restore();
    }

    function draw(ctx, state) {
        var width=state.canvas.width,height=state.canvas.height;
        ctx.clearRect(0,0,width,height);ctx.fillStyle='#243528';ctx.fillRect(0,0,width,height);
        if(!state.walls)return;
        if(cachedMap!==state.walls||cachedVersion!==state.mapVersion)rebuild(state);
        ctx.drawImage(terrain,0,0);
        if(state.player)tank(ctx,state.player);
        state.enemies.forEach(function(e){tank(ctx,e);});
        state.bullets.forEach(function(b){ctx.beginPath();ctx.fillStyle=b.enemy?'#ff5252':b.pierce?'#80d8ff':'#ffee58';ctx.arc(b.x,b.y,b.pierce?4.5:3.5,0,Math.PI*2);ctx.fill();});
        if(state.base){ctx.fillStyle=state.baseShieldTime>0?'#80d8ff':'#ffd54f';ctx.fillRect(state.base.x-15,state.base.y-15,30,30);ctx.fillStyle='#4e342e';ctx.font='20px sans-serif';ctx.textAlign='center';ctx.fillText('★',state.base.x,state.base.y+7);ctx.fillStyle='#311b92';ctx.fillRect(state.base.x-18,state.base.y-23,36,5);ctx.fillStyle='#66bb6a';ctx.fillRect(state.base.x-18,state.base.y-23,36*(state.base.hp/state.base.maxHp),5);if(state.baseArmorTime>0){ctx.fillStyle='#e1f5fe';ctx.font='bold 11px sans-serif';ctx.fillText('钢墙 '+Math.ceil(state.baseArmorTime)+'秒',state.base.x,state.base.y+30);}}
        state.pickups.forEach(function(p){ctx.fillStyle='#fff176';ctx.beginPath();ctx.arc(p.x,p.y,13,0,Math.PI*2);ctx.fill();ctx.fillStyle='#b71c1c';ctx.font='bold 17px sans-serif';ctx.textAlign='center';ctx.fillText(PICKUP_ICONS[p.type],p.x,p.y+6);});
        ctx.drawImage(foreground,0,0);
    }
    w.TankRenderer={draw:draw};
})(window);
