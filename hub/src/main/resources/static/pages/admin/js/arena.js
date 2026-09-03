(function (w) {
    'use strict';
    var Tools = w.AdminTools;
    var byId = Tools.byId;
    var escapeHtml = Tools.escapeHtml;
    var arenaUserId = 0;
    var arenaState = null;

    function response(data) {
        if (!data || data.code !== 0) throw new Error((data && data.msg) || '剑气除魔后台请求失败');
        return data;
    }

    function arenaGet(path) {
        return w.Admin.get('/arena' + path).then(response);
    }

    function arenaPost(path, payload) {
        return w.Admin.post('/arena' + path, payload).then(response);
    }

    function showError(error) {
        w.Admin.msg('arenaMsg', error.message || '剑气除魔后台请求失败', false);
        return null;
    }

    function readInteger(id, label, min, max) {
        var value = Tools.nonNegativeInteger(byId(id).value, label, max == null ? 2147483647 : max);
        if (value < min || (max != null && value > max)) {
            throw new Error(label + '必须在 ' + min + ' 到 ' + max + ' 之间');
        }
        return value;
    }

    function readSignedInteger(id, label) {
        var raw = byId(id).value.trim();
        if (!/^-?\d+$/.test(raw)) throw new Error(label + '必须是整数');
        var value = Number(raw);
        if (!isFinite(value) || value < -2147483648 || value > 2147483647) {
            throw new Error(label + '超出整数范围');
        }
        return value;
    }

    function renderPlayerRows(players) {
        var body = byId('arenaPlayerBody');
        body.innerHTML = players.length ? players.map(function (player) {
            return '<tr><td>' + escapeHtml(player.userId) + '</td><td>' +
                escapeHtml(player.nickname || player.username) + '</td><td>' + escapeHtml(player.liquid) +
                '</td><td>' + escapeHtml(player.coins) + '</td><td>' + escapeHtml(player.fate) +
                '</td><td>' + escapeHtml(player.stones) + '</td><td>' + escapeHtml(player.dungeonCleared) +
                '</td><td class="actions"><button class="btn btn-ghost arena-player-open" data-user-id="' +
                escapeHtml(player.userId) + '" data-username="' + escapeHtml(player.username || '') + '">管理</button></td></tr>';
        }).join('') : '<tr><td colspan="8">暂无玩家数据</td></tr>';
        body.querySelectorAll('.arena-player-open').forEach(function (button) {
            button.addEventListener('click', function () {
                w.openArenaPlayer(Number(button.getAttribute('data-user-id')), button.getAttribute('data-username') || '');
            });
        });
    }

    function renderHeroRows(heroes) {
        var body = byId('arenaHeroBody');
        body.innerHTML = heroes.length ? heroes.map(function (hero) {
            var id = String(hero.id || '');
            return '<tr><td>' + escapeHtml(id) + '</td><td><input id="ar-' + escapeHtml(id) +
                '" type="number" min="1" max="80" value="' + escapeHtml(hero.rank) + '"></td><td><input id="as-' +
                escapeHtml(id) + '" type="number" min="1" max="5" value="' + escapeHtml(hero.stars) +
                '"></td><td><input id="ak-' + escapeHtml(id) + '" type="number" min="1" max="100" value="' +
                escapeHtml(hero.skill) + '"></td><td><input id="ah-' + escapeHtml(id) + '" type="number" min="0" value="' +
                escapeHtml(hero.shards) + '"></td><td class="actions"><button class="btn btn-primary arena-hero-save" data-hero-id="' +
                escapeHtml(id) + '">保存</button></td></tr>';
        }).join('') : '<tr><td colspan="6">暂无仙侣</td></tr>';
        body.querySelectorAll('.arena-hero-save').forEach(function (button) {
            button.addEventListener('click', function () { w.saveArenaHero(button.getAttribute('data-hero-id')); });
        });
    }

    function renderArenaState(state) {
        arenaState = state || {};
        var journey = arenaState.journey || {};
        byId('arenaProgress').textContent = '灵液 ' + (arenaState.liquid || 0) + ' · 灵币 ' +
            (arenaState.coins || 0) + ' · 仙缘 ' + (arenaState.fate || 0) + ' · 保底 ' +
            (arenaState.pity || 0) + '/90 · 历练体力 ' + (journey.stamina || 0) + '/120 · 地图 ' +
            (journey.maxMap || 0);
        byId('arenaInventory').textContent = (arenaState.inventory || []).map(function (item) {
            return item.id + ' × ' + item.quantity;
        }).join('　') || '空';
        byId('arenaCleared').value = arenaState.dungeonCleared == null ? 0 : arenaState.dungeonCleared;
        byId('arenaAttempts').value = arenaState.dungeonAttempts == null ? 0 : arenaState.dungeonAttempts;
        byId('arenaFormation').value = arenaState.formationLevel == null ? 1 : arenaState.formationLevel;
        byId('arenaGrotto').value = arenaState.grottoLevel == null ? 1 : arenaState.grottoLevel;
        renderHeroRows(arenaState.heroes || []);
    }

    function applyState(data, refreshPlayers) {
        renderArenaState(data.state);
        if (refreshPlayers) return w.loadArenaPlayers().then(function () { return data; });
        return data;
    }

    w.loadArenaPlayers = function () {
        return arenaGet('/players').then(function (data) {
            renderPlayerRows(data.players || []);
            w.Admin.msg('arenaMsg', '共 ' + (data.players || []).length + ' 名玩家', true);
            return data;
        }).catch(showError);
    };

    w.openArenaPlayer = function (id, name) {
        arenaUserId = id;
        byId('arenaDetail').hidden = false;
        byId('arenaDetailTitle').textContent = '玩家详情 · ' + name;
        return w.loadArenaDetail();
    };

    w.loadArenaDetail = function () {
        if (!arenaUserId) return Promise.resolve();
        return arenaGet('/state?userId=' + encodeURIComponent(arenaUserId)).then(function (data) {
            renderArenaState(data.state);
            return data;
        }).catch(showError);
    };

    w.adjustArenaItem = function () {
        var payload;
        try {
            payload = {userId: arenaUserId, itemId: byId('arenaItemId').value, delta: readSignedInteger('arenaItemDelta', '物品数量')};
        } catch (error) {
            return showError(error);
        }
        return arenaPost('/item', payload).then(applyState).catch(showError);
    };

    w.adjustArenaResource = function () {
        var payload;
        try {
            payload = {userId: arenaUserId, resource: byId('arenaResource').value, delta: readSignedInteger('arenaDelta', '调整数量')};
        } catch (error) {
            return showError(error);
        }
        return arenaPost('/resource', payload).then(function (data) { return applyState(data, true); }).catch(showError);
    };

    w.setArenaResource = function () {
        if (!arenaState) return showError(new Error('请先加载玩家详情'));
        var resource = byId('arenaResource').value;
        var target;
        try {
            target = readInteger('arenaDelta', '目标资源值', 0);
        } catch (error) {
            return showError(error);
        }
        var current = Number(arenaState[resource]) || 0;
        return arenaPost('/resource', {userId: arenaUserId, resource: resource, delta: target - current})
            .then(function (data) { return applyState(data, true); }).catch(showError);
    };

    w.saveArenaProgress = function () {
        var payload;
        try {
            payload = {
                userId: arenaUserId,
                dungeonCleared: readInteger('arenaCleared', '通关数', 0, 12),
                dungeonAttempts: readInteger('arenaAttempts', '剩余次数', 0),
                formationLevel: readInteger('arenaFormation', '战阵等级', 1),
                grottoLevel: readInteger('arenaGrotto', '洞府等级', 1)
            };
        } catch (error) {
            return showError(error);
        }
        return arenaPost('/progress', payload).then(applyState).catch(showError);
    };

    w.addArenaHero = function () {
        var heroId = byId('arenaNewHero').value;
        var current = (arenaState && (arenaState.heroes || []).find(function (hero) { return hero.id === heroId; })) || {};
        return arenaPost('/hero', {
            userId: arenaUserId,
            heroId: heroId,
            rank: current.rank || 1,
            stars: current.stars || 1,
            skill: current.skill || 1,
            shards: current.shards || 0
        }).then(applyState).catch(showError);
    };

    w.saveArenaHero = function (heroId) {
        var payload;
        try {
            payload = {
                userId: arenaUserId,
                heroId: heroId,
                rank: readInteger('ar-' + heroId, '境界', 1, 80),
                stars: readInteger('as-' + heroId, '星级', 1, 5),
                skill: readInteger('ak-' + heroId, '功法', 1, 100),
                shards: readInteger('ah-' + heroId, '碎片', 0)
            };
        } catch (error) {
            return showError(error);
        }
        return arenaPost('/hero', payload).then(applyState).catch(showError);
    };
})(window);
