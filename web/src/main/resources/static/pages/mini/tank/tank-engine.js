(function (w) {
    'use strict';
    // Pure terrain/entity collision helpers. Callers own entity state and pass a
    // complete opts snapshot; move() mutates only the supplied entity position.

    function tileAt(map, tile, rows, cols, x, y) {
        var col = Math.floor(x / tile), row = Math.floor(y / tile);
        if (row < 0 || col < 0 || row >= rows || col >= cols) return -1;
        return map[row][col];
    }

    function terrainBlocks(value) {
        return value !== 0 && value !== 4 && value !== 6;
    }

    function collides(opts, x, y, self) {
        var radius = opts.radius || 12;
        if (terrainBlocks(tileAt(opts.map,opts.tile,opts.rows,opts.cols,x-radius,y-radius)) ||
            terrainBlocks(tileAt(opts.map,opts.tile,opts.rows,opts.cols,x+radius,y-radius)) ||
            terrainBlocks(tileAt(opts.map,opts.tile,opts.rows,opts.cols,x-radius,y+radius)) ||
            terrainBlocks(tileAt(opts.map,opts.tile,opts.rows,opts.cols,x+radius,y+radius))) return true;
        var enemies = opts.enemies || [];
        for (var j = 0; j < enemies.length; j++) {
            var enemy = enemies[j];
            if (enemy !== self && Math.abs(enemy.x-x) < 28 && Math.abs(enemy.y-y) < 28) return true;
        }
        var player = opts.player;
        return !!(player && player !== self && Math.abs(player.x-x) < 28 && Math.abs(player.y-y) < 28);
    }

    function move(opts, entity, dt, direction) {
        var d = direction[entity.dir];
        var step = entity.speed * dt;
        var nx = entity.x + d[0] * step, ny = entity.y + d[1] * step;
        if (!collides(opts, nx, ny, entity)) {
            entity.x = nx; entity.y = ny; return true;
        }
        var tile = opts.tile;
        var snapX = Math.round((entity.x-tile/2)/tile)*tile+tile/2;
        var snapY = Math.round((entity.y-tile/2)/tile)*tile+tile/2;
        var assist = Math.max(2, step * 0.8), shifted = false;
        if (d[0] && Math.abs(snapY-entity.y) <= 10) {
            var sy = entity.y + Math.sign(snapY-entity.y) * Math.min(assist, Math.abs(snapY-entity.y));
            if (!collides(opts, entity.x, sy, entity)) { entity.y = sy; shifted = true; }
        } else if (d[1] && Math.abs(snapX-entity.x) <= 10) {
            var sx = entity.x + Math.sign(snapX-entity.x) * Math.min(assist, Math.abs(snapX-entity.x));
            if (!collides(opts, sx, entity.y, entity)) { entity.x = sx; shifted = true; }
        }
        return shifted;
    }

    function safeSpawn(candidates, opts) {
        return findSafeSpawn(candidates,opts)||candidates[0];
    }

    function findSafeSpawn(candidates,opts){for(var i=0;i<candidates.length;i++)if(!collides(opts,candidates[i][0],candidates[i][1],null))return candidates[i];return null;}

    w.TankEngine = {tileAt:tileAt,terrainBlocks:terrainBlocks,collides:collides,move:move,safeSpawn:safeSpawn,findSafeSpawn:findSafeSpawn};
})(window);
