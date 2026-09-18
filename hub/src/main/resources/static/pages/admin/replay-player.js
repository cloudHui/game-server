/**
 * 全仿真对局回放播放器 (ReplayPlayer)
 *
 * 架构分层：
 * 1. 播放调度控制器：处理自动播放、步进、暂停、快进、实时轮询及快捷键；
 * 2. DOM 缓存管理层：只在 open 时缓存所有节点引用，消除频繁 render 时的 DOM 遍历；
 * 3. 玩法渲染器管线：
 *    - SeatRenderer: 座位光晕、昵称、角色徽章（地主/农民/庄家）、叫分气泡；
 *    - PokerRenderer: 门前出牌区（played-cards）、不出气泡、顶部三张底牌；
 *    - MahjongRenderer: 手牌摸牌隔离（.tile-drawn）、副露组（吃碰杠）、中央堂子牌池；
 * 4. 视角与全屏控制器：上帝全明牌视角、任意玩家主视角切换、横屏影院模式。
 */
(function (global) {
    'use strict';

    // 播放器内部状态
    var timer = null;          // 自动播放定时器
    var liveTimer = null;      // 实时对局轮询定时器
    var liveUrl = '';          // 实时对局更新地址
    var model = null;          // 当前回放数据模型
    var viewSeat = 0;          // 当前主视角座位索引 (0~N-1)
    var revealOpponents = true;// 是否开启上帝全明牌视角 (默认开启)
    var logTimer = null;       // 审计日志抽屉自动隐藏计时器

    // 牌桌 DOM 节点缓存池 (避免每次 render 反复进行 DOM 查询)
    var dom = null;

    /**
     * 转义 HTML 特殊字符，防范 XSS 注入。
     *
     * @param {string} str 待转义字符
     * @returns {string} 转义后安全字符串
     */
    function esc(str) {
        return String(str || '').replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    /**
     * 判断当前模型是否为麻将玩法。
     *
     * @returns {boolean} true 为麻将，false 为扑克
     */
    function isMj() {
        return model && ReplayModel.isMahjong(model);
    }

    /**
     * 创建单张牌/牌瓦的 DOM 节点。
     *
     * @param {number} id 牌唯一ID
     * @param {Object} [opt] 渲染选项
     * @param {boolean} [opt.large] 是否以大尺寸主视角渲染
     * @returns {HTMLElement} 牌面节点
     */
    function createCardElement(id, opt) {
        opt = opt || {};
        if (isMj()) {
            if (global.MahjongTile) {
                return global.MahjongTile.createTileEl(id, { small: !opt.large });
            }
        } else {
            if (global.TableSeatView && global.TableSeatView.createPokerCard) {
                return global.TableSeatView.createPokerCard(id, opt);
            }
            if (global.PokerCard) {
                var box = document.createElement('div');
                box.className = 'card';
                box.appendChild(global.PokerCard.createCardFace(id));
                return box;
            }
        }
        var fallback = document.createElement('span');
        fallback.textContent = String(id);
        return fallback;
    }

    /**
     * 在打开回放时初始化并缓存牌桌所有 DOM 节点。
     * 消除逐帧 render 时的 document.getElementById / querySelectorAll 开销。
     */
    function cacheDomElements() {
        var slots = ['top', 'left', 'right', 'bottom'];
        var seatsMap = {};
        var playedMap = {};

        for (var i = 0; i < slots.length; i++) {
            var slot = slots[i];
            var seatEl = document.querySelector('#replayTable [data-slot="' + slot + '"]');
            if (seatEl) {
                seatsMap[slot] = {
                    el: seatEl,
                    name: seatEl.querySelector('.name'),
                    roleBadge: seatEl.querySelector('.role-badge'),
                    countBadge: seatEl.querySelector('.tile-count-badge'),
                    bidBubble: seatEl.querySelector('.bid-bubble'),
                    hand: seatEl.querySelector('.replay-hand'),
                    exposed: seatEl.querySelector('.replay-exposed')
                };
            }
            var playedEl = document.getElementById('played' + slot.charAt(0).toUpperCase() + slot.slice(1));
            if (playedEl) {
                playedMap[slot] = playedEl;
            }
        }

        dom = {
            table: document.getElementById('replayTable'),
            roomMeta: document.getElementById('replayRoomMeta'),
            specialCards: document.getElementById('replaySpecialCards'),
            seats: seatsMap,
            played: playedMap,
            mjCenter: document.getElementById('replayMjCenter'),
            mjDiscards: document.getElementById('replayMjDiscards'),
            actionToast: document.getElementById('replayActionToast'),
            range: document.getElementById('replayRange'),
            stepLabel: document.getElementById('replayStep'),
            playBtn: document.getElementById('replayPlay'),
            viewSeatSel: document.getElementById('replayViewSeat'),
            codeLabel: document.getElementById('replayCodeLabel'),
            waiting: document.getElementById('replayWaiting'),
            waitingText: document.getElementById('replayWaitingText'),
            logMask: document.getElementById('replayLogMask'),
            logList: document.getElementById('replayLog'),
            rawPre: document.getElementById('replayRaw'),
            playerRoot: document.getElementById('replayPlayer'),
            detailCard: document.getElementById('replayDetailCard')
        };
    }

    // =========================================================================
    // 渲染管线分层：SeatRenderer / PokerRenderer / MahjongRenderer
    // =========================================================================

    /**
     * 座位通用信息渲染器。
     */
    var SeatRenderer = {
        /**
         * 渲染指定座位的玩家信息、状态光晕、角色徽章及叫分气泡。
         *
         * @param {Object} seatNode 缓存的座位 DOM 节点集合
         * @param {number} seatIndex 座位索引
         * @param {Object} snap 当前步骤状态快照
         */
        renderInfo: function (seatNode, seatIndex, snap) {
            seatNode.el.hidden = false;
            seatNode.el.setAttribute('data-seat', String(seatIndex));

            // 1. 轮到行动时光晕高亮
            seatNode.el.classList.toggle('active', snap.nextSeat === seatIndex);

            // 2. 昵称
            if (seatNode.name) {
                seatNode.name.textContent = '座' + seatIndex + ' ' + (model.players[seatIndex] || '');
            }

            // 3. 剩余手牌张数
            var handList = snap.hands[seatIndex] || [];
            if (seatNode.countBadge) {
                seatNode.countBadge.textContent = handList.length + '张';
            }

            // 4. 角色徽章 (斗地主地主/农民，麻将庄家)
            if (seatNode.roleBadge) {
                if (!isMj() && snap.landlordSeat >= 0) {
                    var isLandlord = (snap.landlordSeat === seatIndex);
                    seatNode.roleBadge.className = 'role-badge ' + (isLandlord ? 'landlord' : 'farmer');
                    seatNode.roleBadge.textContent = isLandlord ? '地主' : '农民';
                    seatNode.roleBadge.style.display = 'inline-block';
                } else if (isMj() && model.meta.dealerSeat === seatIndex) {
                    seatNode.roleBadge.className = 'role-badge dealer';
                    seatNode.roleBadge.textContent = '庄';
                    seatNode.roleBadge.style.display = 'inline-block';
                } else {
                    seatNode.roleBadge.style.display = 'none';
                }
            }

            // 5. 叫分/抢地主浮动气泡
            if (seatNode.bidBubble) {
                var bidText = snap.bids[seatIndex];
                if (bidText) {
                    seatNode.bidBubble.textContent = bidText;
                    seatNode.bidBubble.style.display = 'block';
                } else {
                    seatNode.bidBubble.style.display = 'none';
                }
            }
        }
    };

    /**
     * 扑克玩法渲染器 (斗地主、跑得快、拖拉机)。
     */
    var PokerRenderer = {
        /**
         * 渲染顶部底牌展示区。
         *
         * @param {HTMLElement} container 容器元素
         * @param {number[]} bottomCards 底牌ID列表
         */
        renderBottomCards: function (container, bottomCards) {
            if (!container) return;
            container.innerHTML = '';
            if (!bottomCards || !bottomCards.length) return;

            var label = document.createElement('span');
            label.className = 'replay-special-label';
            label.textContent = '底牌:';
            container.appendChild(label);
            for (var i = 0; i < bottomCards.length; i++) {
                container.appendChild(createCardElement(bottomCards[i]));
            }
        },

        /**
         * 渲染扑克手牌。
         *
         * @param {HTMLElement} handEl 手牌容器
         * @param {number[]} handList 手牌ID列表
         * @param {boolean} isBottom 是否为主视角
         */
        renderHand: function (handEl, handList, isBottom) {
            if (!handEl) return;
            handEl.innerHTML = '';
            var showFace = isBottom || revealOpponents;

            if (showFace) {
                for (var i = 0; i < handList.length; i++) {
                    handEl.appendChild(createCardElement(handList[i], { large: isBottom }));
                }
            } else {
                if (global.TableSeatView && global.TableSeatView.renderBacks) {
                    global.TableSeatView.renderBacks(handEl, handList.length, 'poker');
                }
            }
        },

        /**
         * 渲染门前独立出牌区与“不出”提示。
         *
         * @param {Object} playedMap 缓存的出牌区映射
         * @param {Object} snap 当前步骤快照
         */
        renderPlayed: function (playedMap, snap) {
            var slots = Object.keys(playedMap);
            for (var i = 0; i < slots.length; i++) {
                var el = playedMap[slots[i]];
                el.innerHTML = '';
                el.style.display = 'flex';
            }

            for (var s = 0; s < model.seatCount; s++) {
                var seatSlot = ReplayTableView.slotFor(s, viewSeat, model.seatCount);
                var targetEl = playedMap[seatSlot];
                if (!targetEl) continue;

                var playedItem = snap.played[s];
                if (!playedItem) continue;

                if (playedItem === 'PASS') {
                    var hint = document.createElement('span');
                    hint.className = 'pass-hint';
                    hint.textContent = '不出';
                    targetEl.appendChild(hint);
                } else if (Array.isArray(playedItem)) {
                    for (var p = 0; p < playedItem.length; p++) {
                        targetEl.appendChild(createCardElement(playedItem[p], { large: false }));
                    }
                }
            }
        }
    };

    /**
     * 麻将玩法渲染器 (卡五星、荆门麻将等)。
     */
    var MahjongRenderer = {
        /**
         * 渲染顶部翻牌与赖子。
         *
         * @param {HTMLElement} container 特殊牌容器
         * @param {Object} meta 牌桌元数据
         */
        renderLaiZi: function (container, meta) {
            if (!container) return;
            container.innerHTML = '';
            if (meta.flipTileId > 0) {
                var fLabel = document.createElement('span');
                fLabel.className = 'replay-special-label';
                fLabel.textContent = '翻牌:';
                container.appendChild(fLabel);
                container.appendChild(createCardElement(meta.flipTileId, { large: false }));
            }
            if (meta.laiZiTileId > 0) {
                var lLabel = document.createElement('span');
                lLabel.className = 'replay-special-label';
                lLabel.textContent = '赖子:';
                container.appendChild(lLabel);
                container.appendChild(createCardElement(meta.laiZiTileId, { large: false }));
            }
        },

        /**
         * 渲染麻将手牌与副露（最新摸牌在右端留空隔离）。
         *
         * @param {HTMLElement} handEl 手牌容器
         * @param {HTMLElement} exposedEl 副露容器
         * @param {number[]} handList 手牌列表
         * @param {number} seatIndex 座位索引
         * @param {boolean} isBottom 是否为主视角
         * @param {Object} snap 当前步骤快照
         */
        renderHandAndExposed: function (handEl, exposedEl, handList, seatIndex, isBottom, snap) {
            if (!handEl) return;
            handEl.innerHTML = '';
            if (exposedEl) exposedEl.innerHTML = '';

            var showFace = isBottom || revealOpponents;
            if (showFace) {
                var drawn = snap.drawnTile;
                var isDrawnSeat = (drawn && drawn.seat === seatIndex);

                for (var i = 0; i < handList.length; i++) {
                    var tileId = handList[i];
                    var isDrawnTile = isDrawnSeat && (i === handList.length - 1) && (tileId === drawn.tileId);
                    var tileNode = createCardElement(tileId, { large: isBottom });
                    // 设计原因：刚摸到的牌在右侧留有间隙隔离展示，还原真实打牌看牌体验
                    if (isDrawnTile) {
                        tileNode.classList.add('tile-drawn');
                    }
                    handEl.appendChild(tileNode);
                }
            } else {
                if (global.TableSeatView && global.TableSeatView.renderBacks) {
                    global.TableSeatView.renderBacks(handEl, handList.length, 'mahjong');
                }
            }

            // 渲染副露组合
            if (exposedEl && global.TableSeatView && global.TableSeatView.renderMahjongSets) {
                global.TableSeatView.renderMahjongSets(exposedEl, snap.exposedSets[seatIndex] || [], {
                    ownerSeat: seatIndex,
                    revealAnGang: false
                });
            }
        },

        /**
         * 渲染中央堂子弃牌池。
         *
         * @param {HTMLElement} centerEl 牌池容器
         * @param {HTMLElement} discardsEl 牌池牌面区域
         * @param {Array} discards 弃牌列表
         */
        renderDiscards: function (centerEl, discardsEl, discards) {
            if (!centerEl || !discardsEl) return;
            centerEl.style.display = 'flex';
            discardsEl.innerHTML = '';

            var list = discards || [];
            for (var i = 0; i < list.length; i++) {
                var tileNode = createCardElement(list[i].id, { large: false });
                if (i === list.length - 1) {
                    tileNode.classList.add('last-discard');
                }
                discardsEl.appendChild(tileNode);
            }
        }
    };

    // =========================================================================
    // 渲染总调度引擎
    // =========================================================================

    /**
     * 核心渲染主流程：根据快照数据更新整张牌桌。
     *
     * @param {Object} snap 当前步骤快照
     */
    function render(snap) {
        if (!model || !snap || !dom) return;

        // 1. 顶部元数据
        if (dom.roomMeta) {
            var meta = model.meta || {};
            dom.roomMeta.textContent = (model.type || '对局')
                + (meta.tableId ? ' · 桌号 ' + meta.tableId : '')
                + (meta.round ? ' · 第 ' + meta.round + ' 局' : '');
        }

        // 2. 顶部特殊牌区
        if (isMj()) {
            MahjongRenderer.renderLaiZi(dom.specialCards, model.meta || {});
        } else {
            PokerRenderer.renderBottomCards(dom.specialCards, snap.bottomCards);
        }

        // 3. 各座位隐藏初始化
        var slotKeys = Object.keys(dom.seats);
        for (var k = 0; k < slotKeys.length; k++) {
            dom.seats[slotKeys[k]].el.hidden = true;
        }

        // 4. 各座位手牌与信息呈现
        for (var s = 0; s < model.seatCount; s++) {
            var slot = ReplayTableView.slotFor(s, viewSeat, model.seatCount);
            var seatNode = dom.seats[slot];
            if (!seatNode) continue;

            SeatRenderer.renderInfo(seatNode, s, snap);
            var isBottom = (slot === 'bottom');
            var handList = snap.hands[s] || [];

            if (isMj()) {
                MahjongRenderer.renderHandAndExposed(seatNode.hand, seatNode.exposed, handList, s, isBottom, snap);
            } else {
                if (seatNode.exposed) seatNode.exposed.innerHTML = '';
                PokerRenderer.renderHand(seatNode.hand, handList, isBottom);
            }
        }

        // 5. 扑克门前出牌 vs 麻将中央牌池分流渲染
        if (isMj()) {
            // 麻将关闭扑克门前出牌区，开启中央堂子
            var pKeys = Object.keys(dom.played);
            for (var p = 0; p < pKeys.length; p++) {
                dom.played[pKeys[p]].style.display = 'none';
            }
            MahjongRenderer.renderDiscards(dom.mjCenter, dom.mjDiscards, snap.discards);
        } else {
            // 扑克关闭麻将中央牌池，开启门前出牌区
            if (dom.mjCenter) dom.mjCenter.style.display = 'none';
            PokerRenderer.renderPlayed(dom.played, snap);
        }

        // 6. 底部轻量动作提示浮条
        if (dom.actionToast) {
            if (model.pos < 0) {
                dom.actionToast.textContent = '初始发牌就绪';
            } else {
                var evt = model.events[model.pos];
                dom.actionToast.textContent = evt
                    ? ('#' + evt.index + ' ' + (evt.time ? '[' + evt.time + '] ' : '') + evt.text)
                    : '';
            }
        }

        // 7. 更新进度条与计数指示
        if (dom.range) dom.range.value = model.pos + 1;
        if (dom.stepLabel) dom.stepLabel.textContent = (model.pos + 1) + ' / ' + model.events.length;

        // 8. 审计日志高亮同步
        updateLogActiveLine();
    }

    /**
     * 定位到指定事件步骤，依托快照实现 O(1) 立即呈现。
     *
     * @param {number} pos 步骤索引 (-1 到 events.length - 1)
     */
    function seek(pos) {
        if (!model) return;
        var snap = ReplayModel.seek(model, pos);
        render(snap);
    }

    /**
     * 播放 / 暂停切换。
     */
    function toggle() {
        if (!model) return;
        if (timer) {
            clearInterval(timer);
            timer = null;
            if (dom && dom.playBtn) dom.playBtn.textContent = '播放';
            return;
        }
        if (dom && dom.playBtn) dom.playBtn.textContent = '暂停';

        var speedSel = document.getElementById('replaySpeed');
        var speedRate = speedSel ? Number(speedSel.value) || 1 : 1;
        var intervalMs = Math.max(100, Math.round(700 / speedRate));

        timer = setInterval(function () {
            if (model.pos >= model.events.length - 1) {
                toggle();
                return;
            }
            seek(model.pos + 1);
        }, intervalMs);
    }

    /**
     * 打开并初始化回放数据。
     *
     * @param {Object} data 后端返回的回放结构
     * @param {string} [url] 实时对局轮询地址
     */
    function open(data, url) {
        if (timer) { clearInterval(timer); timer = null; }

        // 解析并预推演全量快照
        model = ReplayModel.parse(data.content, data.replayCode || (data.date + '/' + data.name), data.gameType);

        // 首次或重新打开时重建 DOM 缓存
        cacheDomElements();

        if (dom.playerRoot) dom.playerRoot.classList.add('active');
        if (dom.waiting) dom.waiting.hidden = true;
        if (dom.table) dom.table.classList.toggle('replay-mahjong', isMj());
        if (dom.range) {
            dom.range.max = model.events.length;
            dom.range.value = 0;
        }
        if (dom.codeLabel) dom.codeLabel.textContent = model.code;

        // 初始化主视角下拉选单
        if (dom.viewSeatSel) {
            dom.viewSeatSel.innerHTML = '';
            for (var s = 0; s < model.seatCount; s++) {
                var opt = document.createElement('option');
                opt.value = String(s);
                opt.textContent = '主视角: 座' + s + ' ' + (model.players[s] || '');
                dom.viewSeatSel.appendChild(opt);
            }
            viewSeat = Math.min(viewSeat, model.seatCount - 1);
            dom.viewSeatSel.value = String(viewSeat);
        }

        // 填充审计日志列表
        if (dom.rawPre) dom.rawPre.textContent = model.content;
        if (dom.logList) {
            dom.logList.innerHTML = model.events.map(function (e) {
                return '<div>#' + e.index + ' ' + esc(e.time) + ' ' + esc(e.text) + '</div>';
            }).join('');
        }

        seek(-1);
        armLive(url, data.status);
    }

    /**
     * 实时对局轮询探活。
     *
     * @param {string} url 轮询链接
     * @param {string} status 状态文本
     */
    function armLive(url, status) {
        if (!url || status === '已结算') {
            if (liveTimer) { clearInterval(liveTimer); liveTimer = null; }
            liveUrl = '';
            return;
        }
        if (liveTimer && liveUrl === url) return;
        if (liveTimer) clearInterval(liveTimer);
        liveUrl = url;

        liveTimer = setInterval(function () {
            fetch(liveUrl).then(function (r) { return r.json(); }).then(function (d) {
                if (d.code !== 0 || !model || d.content === model.content) return;
                var oldPos = model.pos;
                var wasEnd = oldPos >= model.events.length - 1;
                var wasPlaying = !!timer;
                open(d, liveUrl);
                seek(wasEnd ? model.events.length - 1 : Math.min(oldPos, model.events.length - 1));
                if (wasPlaying) toggle();
            }).catch(function () {});
        }, 2000);
    }

    /**
     * 切换主视角座位。
     *
     * @param {number} seat 目标座位索引
     */
    function changeViewSeat(seat) {
        if (!model) return;
        viewSeat = Math.max(0, Math.min(seat, model.seatCount - 1));
        var snap = model.snapshots[model.pos + 1];
        render(snap);
    }

    /**
     * 切换上帝全明牌 / 对手暗牌模式。
     */
    function toggleSpectatorMode() {
        revealOpponents = !revealOpponents;
        if (model) {
            var snap = model.snapshots[model.pos + 1];
            render(snap);
        }
    }

    /**
     * 追到回放最新一步。
     */
    function latest() {
        if (model) seek(model.events.length - 1);
    }

    /**
     * 关闭回放播放器并退出全屏。
     */
    function close() {
        if (timer) { clearInterval(timer); timer = null; }
        if (liveTimer) { clearInterval(liveTimer); liveTimer = null; }
        closeLog();
        liveUrl = '';
        model = null;

        if (dom && dom.detailCard) {
            dom.detailCard.style.display = 'none';
            dom.detailCard.classList.remove('replay-overlay');
        }
        if (dom && dom.playerRoot) dom.playerRoot.classList.remove('active');
        document.body.classList.remove('replay-watching');
        document.dispatchEvent(new CustomEvent('replay-player-close'));

        if (document.fullscreenElement && document.exitFullscreen) {
            document.exitFullscreen().catch(function () {});
        }
        if (screen.orientation && screen.orientation.unlock) {
            try { screen.orientation.unlock(); } catch (e) {}
        }
    }

    /**
     * 进入全屏横屏影院观看模式。
     */
    function theater() {
        if (dom && dom.detailCard) {
            dom.detailCard.style.display = 'block';
            dom.detailCard.classList.add('replay-overlay');
        }
        document.body.classList.add('replay-watching');
        var el = document.documentElement;
        if (el.requestFullscreen && !document.fullscreenElement) {
            el.requestFullscreen().catch(function () {});
        }
        if (screen.orientation && screen.orientation.lock) {
            screen.orientation.lock('landscape').catch(function () {});
        }
    }

    /**
     * 审计日志抽屉面板开关逻辑。
     */
    function armLog() {
        if (logTimer) clearTimeout(logTimer);
        logTimer = setTimeout(closeLog, 4000);
    }

    function openLog() {
        if (dom && dom.logMask) dom.logMask.classList.add('show');
        armLog();
    }

    function closeLog() {
        if (dom && dom.logMask) dom.logMask.classList.remove('show');
        if (logTimer) { clearTimeout(logTimer); logTimer = null; }
    }

    function toggleLog() {
        if (dom && dom.logMask && dom.logMask.classList.contains('show')) closeLog();
        else openLog();
    }

    function updateLogActiveLine() {
        if (!dom || !dom.logList) return;
        var logItems = dom.logList.children;
        for (var i = 0; i < logItems.length; i++) {
            var isCurrent = (i === model.pos);
            logItems[i].classList.toggle('current', isCurrent);
            if (isCurrent) logItems[i].scrollIntoView({ block: 'nearest' });
        }
    }

    /**
     * 安全复制回放码（兼容非 HTTPS 环境）。
     */
    function copyCode() {
        if (!model || !model.code) return;
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(model.code).catch(function () {
                window.prompt('请手动复制回放码:', model.code);
            });
        } else {
            window.prompt('请手动复制回放码:', model.code);
        }
    }

    /**
     * 实时观战等待状态入口。
     *
     * @param {number|string} tableId 桌号
     */
    function waiting(tableId) {
        if (timer) { clearInterval(timer); timer = null; }
        if (liveTimer) { clearInterval(liveTimer); liveTimer = null; }
        model = null;
        cacheDomElements();
        theater();

        if (dom.playerRoot) dom.playerRoot.classList.add('active');
        if (dom.waiting) dom.waiting.hidden = false;
        if (dom.playBtn) dom.playBtn.textContent = '播放';
        if (dom.waitingText) dom.waitingText.textContent = '桌 ' + tableId + ' 已创建，等待首个回放事件…';
    }

    // 绑定键盘全局快捷键
    if (typeof document !== 'undefined') {
        document.addEventListener('keydown', function (e) {
            if (!document.body.classList.contains('replay-watching')) return;
            if (e.key === 'Escape') close();
            else if (e.key === ' ') { e.preventDefault(); toggle(); }
            else if (model && e.key === 'ArrowLeft') seek(model.pos - 1);
            else if (model && e.key === 'ArrowRight') seek(model.pos + 1);
            else if (e.key === 'End') latest();
        });
    }

    var ReplayPlayer = {
        open: function (data, url) { open(data, url); theater(); },
        waiting: waiting,
        toggle: toggle,
        seek: function (n) { seek(n); },
        move: function (n) { if (model) seek(model.pos + n); },
        latest: latest,
        viewSeat: changeViewSeat,
        toggleSpectatorMode: toggleSpectatorMode,
        close: close,
        copyCode: copyCode,
        toggleLog: toggleLog,
        closeLog: closeLog,
        _inspect: ReplayModel.inspect
    };

    if (global) {
        global.ReplayPlayer = ReplayPlayer;
    }
})(typeof window !== 'undefined' ? window : this);
