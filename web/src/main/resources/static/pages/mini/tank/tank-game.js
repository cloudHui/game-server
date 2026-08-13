    (function () {
        var user = MiniGames.requireLogin();
        if (!user) return;
        document.getElementById('userDisplay').textContent = user.nickname;
        document.getElementById('backLink').href = appUrl('/pages/mini/index.html');

        var canvas = document.getElementById('game');
        var ctx = canvas.getContext('2d');
        var TILE = 40, COLS = 13, ROWS = 13;
        var DIR = [[0, -1], [1, 0], [0, 1], [-1, 0]]; // U R D L
        var input = TankInput.create(document.getElementById('pad'));
        var keys = input.keys;
        var running = false, over = false;
        var player, enemies, bullets, walls, pickups, base, score, level, lives, lastTs;
        var bulletPool=[];
        var rafId=0;
        var freezeTime=0,baseShieldTime=0,baseArmorTime=0,baseArmorBackup=null,respawnTime=0,respawnSecond=-1,mapVersion=0;
        var BASE_RING = [[11,5],[10,5],[10,6],[10,7],[11,7]];
        var PICKUP_TYPES=['life','shield','freeze','rapid','armor','grenade','pierce','spread','speed'];
        var movementContext={map:null,tile:TILE,rows:ROWS,cols:COLS,radius:12,enemies:null,player:null};

        function setStatus(t) {
            document.getElementById('status').textContent = t;
        }

        function setHud() {
            document.getElementById('scoreHud').textContent =
                '得分 '+score+' · 关卡 '+level+' · 生命 '+lives+(base?' · 基地 '+base.hp+'/'+base.maxHp:'');
            [['buyRepair',TankConfig.costs.repair],['buyLife',TankConfig.costs.life],['buyArmor',TankConfig.costs.armor]].forEach(function(item){
                var button=document.getElementById(item[0]);if(button)button.disabled=!running||score<item[1];
            });
        }

        function tank(x, y, dir, enemy) {
            return {x: x, y: y, dir: dir, enemy: !!enemy, cool: 0, speed: enemy ? 70 : 105,
                hp:enemy?1:2,shield:enemy?0:2,rapid:0,pierce:0,spread:0,bulletSpeed:0};
        }

        function resetLevel() {
            walls = TankMap.create(level, ROWS, COLS); mapVersion++;
            player = tank((COLS / 2 | 0) * TILE + TILE / 2, (ROWS - 4) * TILE + TILE / 2, 0, false);
            enemies = [];
            clearBullets();
            pickups = [];
            base = {x: 6.5 * TILE, y: 11.5 * TILE, hp: Math.min(8, 3 + level), maxHp: Math.min(8, 3 + level)};
            freezeTime=0;baseShieldTime=0;baseArmorTime=0;baseArmorBackup=null;respawnTime=0;
            for (var i = 0; i < Math.min(2 + level, 5); i++) spawnEnemy();
        }

        function spawnEnemy() {
            var spots = [[1.5*TILE,1.5*TILE],[(COLS-1.5)*TILE,1.5*TILE],[(COLS/2)*TILE,1.5*TILE],
                [3.5*TILE,1.5*TILE],[(COLS-3.5)*TILE,1.5*TILE]];
            var s = TankEngine.findSafeSpawn(spots, {
                map:walls,tile:TILE,rows:ROWS,cols:COLS,radius:12,enemies:enemies,player:player
            });
            if (!s) return false;
            var e = tank(s[0], s[1], 2, true);
            e.think = 0;
            e.carrier = Math.random() < TankConfig.carrierChance;
            enemies.push(e);
            return true;
        }

        function startGame() {
            cancelAnimationFrame(rafId);
            score = 0;
            level = 1;
            lives = 3;
            over = false;
            running = true;
            resetLevel();
            hideResult();
            setHud();
            setStatus('战斗中 · 消灭敌军');
            lastTs = performance.now();
            rafId=requestAnimationFrame(loop);
        }

        function resetMenu() {
            cancelAnimationFrame(rafId);rafId=0;
            running = false;
            over = false;
            score = 0;
            level = 1;
            lives = 3;
            walls = TankMap.create(1, ROWS, COLS); mapVersion++;
            player = null;
            enemies = [];
            clearBullets();
            pickups = [];
            base = {x: 6.5 * TILE, y: 11.5 * TILE, hp: 3, maxHp: 3};
            freezeTime=0;baseShieldTime=0;baseArmorTime=0;baseArmorBackup=null;respawnTime=0;
            setHud();
            setStatus('按开始进入战斗');
            hideResult();
            draw();
        }

        function moveTank(t, dt) {
            movementContext.map=walls;movementContext.enemies=enemies;movementContext.player=player;
            return TankEngine.move(movementContext, t, dt, DIR);
        }

        function fire(t) {
            if (t.cool>0||bullets.length>=TankConfig.maxBullets)return;
            var d = DIR[t.dir];
            var offsets = !t.enemy && t.spread > 0 ? [-52, 0, 52] : [0];
            if(bullets.length+offsets.length>TankConfig.maxBullets)return;
            t.cool = t.enemy ? 0.9 : (t.rapid > 0 ? 0.16 : 0.35);
            offsets.forEach(function (side) {
                var speed=!t.enemy&&t.bulletSpeed>0?TankConfig.boostedBulletSpeed:TankConfig.playerBulletSpeed;
                bullets.push(Object.assign(bulletPool.pop()||{},{x:t.x+d[0]*18,y:t.y+d[1]*18,
                    vx:d[0]*speed+(d[1]*side),vy:d[1]*speed-(d[0]*side),enemy:t.enemy,life:2.2,
                    pierce:!t.enemy&&t.pierce>0?2:0}));
            });
        }

        function recycleBulletAt(index){var old=bullets.splice(index,1)[0];if(old&&bulletPool.length<TankConfig.maxBullets)bulletPool.push(old);}
        function clearBullets(){if(bullets)while(bullets.length)recycleBulletAt(bullets.length-1);else bullets=[];}

        function ai(e, dt) {
            if (freezeTime > 0) return;
            e.think -= dt;
            if (e.think <= 0) {
                e.think = 0.4 + Math.random() * 0.8;
                if (player && Math.random() < 0.55) {
                    var dx = player.x - e.x, dy = player.y - e.y;
                    e.dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : 3) : (dy > 0 ? 2 : 0);
                } else {
                    e.dir = (Math.random() * 4) | 0;
                }
                if (Math.random() < 0.45) fire(e);
            }
            if (!moveTank(e, dt)) {
                e.dir = (e.dir + 1 + ((Math.random() * 3) | 0)) % 4;
                e.think = Math.min(e.think, 0.2);
            }
            if (e.cool > 0) e.cool -= dt;
        }

        function update(dt) {
            if (!running || over) return;
            freezeTime = Math.max(0, freezeTime - dt);
            baseShieldTime = Math.max(0, baseShieldTime - dt);
            if (baseArmorTime > 0) {
                baseArmorTime = Math.max(0, baseArmorTime-dt);
                if (baseArmorTime === 0) restoreBaseWalls();
            }
            if(player){player.shield=Math.max(0,player.shield-dt);player.rapid=Math.max(0,player.rapid-dt);player.pierce=Math.max(0,player.pierce-dt);player.spread=Math.max(0,player.spread-dt);player.bulletSpeed=Math.max(0,player.bulletSpeed-dt);}
            if (respawnTime > 0) {
                respawnTime -= dt;
                var second=Math.max(1,Math.ceil(respawnTime));
                if(player===null&&second!==respawnSecond){respawnSecond=second;setStatus('坦克损毁，'+second+' 秒后复活');}
                if (respawnTime <= 0) respawnPlayer();
            }
            // player input
            if (player && (keys[38] || keys[87])) player.dir = 0;
            else if (player && (keys[39] || keys[68])) player.dir = 1;
            else if (player && (keys[40] || keys[83])) player.dir = 2;
            else if (player && (keys[37] || keys[65])) player.dir = 3;
            var moving = keys[37] || keys[38] || keys[39] || keys[40] || keys[65] || keys[87] || keys[68] || keys[83];
            if (player && moving) moveTank(player, dt);
            if (player && keys[32]) fire(player);
            if (player && player.cool > 0) player.cool -= dt;

            for (var i = 0; i < enemies.length; i++) ai(enemies[i], dt);

            // bullets
            for (var b = bullets.length - 1; b >= 0; b--) {
                var bul = bullets[b];
                bul.x += bul.vx * dt;
                bul.y += bul.vy * dt;
                bul.life -= dt;
                var c = (bul.x / TILE) | 0, r = (bul.y / TILE) | 0;
                var hitWall = false;
                if (r < 0 || c < 0 || r >= ROWS || c >= COLS) {
                    recycleBulletAt(b);
                    continue;
                }
                if (walls[r][c] === 1 || walls[r][c] === 5) {
                    walls[r][c] = walls[r][c] === 1 ? 5 : 0;
                    mapVersion++;
                    if (bul.pierce > 0) bul.pierce--; else hitWall = true;
                } else if (walls[r][c] === 2) hitWall = true;
                if (hitWall || bul.life <= 0) {
                    recycleBulletAt(b);
                    continue;
                }

                if (!bul.enemy && player) {
                    // hit enemies
                    for (var ei = enemies.length - 1; ei >= 0; ei--) {
                        var en = enemies[ei];
                        if (Math.abs(en.x - bul.x) < 16 && Math.abs(en.y - bul.y) < 16) {
                            enemies.splice(ei, 1);
                            recycleBulletAt(b);
                            score += 100;
                            if(en.carrier||Math.random()<TankConfig.normalDropChance)spawnPickup(en.x,en.y);
                            setHud();
                            break;
                        }
                    }
                } else if (bul.enemy && player) {
                    if (Math.abs(player.x - bul.x) < 16 && Math.abs(player.y - bul.y) < 16) {
                        recycleBulletAt(b);
                        if (player.shield > 0) continue;
                        player.hp -= 1;
                        if (player.hp > 0) { player.shield = 1.2; setStatus('装甲受损，还可承受 ' + player.hp + ' 次攻击'); continue; }
                        lives -= 1;
                        setHud();
                        if (lives <= 0) {
                            over = true;
                            running = false;
                            setStatus('战败 · 得分 '+score);showResult('生命耗尽，最终得分 '+score);
                        } else {
                            player=null;respawnTime=2;respawnSecond=2;
                            setStatus('坦克损毁，2 秒后从备用营地复活');
                        }
                    }
                }
            }

            // 基地可承受多次攻击；护盾期间外围砖墙与基地都无敌。
            for (var bi = bullets.length - 1; bi >= 0; bi--) {
                var bb = bullets[bi];
                if (bb.enemy && base && Math.abs(base.x - bb.x) < 17 && Math.abs(base.y - bb.y) < 17) {
                    recycleBulletAt(bi);
                    if (baseShieldTime <= 0 && --base.hp <= 0) {
                        lives = Math.max(0, lives - 1); setHud();
                        base.hp = base.maxHp; baseShieldTime = 4;
                        setStatus(lives ? '基地受损，备用核心已启动' : '基地失守');
                        if(!lives){over=true;running=false;showResult('基地失守，最终得分 '+score);}
                    }
                }
            }
            collectPickups(dt);

            if (running && !over && enemies.length === 0) {
                level += 1;
                score += 200;
                setHud();
                setStatus('过关！进入第 ' + level + ' 关');
                MiniCelebrate.play({title: '顺利过关！', note: '小小坦克手，你太棒啦！', icon: '🐻'});
                if (window.MiniFeedback) MiniFeedback.praise('很棒！');
                resetLevel();
            }
        }

        function respawnPlayer() {
            var spots = [[6.5*TILE,9.5*TILE],[5.5*TILE,9.5*TILE],[7.5*TILE,9.5*TILE]];
            var pos = TankEngine.findSafeSpawn(spots, {map:walls,tile:TILE,rows:ROWS,cols:COLS,radius:12,enemies:enemies,player:null});
            if (!pos) {
                respawnTime=.25;
                setStatus('出生区被占用，正在等待安全复活');
                return;
            }
            player = tank(pos[0], pos[1], 0, false);
            respawnSecond=-1;
            player.shield = 3;
            setStatus('已复活 · 3 秒护盾');
        }

        function spawnPickup(x, y) {
            if (pickups.length >= 4) return;
            var col=Math.floor(x/TILE),row=Math.floor(y/TILE),cells=[[row,col],[row,col-1],[row,col+1],[row-1,col],[row+1,col]];
            var candidates=cells.filter(function(p){return p[0]>0&&p[1]>0&&p[0]<ROWS-1&&p[1]<COLS-1&&walls[p[0]][p[1]]!==3&&walls[p[0]][p[1]]!==2&&walls[p[0]][p[1]]!==1&&walls[p[0]][p[1]]!==5;})
                .map(function(p){return[(p[1]+.5)*TILE,(p[0]+.5)*TILE];});
            var pos=TankEngine.findSafeSpawn(candidates,{map:walls,tile:TILE,rows:ROWS,cols:COLS,radius:10,enemies:enemies,player:player});
            if(!pos||base&&Math.abs(base.x-pos[0])<30&&Math.abs(base.y-pos[1])<30)return;
            pickups.push({x:pos[0],y:pos[1],type:PICKUP_TYPES[(Math.random()*PICKUP_TYPES.length)|0],life:TankConfig.pickupLifetime});
        }

        function collectPickups(dt) {
            for (var i = pickups.length - 1; i >= 0; i--) {
                var p = pickups[i]; p.life -= dt;
                if (p.life <= 0) { pickups.splice(i, 1); continue; }
                if (!player || Math.abs(player.x - p.x) >= 22 || Math.abs(player.y - p.y) >= 22) continue;
                pickups.splice(i, 1); applyPickup(p.type);
            }
        }

        function applyPickup(type) {
            var names={life:'生命补给',shield:'无敌护盾',freeze:'全屏冰冻',rapid:'快速射击',armor:'基地钢墙',grenade:'全屏手雷',pierce:'穿甲弹',spread:'三向散射',speed:'子弹加速'};
            if (type === 'life') lives++;
            else if(type==='shield')player.shield=Math.max(player.shield,TankConfig.durations.shield);
            else if(type==='freeze')freezeTime=TankConfig.durations.freeze;
            else if(type==='rapid')player.rapid=TankConfig.durations.rapid;
            else if(type==='pierce')player.pierce=TankConfig.durations.pierce;
            else if(type==='spread')player.spread=TankConfig.durations.spread;
            else if(type==='speed')player.bulletSpeed=TankConfig.durations.speed;
            else if (type === 'armor') { armorBase(); base.hp = base.maxHp; }
            else if (type === 'grenade') { score += enemies.length * 100; enemies.length = 0; }
            score += 50; setHud(); setStatus('获得：' + names[type]);
        }

        function armorBase() {
            if (baseArmorTime <= 0) {
                baseArmorBackup = BASE_RING.map(function(p){return walls[p[0]][p[1]];});
                BASE_RING.forEach(function(p){walls[p[0]][p[1]]=2;});
                mapVersion++;
            }
            baseArmorTime=TankConfig.durations.armor;
        }

        function restoreBaseWalls() {
            if (!baseArmorBackup) return;
            BASE_RING.forEach(function(p,i){if(walls[p[0]][p[1]]===2)walls[p[0]][p[1]]=baseArmorBackup[i];});
            baseArmorBackup=null;mapVersion++;
        }

        function buySupply(kind,cost){
            if(!running||score<cost)return;score-=cost;
            if(kind==='repair')base.hp=base.maxHp;else if(kind==='life')lives++;else armorBase();
            setHud();setStatus(kind==='repair'?'补给站：基地已修复':kind==='life'?'补给站：生命 +1':'补给站：基地钢墙已启动');
        }

        function showResult(text){document.getElementById('tankResultText').textContent=text;document.getElementById('tankResult').hidden=false;}
        function hideResult(){document.getElementById('tankResult').hidden=true;}

        function draw() {
            TankRenderer.draw(ctx, {
                canvas:canvas,tile:TILE,rows:ROWS,cols:COLS,walls:walls,mapVersion:mapVersion,
                player:player,enemies:enemies,bullets:bullets,pickups:pickups,base:base,
                baseShieldTime:baseShieldTime,baseArmorTime:baseArmorTime
            });
        }
        function loop(ts) {
            if (!running) {
                draw();
                return;
            }
            var dt = Math.min(0.033, (ts - lastTs) / 1000);
            lastTs = ts;
            update(dt);
            draw();
            if (running) rafId=requestAnimationFrame(loop);
        }

        function resize() {
            var side = Math.max(240, Math.min(520, window.innerWidth - 36));
            canvas.style.width = side + 'px';
            canvas.style.height = side + 'px';
        }

        window.addEventListener('resize', resize);
        resize();

        document.getElementById('startBtn').addEventListener('click', startGame);
        document.getElementById('resetBtn').addEventListener('click', resetMenu);
        document.getElementById('retryBtn').addEventListener('click',startGame);
        [['buyRepair','repair','修复基地'],['buyLife','life','增加生命'],['buyArmor','armor','基地钢墙']].forEach(function(item){
            var button=document.getElementById(item[0]),cost=TankConfig.costs[item[1]];
            button.textContent=item[2]+' '+cost+'分';button.addEventListener('click',function(){buySupply(item[1],cost);});
        });
        window.addEventListener('pagehide', function () {
            running=false;cancelAnimationFrame(rafId);
            window.removeEventListener('resize',resize);
        }, {once:true});
        resetMenu();
    })();
