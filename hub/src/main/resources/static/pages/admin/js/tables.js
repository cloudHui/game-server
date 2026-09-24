(function (w) {
    'use strict';

    var pollTimer, watchGeneration = 0;
    var currentInspectingTableId = null;
    var select = document.getElementById('robotMatchRoom');
    if (select && w.RoomConfig) {
        select.innerHTML = RoomConfig.robotTests().map(function (item) {
            return '<option value="' + item.roomId + '">' + item.name + '</option>';
        }).join('');
    }

    function checked(id) {
        var el = document.getElementById(id);
        return el && el.checked ? 1 : 0;
    }

    function watchTable(tableId, attempt, generation) {
        if (generation !== watchGeneration) return;
        clearTimeout(pollTimer);
        Admin.get('/replays?page=1&size=100').then(function (data) {
            if (generation !== watchGeneration) return;
            var replay = (data.replays || []).find(function (item) {
                return String(item.tableId) === String(tableId) && item.status !== '已结算';
            }) || (data.replays || []).find(function (item) {
                return String(item.tableId) === String(tableId);
            });
            if (replay) return w.openReplay(replay.date, replay.name);
            if ((attempt || 0) >= 60) return Admin.msg('tablesMsg', '桌 ' + tableId + ' 已存在，但还没有生成首个回放事件。', false);
            pollTimer = setTimeout(function () {
                watchTable(tableId, (attempt || 0) + 1, generation);
            }, 1000);
        }).catch(function () {
            if (generation === watchGeneration) {
                pollTimer = setTimeout(function () {
                    watchTable(tableId, (attempt || 0) + 1, generation);
                }, 1500);
            }
        });
    }

    /**
     * 加载当前所有活跃牌桌概要。
     */
    function load() {
        Admin.get('/tables').then(function (data) {
            var body = document.getElementById('tableBody');
            var meta = document.getElementById('tablesMeta');
            if (data.code !== 0) {
                body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>';
                return;
            }
            if (meta) {
                meta.textContent = '在线用户 ' + (data.onlineUsers || 0);
            }

            var tables = data.tables || data.rooms || [];
            var rows = [];

            tables.forEach(function (table) {
                var gameName = table.gameTypeName || (w.RoomConfig ? RoomConfig.game(table.gameType).name : ('玩法' + table.gameType));
                var stateDesc = table.stateDesc || '未知状态';
                var stateCode = table.stateCode !== undefined ? table.stateCode : '-';
                var roundText = (table.currentRound || 1) + ' / ' + (table.totalRounds || 1) + ' 局';
                var playersText = (table.players && table.players.length) ? table.players.join('； ') : (table.playerCount + ' 人');

                rows.push(
                    '<tr>' +
                    '<td><strong>' + table.tableId + '</strong></td>' +
                    '<td><span style="color:var(--accent);font-weight:bold;">' + gameName + '</span></td>' +
                    '<td><span style="background:#eef1f6;padding:2px 8px;border-radius:4px;font-size:12px;font-weight:bold;color:var(--ink);">码 ' + stateCode + '：' + stateDesc + '</span></td>' +
                    '<td>' + roundText + '</td>' +
                    '<td style="font-size:13px;color:var(--muted);max-width:320px;word-break:break-all;">' + playersText + '</td>' +
                    '<td>' +
                    '<button class="btn btn-sm" style="padding:4px 10px;margin-right:6px;" onclick="inspectTable(' + table.tableId + ')">透视详情</button>' +
                    '<button class="btn btn-ghost btn-sm" style="padding:4px 10px;" onclick="watchLiveTable(' + table.tableId + ')">进入观看</button>' +
                    '</td>' +
                    '</tr>'
                );
            });

            body.innerHTML = rows.length ? rows.join('') : '<tr><td colspan="6">当前无活跃对局桌子</td></tr>';
            Admin.msg('tablesMsg', '共 ' + rows.length + ' 个活跃牌桌', true);

            // 若当前正在透视某桌，同步自动刷新透视面板
            if (currentInspectingTableId) {
                inspectTable(currentInspectingTableId);
            }
        }).catch(function (e) {
            Admin.msg('tablesMsg', '获取实时桌子列表失败：' + (e.message || '网络错误'), false);
        });
    }

    /**
     * 单桌全量上帝视角透视（展示状态码、各玩家手牌、底牌与牌山）。
     */
    function inspectTable(tableId) {
        currentInspectingTableId = tableId;
        var card = document.getElementById('tableInspectorCard');
        var titleEl = document.getElementById('inspectorTitle');
        var metaEl = document.getElementById('inspectorMeta');
        var contentEl = document.getElementById('inspectorContent');

        if (!card) return;
        card.style.display = 'block';
        titleEl.textContent = '牌桌透视详情 - 桌号 ' + tableId;
        contentEl.innerHTML = '<div style="color:var(--muted);padding:12px;">正在读取桌号 ' + tableId + ' 上帝视角数据...</div>';

        Admin.get('/tables/' + tableId).then(function (res) {
            if (res.code !== 0 || !res.table) {
                contentEl.innerHTML = '<div class="msg err">获取牌桌透视失败：' + (res.msg || '桌子不存在或已解散') + '</div>';
                return;
            }
            var d = res.table;
            var opText = d.currOpSeat >= 0 ? ('座位 ' + d.currOpSeat) : '无';
            metaEl.innerHTML = '玩法：<strong>' + d.gameTypeName + '</strong>（房间 ' + d.roomId + '） | 状态码：<strong style="color:var(--accent);">' + d.stateCode + ' (' + d.stateDesc + ')</strong> | 局数：<strong>第 ' + d.currentRound + ' / ' + d.totalRounds + ' 局</strong> | 当前操作位：<strong>' + opText + '</strong> | 状态持续：' + d.durationSeconds + ' 秒';

            var html = '';

            // 1. 玩家手牌区
            html += '<h3 style="font-size:15px;margin:12px 0 8px 0;color:var(--ink);">在桌玩家手牌透视（明牌）</h3>';
            html += '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px;margin-bottom:16px;">';

            var players = d.playerDetails || [];
            if (!players.length) {
                html += '<div style="color:var(--muted);padding:8px;">暂无在桌玩家</div>';
            } else {
                players.forEach(function (p) {
                    var isCurrentOp = (p.seat === d.currOpSeat);
                    var borderStyle = isCurrentOp ? 'border:2px solid var(--accent);' : 'border:1px solid #e4e7ed;';
                    var bgStyle = isCurrentOp ? 'background:#fbfdff;' : 'background:#fff;';

                    html += '<div class="card" style="margin:0;padding:12px;border-radius:6px;' + borderStyle + bgStyle + '">';
                    html += '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;border-bottom:1px dashed #ebeef5;padding-bottom:6px;">';
                    html += '<div><strong>座位 ' + p.seat + '</strong>：' + (p.nick || '玩家' + p.userId) + (p.robot ? ' <span style="font-size:11px;background:#eef1f6;padding:1px 4px;border-radius:3px;">机器人</span>' : '') + '</div>';
                    html += '<div style="font-size:12px;color:var(--muted);">积分: <strong>' + p.score + '</strong> | 手牌: <strong>' + p.cardCount + '</strong>张</div>';
                    html += '</div>';

                    // 手牌列表
                    html += '<div style="display:flex;flex-wrap:wrap;gap:4px;min-height:36px;align-items:center;">';
                    var cards = p.cards || [];
                    if (!cards.length) {
                        html += '<span style="color:var(--muted);font-size:12px;">（暂无手牌）</span>';
                    } else {
                        cards.forEach(function (c) {
                            html += renderCardBadge(c);
                        });
                    }
                    html += '</div>';
                    html += '</div>';
                });
            }
            html += '</div>';

            // 2. 牌堆 / 底牌 / 牌山透视区
            var deck = d.deckInfo || {};
            html += '<h3 style="font-size:15px;margin:16px 0 8px 0;color:var(--ink);">牌堆与底牌透视</h3>';
            html += '<div class="card" style="margin:0;padding:12px;background:#fafbfc;border:1px solid #e4e7ed;border-radius:6px;">';

            if (deck.type === 'ddz') {
                var landlordText = deck.landlordSeat >= 0 ? ('座位 ' + deck.landlordSeat) : '尚未确定';
                html += '<div style="margin-bottom:8px;font-size:13px;"><strong>斗地主底牌（共 3 张）</strong> | 当前地主归属：<strong>' + landlordText + '</strong></div>';
                html += '<div style="display:flex;flex-wrap:wrap;gap:6px;">';
                var bottoms = deck.bottomCards || [];
                if (!bottoms.length) {
                    html += '<span style="color:var(--muted);font-size:12px;">（暂无底牌数据）</span>';
                } else {
                    bottoms.forEach(function (c) {
                        html += renderCardBadge(c, true);
                    });
                }
                html += '</div>';
            } else if (deck.type === 'mj') {
                var dealerText = deck.dealerSeat >= 0 ? ('座位 ' + deck.dealerSeat) : '无';
                html += '<div style="margin-bottom:8px;font-size:13px;"><strong>麻将牌墙（剩余 ' + (deck.remainingCount || 0) + ' 张）</strong> | 庄家位：<strong>' + dealerText + '</strong>';
                if (deck.laiZiTile) {
                    html += ' | 赖子牌：' + renderCardBadge(deck.laiZiTile);
                }
                html += '</div>';

                var walls = deck.wallTiles || [];
                html += '<div style="font-size:12px;color:var(--muted);margin-bottom:4px;">牌墙余牌摸牌顺序（前 30 张预览）：</div>';
                html += '<div style="display:flex;flex-wrap:wrap;gap:4px;max-height:160px;overflow-y:auto;background:#fff;padding:8px;border:1px solid #ebeef5;border-radius:4px;">';
                if (!walls.length) {
                    html += '<span style="color:var(--muted);font-size:12px;">牌墙已摸空（流局或已胡牌）</span>';
                } else {
                    walls.slice(0, 30).forEach(function (c, idx) {
                        html += '<span style="display:inline-flex;align-items:center;margin:1px;">' +
                            '<span style="font-size:10px;color:var(--muted);margin-right:2px;">' + (idx + 1) + '.</span>' +
                            renderCardBadge(c) +
                            '</span>';
                    });
                    if (walls.length > 30) {
                        html += '<span style="color:var(--muted);font-size:12px;align-self:center;margin-left:6px;">...等共 ' + walls.length + ' 张</span>';
                    }
                }
                html += '</div>';
            } else if (deck.type === 'tractor') {
                var bankerText = deck.bankerSeat >= 0 ? ('座位 ' + deck.bankerSeat) : '无';
                html += '<div style="margin-bottom:8px;font-size:13px;"><strong>拖拉机底牌（共 8 张）</strong> | 庄家位：<strong>' + bankerText + '</strong></div>';
                html += '<div style="display:flex;flex-wrap:wrap;gap:6px;">';
                var trBottoms = deck.bottomCards || [];
                if (!trBottoms.length) {
                    html += '<span style="color:var(--muted);font-size:12px;">（暂无底牌数据）</span>';
                } else {
                    trBottoms.forEach(function (c) {
                        html += renderCardBadge(c, true);
                    });
                }
                html += '</div>';
            } else if (deck.type === 'pdk') {
                html += '<div style="margin-bottom:8px;font-size:13px;"><strong>跑得快发牌余牌（剩余 ' + (deck.remainingCount || 0) + ' 张）</strong></div>';
                html += '<div style="display:flex;flex-wrap:wrap;gap:6px;">';
                var pdkPool = deck.remainingCards || [];
                if (!pdkPool.length) {
                    html += '<span style="color:var(--muted);font-size:12px;">全量牌已发完</span>';
                } else {
                    pdkPool.forEach(function (c) {
                        html += renderCardBadge(c);
                    });
                }
                html += '</div>';
            } else {
                html += '<div style="color:var(--muted);font-size:12px;">该玩法无特殊牌堆数据</div>';
            }
            html += '</div>';

            contentEl.innerHTML = html;
        }).catch(function (err) {
            contentEl.innerHTML = '<div class="msg err">透视请求失败：' + (err.message || '网络异常') + '</div>';
        });
    }

    /**
     * 辅助渲染卡片或麻将小徽章。
     */
    function renderCardBadge(c, highlight) {
        if (!c) return '';
        var isRed = (c.color === 'red');
        var isGreen = (c.color === 'green');
        var isBlue = (c.color === 'blue');
        var isPurple = (c.color === 'purple');

        var textColor = isRed ? '#d32f2f' : (isGreen ? '#2e7d32' : (isBlue ? '#1976d2' : (isPurple ? '#7b1fa2' : '#2c3e50')));
        var borderColor = isRed ? '#ffcdd2' : (isGreen ? '#c8e6c9' : (isBlue ? '#bbdefb' : (isPurple ? '#e1bee7' : '#dcdfe6')));
        var bgColor = highlight ? '#fff8e1' : '#ffffff';

        return '<span style="display:inline-flex;align-items:center;padding:2px 6px;margin:2px;font-size:12px;font-weight:bold;color:' + textColor + ';background:' + bgColor + ';border:1px solid ' + borderColor + ';border-radius:4px;box-shadow:0 1px 2px rgba(0,0,0,0.04);">' +
            c.name +
            '<span style="font-size:10px;color:#909399;margin-left:3px;font-weight:normal;">#' + c.id + '</span>' +
            '</span>';
    }

    function closeInspector() {
        currentInspectingTableId = null;
        var card = document.getElementById('tableInspectorCard');
        if (card) card.style.display = 'none';
    }

    function refreshCurrentInspector() {
        if (currentInspectingTableId) {
            inspectTable(currentInspectingTableId);
        }
    }

    w.loadTables = load;
    w.inspectTable = inspectTable;
    w.closeInspector = closeInspector;
    w.refreshCurrentInspector = refreshCurrentInspector;

    w.watchLiveTable = function (tableId) {
        Admin.msg('tablesMsg', '正在进入桌 ' + tableId + '…');
        ReplayPlayer.waiting(tableId);
        watchGeneration++;
        watchTable(tableId, 0, watchGeneration);
    };

    w.changeRobotRules = function () {
        if (!select) return;
        var roomId = Number(select.value);
        var mjRules = document.getElementById('robotMahjongRules');
        if (mjRules) {
            mjRules.style.display = roomId === 9001 ? 'flex' : 'none';
        }
    };

    w.startRobotMatch = function () {
        var button = document.getElementById('robotMatchStart');
        if (button) button.disabled = true;
        var payload = {
            roomId: Number(select.value),
            totalRounds: Number(document.getElementById('robotMatchRounds').value),
            baseScore: Number(document.getElementById('robotBaseScore').value),
            maxFan: Number(document.getElementById('robotMaxFan').value),
            allowChi: checked('robotAllowChi'),
            allowDianPao: checked('robotAllowDianPao'),
            allowGang: checked('robotAllowGang'),
            allowSevenPairs: checked('robotAllowSevenPairs'),
            allowMultiHu: checked('robotAllowMultiHu')
        };
        Admin.msg('robotMatchMsg', '正在创建机器人测试…');
        Admin.post('/robot-matches', payload).then(function (data) {
            if (button) button.disabled = false;
            if (data.code !== 0) return Admin.msg('robotMatchMsg', data.msg || '启动失败', false);
            Admin.msg('robotMatchMsg', '测试已启动：桌号 ' + data.tableId + '，正在进入实时对局。', true);
            load();
            ReplayPlayer.waiting(data.tableId);
            watchGeneration++;
            watchTable(data.tableId, 0, watchGeneration);
        }).catch(function () {
            if (button) button.disabled = false;
            Admin.msg('robotMatchMsg', '网络错误', false);
        });
    };

    w.changeRobotRules();
    document.addEventListener('replay-player-close', function () {
        watchGeneration++;
        clearTimeout(pollTimer);
    });
})(window);
