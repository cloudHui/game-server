/**
 * @file journey.js
 * @description 仙途历练 · 探索秘境模块。
 * 负责渲染历练地图、体力监控、单次探索及多倍快速扫荡。
 */
(() => {
    'use strict';

    var base = location.pathname.split('/pages/arena/')[0] || '';
    var token = localStorage.getItem('token') || '';
    var headers = { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token };

    var MAP_CONFIG = [
        { id: 1, name: '青云古山', icon: '⛰️', desc: '灵气初聚，盛产九叶灵草与初阶星尘' },
        { id: 2, name: '玄铁寒谷', icon: '⛏️', desc: '千年玄脉，盛产寒铁玄精与淬剑矿石' },
        { id: 3, name: '星陨绝坡', icon: '🌠', desc: '域外星煞，盛产太虚星尘与九叶灵草' },
        { id: 4, name: '万妖魔林', icon: '🌲', desc: '妖兽盘踞，盛产深山玄矿与异种灵草' },
        { id: 5, name: '幽冥魔渊', icon: '🌋', desc: '地煞魔火，盛产大量极品星尘' },
        { id: 6, name: '太虚幻境', icon: '🌌', desc: '天道秘境，海量奇珍异宝与神石' }
    ];

    async function load() {
        try {
            var r = await fetch(base + '/api/arena/journey', { headers: headers });
            var d = await r.json();
            if (!r.ok) throw new Error(d.error || '获取历练状态失败');
            render(d);
        } catch (e) {
            console.warn('加载历练失败:', e.message);
        }
    }

    function render(s) {
        var staEl = document.getElementById('stamina');
        var mapsEl = document.getElementById('journey-maps');
        if (!staEl || !mapsEl) return;

        staEl.textContent = '⚡ 体力 ' + (s.stamina || 0) + ' / 120';

        var maxMap = s.maxMap || 1;
        var html = MAP_CONFIG.map(function (cfg) {
            var locked = cfg.id > maxMap;
            return '<div class="glass-card" style="padding:18px; position:relative;' + (locked ? 'opacity:0.6;' : '') + '">' +
                '<div class="title" style="margin-bottom:8px;">' +
                '<h4 style="font-size:16px; color:' + (locked ? 'var(--text-muted)' : 'var(--sky)') + ';">' +
                cfg.icon + ' ' + cfg.name +
                '</h4>' +
                '<span class="tag" style="color:' + (locked ? 'var(--text-muted)' : 'var(--gold)') + ';">' +
                (locked ? '未解锁' : '已通关') +
                '</span>' +
                '</div>' +
                '<p style="font-size:12px; color:var(--text-muted); margin-bottom:14px;">' + cfg.desc + '</p>' +
                '<div style="font-size:11px; color:var(--gold); margin-bottom:12px;">单次消耗：体力 × 6</div>' +
                '<div style="display:flex; gap:8px;">' +
                '<button class="btn btn-primary" data-map="' + cfg.id + '" data-runs="1" style="flex:1; font-size:12px;" ' + (locked ? 'disabled' : '') + '>探索 1 次</button>' +
                '<button class="btn btn-ghost" data-map="' + cfg.id + '" data-runs="5" style="flex:1; font-size:12px;" ' + (locked ? 'disabled' : '') + '>扫荡 5 次</button>' +
                '</div>' +
                '</div>';
        }).join('');

        mapsEl.innerHTML = html;

        mapsEl.querySelectorAll('[data-map]').forEach(function (btn) {
            btn.onclick = async function () {
                var mapId = parseInt(btn.dataset.map, 10);
                var runs = parseInt(btn.dataset.runs, 10);
                try {
                    btn.disabled = true;
                    var res = await fetch(base + '/api/arena/journey', {
                        method: 'POST',
                        headers: headers,
                        body: JSON.stringify({ map: mapId, runs: runs })
                    });
                    var body = await res.json();
                    if (!res.ok) throw new Error(body.error || '历练失败');
                    render(body);
                    if (window.toast) window.toast('历练大捷！收获珍稀灵材已入纳戒');
                    if (window.ArenaLibrary) window.ArenaLibrary.load();
                    if (window.refreshArenaState) window.refreshArenaState();
                } catch (err) {
                    if (window.toast) window.toast(err.message);
                } finally {
                    btn.disabled = false;
                }
            };
        });
    }

    window.ArenaJourney = { load: load, render: render };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', load);
    } else {
        load();
    }
})();
