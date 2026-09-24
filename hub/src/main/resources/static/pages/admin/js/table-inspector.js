(function (w) {
    'use strict';

    var listTimer = null;
    var detailTimer = null;
    var currentTableId = null;
    var searchKeyword = '';

    /**
     * 外部列表：60 秒自动刷新。
     */
    function startListTimer() {
        stopListTimer();
        listTimer = setInterval(function () {
            if (!currentTableId) {
                loadTableList(false);
            }
        }, 60000);
    }

    function stopListTimer() {
        if (listTimer) {
            clearInterval(listTimer);
            listTimer = null;
        }
    }

    /**
     * 详情页面：1 秒高频自动刷新。
     */
    function startDetailTimer() {
        stopDetailTimer();
        detailTimer = setInterval(function () {
            if (currentTableId) {
                loadTableDetail(currentTableId, true);
            }
        }, 1000);
    }

    function stopDetailTimer() {
        if (detailTimer) {
            clearInterval(detailTimer);
            detailTimer = null;
        }
    }

    /**
     * 视图切换：列表视图 vs 详情视图。
     */
    function showListView() {
        currentTableId = null;
        stopDetailTimer();
        var listView = document.getElementById('inspectorListView');
        var detailView = document.getElementById('inspectorDetailView');
        if (listView) listView.style.display = 'block';
        if (detailView) detailView.style.display = 'none';
        startListTimer();
        loadTableList(true);
    }

    function showDetailView(tableId) {
        currentTableId = tableId;
        stopListTimer();
        var listView = document.getElementById('inspectorListView');
        var detailView = document.getElementById('inspectorDetailView');
        if (listView) listView.style.display = 'none';
        if (detailView) detailView.style.display = 'block';
        loadTableDetail(tableId, false);
        startDetailTimer();
    }

    /**
     * 加载牌桌列表（支持桌号搜索过滤）。
     */
    function loadTableList(isManual) {
        var query = searchKeyword ? ('?tableId=' + encodeURIComponent(searchKeyword)) : '';
        Admin.get('/tables' + query).then(function (data) {
            var body = document.getElementById('tableBody');
            var meta = document.getElementById('tablesMeta');
            if (data.code !== 0) {
                if (body) body.innerHTML = '<tr><td colspan="6">' + (data.msg || '加载失败') + '</td></tr>';
                return;
            }
            if (meta) {
                meta.textContent = '在线用户 ' + (data.onlineUsers || 0) + ' | 列表每 60 秒自动刷新';
            }
            var tables = data.tables || data.rooms || [];
            var rows = [];
            tables.forEach(function (t) {
                var gameName = t.gameTypeName || ('玩法' + t.gameType);
                var stateDesc = t.stateDesc || '未知状态';
                var stateCode = t.stateCode !== undefined ? t.stateCode : '-';
                var roundText = (t.currentRound || 1) + ' / ' + (t.totalRounds || 1) + ' 局';
                var playersText = (t.players && t.players.length) ? t.players.join('； ') : (t.playerCount + ' 人');

                rows.push(
                    '<tr>' +
                    '<td><strong>' + t.tableId + '</strong></td>' +
                    '<td><span style="color:var(--accent);font-weight:bold;">' + gameName + '</span></td>' +
                    '<td><span style="background:#eef1f6;padding:2px 8px;border-radius:4px;font-size:12px;font-weight:bold;color:var(--ink);">码 ' + stateCode + '：' + stateDesc + '</span></td>' +
                    '<td>' + roundText + '</td>' +
                    '<td style="font-size:13px;color:var(--muted);max-width:320px;word-break:break-all;">' + playersText + '</td>' +
                    '<td>' +
                    '<button class="btn btn-sm" style="padding:4px 10px;margin-right:6px;" onclick="TableInspector.openDetail(' + t.tableId + ')">进入详情 (1s刷新)</button>' +
                    '<button class="btn btn-ghost btn-sm" style="padding:4px 10px;" onclick="watchLiveTable(' + t.tableId + ')">观看</button>' +
                    '</td>' +
                    '</tr>'
                );
            });
            if (body) {
                body.innerHTML = rows.length ? rows.join('') : '<tr><td colspan="6">当前无匹配的活跃牌桌</td></tr>';
            }
            if (isManual) {
                Admin.msg('tablesMsg', '检索完成，共 ' + rows.length + ' 个活跃牌桌', true);
            }
        }).catch(function (e) {
            Admin.msg('tablesMsg', '获取牌桌列表失败：' + (e.message || '网络错误'), false);
        });
    }

    /**
     * 加载指定单桌的上帝视角透视详情。
     */
    function loadTableDetail(tableId, isSilent) {
        var contentEl = document.getElementById('inspectorDetailContent');
        var metaEl = document.getElementById('inspectorDetailMeta');
        var titleEl = document.getElementById('inspectorDetailTitle');
        if (titleEl) titleEl.textContent = '牌局实时透视 - 桌号 ' + tableId;

        Admin.get('/tables/' + tableId).then(function (res) {
            if (res.code !== 0 || !res.table) {
                if (contentEl) {
                    contentEl.innerHTML = '<div class="msg err">牌桌不存在或已结算解散（桌号 ' + tableId + '）</div>';
                }
                stopDetailTimer();
                return;
            }
            var d = res.table;
            if (metaEl) {
                var opText = d.currOpSeat >= 0 ? ('座位 ' + d.currOpSeat) : '无';
                metaEl.innerHTML = '玩法：<strong>' + d.gameTypeName + '</strong> | 状态：<strong style="color:var(--accent);">' + d.stateCode + ' (' + d.stateDesc + ')</strong> | 局数：<strong>' + d.currentRound + ' / ' + d.totalRounds + '</strong> | 当前出牌位：<strong>' + opText + '</strong> | 阶段用时：' + d.durationSeconds + '秒';
            }
            if (contentEl) {
                contentEl.innerHTML = renderDetailHtml(d);
            }
        }).catch(function (err) {
            if (!isSilent && contentEl) {
                contentEl.innerHTML = '<div class="msg err">透视拉取异常：' + (err.message || '网络错误') + '</div>';
            }
        });
    }

    /**
     * 渲染牌桌深度透视 HTML（玩家手牌、底牌与麻将换牌控制台）。
     */
    function renderDetailHtml(d) {
        var html = '';

        // 1. 玩家手牌区（后端已按游戏内规则排好序）
        html += '<h3 style="font-size:15px;margin:12px 0 8px 0;color:var(--ink);">玩家手牌透视（已对齐牌局理牌规范展示）</h3>';
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
                html += '<div><strong>座位 ' + p.seat + '</strong>：' + (p.nick || '玩家' + p.userId) + (p.robot ? ' <span style="font-size:11px;background:#eef1f6;padding:1px 4px;border-radius:3px;">机</span>' : '') + (isCurrentOp ? ' <span style="font-size:11px;background:var(--accent);color:#fff;padding:1px 4px;border-radius:3px;">出牌中</span>' : '') + '</div>';
                html += '<div style="font-size:12px;color:var(--muted);">积分: <strong>' + p.score + '</strong> | 牌数: <strong>' + p.cardCount + '</strong>张</div>';
                html += '</div>';
                html += '<div style="display:flex;flex-wrap:wrap;gap:4px;min-height:36px;align-items:center;">';
                var cards = p.cards || [];
                if (!cards.length) {
                    html += '<span style="color:var(--muted);font-size:12px;">（暂无手牌）</span>';
                } else {
                    cards.forEach(function (c) {
                        html += renderCardBadge(c);
                    });
                }
                html += '<div style="display:flex;justify-content:space-between;align-items:center;margin-top:8px;padding-top:6px;border-top:1px dashed #ebeef5;">';
                html += '<div style="font-size:11px;color:var(--muted);">针对性控牌:</div>';
                html += '<div style="display:flex;gap:4px;">';
                html += '<button class="btn btn-ghost btn-sm" style="padding:1px 6px;font-size:11px;" onclick="TableInspector.pickSeat(' + p.seat + ', \'draw\')">设下次摸牌</button>';
                html += '<button class="btn btn-ghost btn-sm" style="padding:1px 6px;font-size:11px;" onclick="TableInspector.pickSeat(' + p.seat + ', \'gang\')">设下次杠牌</button>';
                html += '</div></div>';
                html += '</div>';
            });
        }
        html += '</div>';

        // 2. 牌堆 / 底牌 / 牌山透视
        var deck = d.deckInfo || {};
        html += '<h3 style="font-size:15px;margin:16px 0 8px 0;color:var(--ink);">牌堆与底牌透视</h3>';
        html += '<div class="card" style="margin:0;padding:12px;background:#fafbfc;border:1px solid #e4e7ed;border-radius:6px;margin-bottom:16px;">';
        if (deck.type === 'ddz') {
            html += '<div style="margin-bottom:8px;font-size:13px;"><strong>斗地主底牌（共 3 张）</strong> | 地主座位：<strong>' + (deck.landlordSeat >= 0 ? deck.landlordSeat : '尚未确定') + '</strong></div>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:6px;">' + renderCardList(deck.bottomCards, true) + '</div>';
        } else if (deck.type === 'mj') {
            html += '<div style="margin-bottom:8px;font-size:13px;"><strong>麻将牌墙（剩余 ' + (deck.remainingCount || 0) + ' 张）</strong> | 庄家位：<strong>' + (deck.dealerSeat >= 0 ? deck.dealerSeat : '无') + '</strong>';
            if (deck.laiZiTile) {
                html += ' | 赖子牌：' + renderCardBadge(deck.laiZiTile);
            }
            html += '</div>';
            html += '<div style="font-size:12px;color:var(--muted);margin-bottom:4px;">牌墙余牌摸牌顺序（前 30 张预览）：</div>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:4px;max-height:160px;overflow-y:auto;background:#fff;padding:8px;border:1px solid #ebeef5;border-radius:4px;">';
            var walls = deck.wallTiles || [];
            if (!walls.length) {
                html += '<span style="color:var(--muted);font-size:12px;">牌墙已摸空</span>';
            } else {
                walls.slice(0, 30).forEach(function (c, idx) {
                    html += '<span style="display:inline-flex;align-items:center;margin:1px;"><span style="font-size:10px;color:var(--muted);margin-right:2px;">' + (idx + 1) + '.</span>' + renderCardBadge(c) + '</span>';
                });
                if (walls.length > 30) {
                    html += '<span style="color:var(--muted);font-size:12px;align-self:center;margin-left:6px;">...共 ' + walls.length + ' 张</span>';
                }
            }
            html += '</div>';
        } else if (deck.type === 'tractor') {
            html += '<div style="margin-bottom:8px;font-size:13px;"><strong>拖拉机底牌（共 8 张）</strong> | 庄家位：<strong>' + (deck.bankerSeat >= 0 ? deck.bankerSeat : '无') + '</strong></div>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:6px;">' + renderCardList(deck.bottomCards, true) + '</div>';
        } else if (deck.type === 'pdk') {
            html += '<div style="margin-bottom:8px;font-size:13px;"><strong>跑得快发牌余牌（剩余 ' + (deck.remainingCount || 0) + ' 张）</strong></div>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:6px;">' + renderCardList(deck.remainingCards) + '</div>';
        }
        html += '</div>';

        // 3. 麻将换牌调试控制台（仅麻将桌显示）
        if (d.gameType === 1) {
            html += renderMjCheatConsole(d);
        }
        return html;
    }

    /**
     * 麻将换牌调试控制台面板。
     */
    function renderMjCheatConsole(d) {
        var cheat = d.mjCheatStatus || {};
        var cues = cheat.pendingCues || [];
        var inventory = cheat.wallInventory || {};

        var cHtml = '<div class="card" style="margin:0;padding:14px;border:1px solid #dcdfe6;border-radius:6px;background:#fdfdfd;">';
        cHtml += '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">';
        cHtml += '<h3 style="font-size:15px;margin:0;color:var(--accent);">🀄 麻将控牌调试控制台（基于牌墙真实余牌调换，绝不伪造牌）</h3>';
        cHtml += '<button class="btn btn-ghost btn-sm" onclick="TableInspector.sendMjCmd(\'clear\')">清空所有指令</button>';
        cHtml += '</div>';

        // 挂起的控牌指令任务列表
        cHtml += '<div style="margin-bottom:12px;background:#f8f9fb;padding:10px 12px;border:1px solid #ebeef5;border-radius:4px;">';
        cHtml += '<div style="font-size:13px;font-weight:bold;margin-bottom:6px;color:var(--ink);">待执行的控牌任务队列（轮到该玩家摸牌时生效，未摸前不改手牌）：</div>';
        if (!cues.length) {
            cHtml += '<div style="font-size:12px;color:var(--muted);padding:4px 0;">当前暂无挂起指令（按牌墙自然顺序摸牌）</div>';
        } else {
            cHtml += '<div style="display:flex;flex-direction:column;gap:6px;">';
            cues.forEach(function (cue) {
                var cueWarn = cue.remainingInWall <= 0 ? ' <span style="color:#f56c6c;font-weight:bold;">(警告: 牌墙余牌已耗尽，即将作废)</span>' : ' <span style="color:#67c23a;">(牌墙余 ' + cue.remainingInWall + ' 张)</span>';
                cHtml += '<div style="display:flex;justify-content:space-between;align-items:center;background:#fff;padding:6px 10px;border:1px solid #dcdfe6;border-radius:4px;font-size:12px;">';
                cHtml += '<div>⏳ <strong>' + cue.targetSeatDesc + '</strong> 的 <strong>' + cue.actionDesc + '</strong> 预设为: <span style="color:var(--accent);font-weight:bold;">[' + cue.targetTileName + ']</span>' + cueWarn + '</div>';
                cHtml += '<div style="color:var(--muted);font-size:11px;">状态: ' + cue.status + '</div>';
                cHtml += '</div>';
            });
            cHtml += '</div>';
        }
        cHtml += '</div>';

        // 命令行输入
        cHtml += '<div style="display:flex;gap:8px;align-items:center;margin-bottom:10px;">';
        cHtml += '<input id="mjCheatCmdInput" class="tool-input" style="flex:1;" placeholder="支持指定目标玩家，如: 0 摸 1万 或 seat 1 gang 红中 或 draw 5条 或 clear" onkeydown="if(event.keyCode===13)TableInspector.submitCmdInput()">';
        cHtml += '<button class="btn btn-sm" onclick="TableInspector.submitCmdInput()">执行/挂起指令</button>';
        cHtml += '</div>';

        // 快捷牌选器（带牌墙剩余张数与无余牌置灰保护）
        cHtml += '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;">';
        cHtml += '<div style="font-size:12px;color:var(--muted);">牌墙现有未摸余牌快速点选（标注实时剩余张数，已耗尽则禁选）：</div>';
        cHtml += '<div style="font-size:11px;color:var(--muted);">牌墙总剩余: <strong>' + (cheat.totalWallRemaining || 0) + '</strong> 张</div>';
        cHtml += '</div>';

        cHtml += '<div style="display:flex;flex-wrap:wrap;gap:4px;max-height:120px;overflow-y:auto;background:#fff;padding:8px;border:1px solid #ebeef5;border-radius:4px;">';
        var quickTiles = [
            '1万','2万','3万','4万','5万','6万','7万','8万','9万',
            '1条','2条','3条','4条','5条','6条','7条','8条','9条',
            '1筒','2筒','3筒','4筒','5筒','6筒','7筒','8筒','9筒',
            '东风','南风','西风','北风','红中','发财','白板'
        ];
        quickTiles.forEach(function (tile) {
            var cnt = inventory[tile] || 0;
            if (cnt > 0) {
                cHtml += '<button class="btn btn-ghost btn-sm" style="padding:2px 6px;font-size:11px;" onclick="TableInspector.quickFill(\'' + tile + '\')">' + tile + ' <span style="color:#67c23a;font-weight:bold;">(' + cnt + ')</span></button>';
            } else {
                cHtml += '<button class="btn btn-ghost btn-sm" style="padding:2px 6px;font-size:11px;opacity:0.4;cursor:not-allowed;background:#f5f7fa;" disabled title="牌墙已无此牌">' + tile + ' (0)</button>';
            }
        });
        cHtml += '</div>';
        cHtml += '<div id="mjCheatFeedback" class="msg" style="margin-top:6px;"></div>';
        cHtml += '</div>';
        return cHtml;
    }

    function renderCardList(cards, highlight) {
        if (!cards || !cards.length) return '<span style="color:var(--muted);font-size:12px;">（暂无数据）</span>';
        return cards.map(function (c) { return renderCardBadge(c, highlight); }).join('');
    }

    function renderCardBadge(c, highlight) {
        if (!c) return '';
        var colors = {red: '#d32f2f', green: '#2e7d32', blue: '#1976d2', purple: '#7b1fa2', black: '#2c3e50'};
        var borders = {red: '#ffcdd2', green: '#c8e6c9', blue: '#bbdefb', purple: '#e1bee7', black: '#dcdfe6'};
        var textColor = colors[c.color] || colors.black;
        var borderColor = borders[c.color] || borders.black;
        var bg = highlight ? '#fff8e1' : '#ffffff';
        return '<span style="display:inline-flex;align-items:center;padding:2px 6px;margin:2px;font-size:12px;font-weight:bold;color:' + textColor + ';background:' + bg + ';border:1px solid ' + borderColor + ';border-radius:4px;box-shadow:0 1px 2px rgba(0,0,0,0.04);">' +
            c.name + '<span style="font-size:10px;color:#909399;margin-left:3px;font-weight:normal;">#' + c.id + '</span></span>';
    }

    /**
     * 发送换牌调试命令。
     */
    function sendMjCmd(cmd) {
        if (!currentTableId) return;
        Admin.post('/tables/' + currentTableId + '/mj-cheat', {command: cmd}).then(function (res) {
            var fb = document.getElementById('mjCheatFeedback');
            if (fb) {
                fb.textContent = res.message || res.msg || '执行成功';
                fb.className = 'msg ' + (res.code === 0 ? 'ok' : 'err');
            }
            loadTableDetail(currentTableId, true);
        }).catch(function (e) {
            var fb = document.getElementById('mjCheatFeedback');
            if (fb) {
                fb.textContent = '命令执行失败：' + (e.message || '网络错误');
                fb.className = 'msg err';
            }
        });
    }

    w.TableInspector = {
        loadList: function (manual) {
            var input = document.getElementById('tableSearchInput');
            if (input) searchKeyword = input.value.trim();
            loadTableList(manual);
        },
        resetSearch: function () {
            var input = document.getElementById('tableSearchInput');
            if (input) input.value = '';
            searchKeyword = '';
            loadTableList(true);
        },
        openDetail: function (tableId) {
            showDetailView(tableId);
        },
        backToList: function () {
            showListView();
        },
        refreshCurrent: function () {
            if (currentTableId) {
                loadTableDetail(currentTableId, false);
            }
        },
        sendMjCmd: sendMjCmd,
        submitCmdInput: function () {
            var input = document.getElementById('mjCheatCmdInput');
            if (input && input.value.trim()) {
                sendMjCmd(input.value.trim());
            }
        },
        pickSeat: function (seat, action) {
            var input = document.getElementById('mjCheatCmdInput');
            if (input) {
                input.value = 'seat ' + seat + ' ' + (action === 'gang' ? 'gang ' : 'draw ');
                input.focus();
            }
        },
        quickFill: function (tile) {
            var input = document.getElementById('mjCheatCmdInput');
            if (!input) return;
            var val = input.value.trim();
            if (val.startsWith('seat ') || /^\d+\s+/.test(val)) {
                var parts = val.split(/\s+/);
                if (parts.length >= 2) {
                    var act = parts[parts.length - 1];
                    if (act === 'draw' || act === 'gang' || act === '摸' || act === '杠') {
                        input.value = val + ' ' + tile;
                    } else {
                        parts[parts.length - 1] = tile;
                        input.value = parts.join(' ');
                    }
                }
            } else if (val.startsWith('draw ') || val.startsWith('gang ') || val.startsWith('摸 ') || val.startsWith('杠 ')) {
                var p = val.split(/\s+/);
                input.value = p[0] + ' ' + tile;
            } else {
                input.value = 'draw ' + tile;
            }
            input.focus();
        }
    };

    // 默认启动列表定时器
    startListTimer();

    // 监听 tab 切换事件，如果切离 tables 标签，停止所有定时器；切回时恢复列表定时器
    var origSwitchTab = w.switchTab;
    if (origSwitchTab) {
        w.switchTab = function (name) {
            if (name !== 'tables') {
                stopListTimer();
                stopDetailTimer();
            } else {
                if (currentTableId) {
                    startDetailTimer();
                } else {
                    startListTimer();
                    loadTableList(false);
                }
            }
            origSwitchTab(name);
        };
    }
})(window);
