/**
 * @file library.js
 * @description 无尽书阁 · 炼丹炼器模块。
 * 负责渲染纳戒背包中的修仙灵材、展示古法配方及发起法宝与神丹炼制。
 */
(() => {
    'use strict';

    var base = location.pathname.split('/pages/arena/')[0] || '';
    var token = localStorage.getItem('token') || '';
    var headers = { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token };

    var ITEM_NAMES = {
        herb: { name: '九叶灵草', icon: '🌿', desc: '筑基固本灵草' },
        ore: { name: '寒铁玄精', icon: '🪨', desc: '淬炼飞剑矿石' },
        star_dust: { name: '太虚星尘', icon: '✨', desc: '聚纳星煞精华' },
        pill: { name: '太乙炼气丹', icon: '💊', desc: '大境界突破必需' },
        star_pill: { name: '造化升星丹', icon: '🌟', desc: '仙修升星代碎片' },
        refine_stone: { name: '天罡精炼石', icon: '💎', desc: '神装精炼强化材料' },
        green_sword: { name: '青锋飞剑', icon: '🗡️', desc: '古法飞剑灵器' }
    };

    async function load() {
        try {
            var r = await fetch(base + '/api/arena/library', { headers: headers });
            var d = await r.json();
            if (!r.ok) throw new Error(d.error || '获取书阁失败');
            render(d);
        } catch (e) {
            console.warn('加载书阁失败:', e.message);
        }
    }

    function render(data) {
        var invEl = document.getElementById('inventory');
        var recEl = document.getElementById('recipes');
        if (!invEl || !recEl) return;

        // 1. 纳戒背包
        var itemsHtml = '';
        if (data.items && data.items.length > 0) {
            itemsHtml = data.items.map(function (it) {
                var meta = ITEM_NAMES[it.id] || { name: it.id, icon: '📦', desc: '修仙至宝' };
                return '<div class="glass-card" style="padding:12px; text-align:center;">' +
                    '<div style="font-size:28px; margin-bottom:4px;">' + meta.icon + '</div>' +
                    '<b style="font-size:13px; color:var(--text); display:block;">' + meta.name + '</b>' +
                    '<span class="tag" style="color:var(--sky); margin:4px 0;">存量 × ' + it.quantity + '</span>' +
                    '<small style="font-size:10px; color:var(--text-muted); display:block;">' + meta.desc + '</small>' +
                    '</div>';
            }).join('');
        } else {
            itemsHtml = '<p style="color:var(--text-muted); font-size:12px; grid-column:1/-1;">纳戒空空如也，可前往仙途历练探索收集灵材。</p>';
        }
        invEl.innerHTML = itemsHtml;

        // 2. 炼制配方
        var recHtml = '';
        if (data.recipes && data.recipes.length > 0) {
            recHtml = data.recipes.map(function (rec) {
                var inputMeta = ITEM_NAMES[rec.input] || { name: rec.input, icon: '📦' };
                var outputMeta = ITEM_NAMES[rec.output] || { name: rec.name, icon: '✨' };
                return '<div class="glass-card" style="padding:16px; display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:12px;">' +
                    '<div style="display:flex; align-items:center; gap:12px;">' +
                    '<div style="font-size:32px;">' + outputMeta.icon + '</div>' +
                    '<div>' +
                    '<h4 style="font-size:15px; color:var(--gold); margin-bottom:2px;">' + rec.name + ' × ' + rec.outputCount + '</h4>' +
                    '<p style="font-size:12px; color:var(--text-muted);">' +
                    '消耗：' + inputMeta.name + ' × ' + rec.inputCount + ' · 灵币 ' + rec.coinCost +
                    '</p>' +
                    '</div>' +
                    '</div>' +
                    '<button class="btn btn-primary" data-craft="' + rec.id + '" style="font-size:12px; padding:6px 16px;">🔥 启炉炼制</button>' +
                    '</div>';
            }).join('');
        }
        recEl.innerHTML = recHtml;

        // 绑定炼制事件
        recEl.querySelectorAll('[data-craft]').forEach(function (btn) {
            btn.onclick = async function () {
                var recipeId = btn.dataset.craft;
                try {
                    btn.disabled = true;
                    var res = await fetch(base + '/api/arena/library/craft', {
                        method: 'POST',
                        headers: headers,
                        body: JSON.stringify({ recipeId: recipeId })
                    });
                    var body = await res.json();
                    if (!res.ok) throw new Error(body.error || '炼制失败');
                    render(body);
                    if (window.toast) window.toast('古炉金光盛放，炼制成功！');
                    // 刷新全局状态与顶栏资源
                    if (window.refreshArenaState) window.refreshArenaState();
                } catch (err) {
                    if (window.toast) window.toast(err.message);
                } finally {
                    btn.disabled = false;
                }
            };
        });
    }

    window.ArenaLibrary = { load: load, render: render };

    // 页面初次载入
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', load);
    } else {
        load();
    }
})();
