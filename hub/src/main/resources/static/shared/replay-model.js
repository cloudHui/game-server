/**
 * 对局回放核心数据模型与状态机 (ReplayModel)
 *
 * 核心架构与职责分工：
 * 1. 文本解析层：提取桌号、局数、玩法、初始发牌、玩家信息及动作时间流；
 * 2. 状态机推演层：模块化事件处理器（叫分/底牌/出牌/过牌/摸牌/副露/扣底/审计）；
 * 3. 性能快照层：预先推演全生命周期状态，构建不可变快照数组（snapshots）；
 * 4. 检索访问层：提供 O(1) 复杂度的任意步骤状态检索（seek）。
 */
(function (global) {
    'use strict';

    /**
     * 深拷贝手牌映射表，防止状态推演过程中污染历史快照。
     *
     * @param {Object.<string, number[]>} hands 各座位手牌列表
     * @returns {Object.<string, number[]>} 独立副本
     */
    function cloneHands(hands) {
        var copy = {};
        if (!hands) return copy;
        var seats = Object.keys(hands);
        for (var i = 0; i < seats.length; i++) {
            var seat = seats[i];
            copy[seat] = (hands[seat] || []).slice();
        }
        return copy;
    }

    /**
     * 深拷贝通用字典对象（单层数组或基本类型）。
     *
     * @param {Object.<string, any>} obj 字典对象
     * @returns {Object.<string, any>} 独立副本
     */
    function shallowCloneObj(obj) {
        var copy = {};
        if (!obj) return copy;
        var keys = Object.keys(obj);
        for (var i = 0; i < keys.length; i++) {
            var k = keys[i];
            var v = obj[k];
            copy[k] = Array.isArray(v) ? v.slice() : v;
        }
        return copy;
    }

    /**
     * 深拷贝麻将副露集合列表（包括吃、碰、杠组合）。
     *
     * @param {Object.<string, Array>} exposedSets 副露映射表
     * @returns {Object.<string, Array>} 独立副本
     */
    function cloneExposedSets(exposedSets) {
        var copy = {};
        if (!exposedSets) return copy;
        var seats = Object.keys(exposedSets);
        for (var i = 0; i < seats.length; i++) {
            var seat = seats[i];
            var sets = exposedSets[seat] || [];
            copy[seat] = sets.map(function (item) {
                return {
                    kind: item.kind,
                    tiles: (item.tiles || []).slice(),
                    fromSeat: item.fromSeat,
                    claimTile: item.claimTile
                };
            });
        }
        return copy;
    }

    /**
     * 判断当前玩法是否属于麻将类（包括普通麻将、卡五星、荆门麻将等）。
     *
     * @param {Object} model 回放模型对象
     * @returns {boolean} true 为麻将类，false 为扑克类
     */
    function isMahjong(model) {
        return /麻将|卡五星/.test((model && model.type) || '');
    }

    /**
     * 手牌标准排序算法：
     * - 麻将类：按牌面数值升序单调排列；
     * - 扑克类：按点数升序（3~K、A、2、王），同点数按花色（方块、梅花、红桃、黑桃）升序。
     *
     * @param {Object} model 回放模型对象
     * @param {number[]} cards 待排序牌数组
     * @returns {number[]} 排序后原数组
     */
    function sortHand(model, cards) {
        if (!cards || !cards.length) return cards;
        cards.sort(function (a, b) {
            if (isMahjong(model)) {
                return a - b;
            }
            var valA = a % 100;
            var valB = b % 100;
            if (valA !== valB) {
                return valA - valB;
            }
            return Math.floor(a / 100) - Math.floor(b / 100);
        });
        return cards;
    }

    /**
     * 从指定座位的手牌中移除单张指定牌。
     *
     * @param {Object} state 动态推演状态容器
     * @param {number|string} seat 座位索引
     * @param {number} cardId 需移除的牌ID
     */
    function removeOne(state, seat, cardId) {
        var list = state.hands[seat];
        if (!list) return;
        var idx = list.indexOf(cardId);
        if (idx >= 0) {
            list.splice(idx, 1);
        }
    }

    // =========================================================================
    // 细化拆分：独立事件处理器（Single-Responsibility Action Handlers）
    // =========================================================================

    /**
     * 处理斗地主叫分/抢地主/不叫事件。
     *
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handleBid(state, text) {
        var m = text.match(/^座(\d+)\s*(叫分\s*\d+|不叫|抢地主|不抢)/);
        if (!m) return false;
        state.bids[m[1]] = m[2];
        return true;
    }

    /**
     * 处理斗地主底牌确定与归属事件。
     *
     * @param {Object} model 回放模型
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handleBottomCards(model, state, text) {
        var m = text.match(/^(?:=== 底牌 ===|座(\d+)\s*获得底牌\s*\[([\d,]+)\])/);
        if (!m || !m[1] || !m[2]) return false;

        var seat = m[1];
        var bottomList = m[2].split(',').map(Number);
        state.bottomCards = bottomList;
        state.landlordSeat = +seat;

        // 设计原因：底牌确定后需并入地主手牌并重新排序；同时叫分阶段结束，清除叫分气泡
        state.hands[seat] = (state.hands[seat] || []).concat(bottomList);
        sortHand(model, state.hands[seat]);
        state.bids = {};
        return true;
    }

    /**
     * 处理扑克与麻将的出牌事件（含超时出牌）。
     *
     * @param {Object} model 回放模型
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handlePlay(model, state, text) {
        var m = text.match(/^座(\d+)\s*(?:超时)?出牌\s*(\[[\d,]*\]|\d+)/);
        if (!m) return false;

        var playSeat = m[1];
        var playedIds = m[2][0] === '['
            ? (m[2].slice(1, -1) ? m[2].slice(1, -1).split(',').map(Number) : [])
            : [+m[2]];

        // 设计原因：若上一轮比牌已结束（产生本轮最大方），新出牌时需先清空门前上一轮旧牌
        if (state.needClearPlayed) {
            state.played = {};
            state.needClearPlayed = false;
        }

        // 从手牌扣减打出的牌
        for (var p = 0; p < playedIds.length; p++) {
            removeOne(state, playSeat, playedIds[p]);
        }

        if (isMahjong(model)) {
            // 麻将打出的牌进入中央牌池，并重置当前最新摸牌标记
            for (var d = 0; d < playedIds.length; d++) {
                state.discards.push({ seat: +playSeat, id: playedIds[d] });
            }
            state.drawnTile = null;
        } else {
            // 扑克打出的牌记录在对应门前
            state.played[playSeat] = playedIds;
        }
        return true;
    }

    /**
     * 处理扑克过牌（不出）事件。
     *
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handlePass(state, text) {
        var m = text.match(/^座(\d+)\s*(?:超时)?过/);
        if (!m) return false;
        state.played[m[1]] = 'PASS';
        return true;
    }

    /**
     * 处理麻将摸牌事件。
     *
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handleDraw(state, text) {
        var m = text.match(/^座(\d+)\s*摸牌\s*(\d+)/);
        if (!m) return false;
        var drawSeat = m[1];
        var tileId = +m[2];
        state.hands[drawSeat] = state.hands[drawSeat] || [];
        state.hands[drawSeat].push(tileId);

        // 设计原因：记录最新摸到的牌，渲染时在手牌最右端隔离 12px 展示
        state.drawnTile = { seat: +drawSeat, tileId: tileId };
        return true;
    }

    /**
     * 处理麻将吃/碰/明杠/暗杠/补杠副露事件。
     *
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handleMeld(state, text) {
        var m = text.match(/^座(\d+)\s*(吃|碰|明杠|暗杠|补杠)\s*(\[[\d,]*\]|\d+)/);
        if (!m) return false;

        var meldSeat = m[1];
        var kindName = m[2];
        var rawArg = m[3];
        var meldIds = rawArg[0] === '['
            ? rawArg.slice(1, -1).split(',').filter(Boolean).map(Number)
            : [+rawArg];

        var fromMatch = text.match(/\(座(\d+)出\)/);
        var fromSeat = fromMatch ? +fromMatch[1] : -1;
        var kindKey = { 吃: 'chi', 碰: 'peng', 明杠: 'mingGang', 暗杠: 'anGang', 补杠: 'buGang' }[kindName];

        state.exposedSets[meldSeat] = state.exposedSets[meldSeat] || [];

        if (kindName === '吃') {
            // 吃牌：手牌扣减2张，保留1张作为吃进的目标牌；牌池移走被吃的牌
            var lastDiscardId = state.discards.length ? state.discards[state.discards.length - 1].id : meldIds[0];
            var skipped = false;
            for (var ci = 0; ci < meldIds.length; ci++) {
                if (!skipped && meldIds[ci] === lastDiscardId) {
                    skipped = true;
                } else {
                    removeOne(state, meldSeat, meldIds[ci]);
                }
            }
            state.exposedSets[meldSeat].push({
                kind: kindKey,
                tiles: meldIds.slice(),
                fromSeat: fromSeat,
                claimTile: lastDiscardId
            });
            if (state.discards.length) state.discards.pop();
        } else if (kindName === '碰') {
            // 碰牌：从手牌扣减 2 张相同牌；牌池移走被碰的牌
            removeOne(state, meldSeat, meldIds[0]);
            removeOne(state, meldSeat, meldIds[0]);
            state.exposedSets[meldSeat].push({
                kind: kindKey,
                tiles: [meldIds[0], meldIds[0], meldIds[0]],
                fromSeat: fromSeat,
                claimTile: meldIds[0]
            });
            if (state.discards.length) state.discards.pop();
        } else if (kindName === '明杠') {
            // 明杠：从手牌扣减 3 张相同牌；牌池移走被杠的牌
            for (var mg = 0; mg < 3; mg++) removeOne(state, meldSeat, meldIds[0]);
            state.exposedSets[meldSeat].push({
                kind: kindKey,
                tiles: [meldIds[0], meldIds[0], meldIds[0], meldIds[0]],
                fromSeat: fromSeat,
                claimTile: meldIds[0]
            });
            if (state.discards.length) state.discards.pop();
        } else if (kindName === '暗杠') {
            // 暗杠：从手牌扣减 4 张相同牌，无来源座位
            for (var ag = 0; ag < 4; ag++) removeOne(state, meldSeat, meldIds[0]);
            state.exposedSets[meldSeat].push({
                kind: kindKey,
                tiles: [meldIds[0], meldIds[0], meldIds[0], meldIds[0]],
                fromSeat: -1,
                claimTile: meldIds[0]
            });
        } else if (kindName === '补杠') {
            // 补杠：从手牌扣减 1 张牌，升级已有的碰牌组合
            removeOne(state, meldSeat, meldIds[0]);
            var sets = state.exposedSets[meldSeat];
            var upgraded = false;
            for (var u = 0; u < sets.length; u++) {
                if (sets[u].kind === 'peng' && sets[u].tiles[0] === meldIds[0]) {
                    sets[u].kind = 'buGang';
                    sets[u].tiles.push(meldIds[0]);
                    upgraded = true;
                    break;
                }
            }
            if (!upgraded) {
                sets.push({
                    kind: kindKey,
                    tiles: [meldIds[0], meldIds[0], meldIds[0], meldIds[0]],
                    fromSeat: fromSeat,
                    claimTile: meldIds[0]
                });
            }
        }

        // 杠后补牌处理 (如 "暗杠 101 → 补牌 205")
        var mDrawBu = text.match(/→\s*补牌\s*(\d+)/);
        if (mDrawBu) {
            var buTile = +mDrawBu[1];
            state.hands[meldSeat] = state.hands[meldSeat] || [];
            state.hands[meldSeat].push(buTile);
            state.drawnTile = { seat: +meldSeat, tileId: buTile };
        }
        return true;
    }

    /**
     * 处理拖拉机扣底事件。
     *
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handleBury(state, text) {
        var m = text.match(/^座(\d+)\s*扣底\s*\[([\d,]+)\]/);
        if (!m) return false;
        var burySeat = m[1];
        var buryIds = m[2].split(',').map(Number);
        for (var b = 0; b < buryIds.length; b++) {
            removeOne(state, burySeat, buryIds[b]);
        }
        return true;
    }

    /**
     * 处理轮次、最大方判定及墩分审计事件。
     *
     * @param {Object} state 动态状态容器
     * @param {string} text 事件描述文本
     * @returns {boolean} 是否匹配并处理成功
     */
    function handleAudit(state, text) {
        var mNext = text.match(/^下一操作位\s*座(\d+)/);
        if (mNext) {
            state.nextSeat = +mNext[1];
            return true;
        }

        var mBest = text.match(/^(?:当前|本轮)最大方\s*座(\d+)/);
        if (mBest) {
            state.bestSeat = +mBest[1];
            // 设计原因：产生本轮最大方意味着当前一手出牌已完整比对，下一次出牌前需清理桌面旧牌
            if (text.indexOf('本轮最大方') >= 0) {
                state.needClearPlayed = true;
                state.lastTrickWinner = +mBest[1];
            }
            return true;
        }

        var mTrick = text.match(/本墩得分\s*(\d+)/);
        if (mTrick) {
            state.trickScore = +mTrick[1];
            return true;
        }

        return false;
    }

    /**
     * 单步执行事件：通过职责链模式分发给具体的动作处理器。
     *
     * @param {Object} model 回放模型对象
     * @param {Object} state 动态状态容器
     * @param {Object} evt 当前事件对象 { index, time, text }
     */
    function stepEvent(model, state, evt) {
        var text = evt.text;
        state.statusText = text;

        // 顺序匹配各业务子处理器
        var handled = handleBid(state, text)
            || handleBottomCards(model, state, text)
            || handlePlay(model, state, text)
            || handlePass(state, text)
            || handleDraw(state, text)
            || handleMeld(state, text)
            || handleBury(state, text)
            || handleAudit(state, text);

        return handled;
    }

    /**
     * 捕获当前运行状态的不可变浅层拷贝快照。
     *
     * @param {Object} state 当前运行状态
     * @returns {Object} 状态快照
     */
    function captureSnapshot(state) {
        return {
            hands: cloneHands(state.hands),
            played: shallowCloneObj(state.played),
            bids: shallowCloneObj(state.bids),
            bottomCards: (state.bottomCards || []).slice(),
            landlordSeat: state.landlordSeat,
            dealerSeat: state.dealerSeat,
            drawnTile: state.drawnTile ? { seat: state.drawnTile.seat, tileId: state.drawnTile.tileId } : null,
            discards: (state.discards || []).slice(),
            exposedSets: cloneExposedSets(state.exposedSets),
            nextSeat: state.nextSeat,
            bestSeat: state.bestSeat,
            statusText: state.statusText,
            lastTrickWinner: state.lastTrickWinner,
            trickScore: state.trickScore
        };
    }

    /**
     * 顺序步进全量事件，生成轻量状态快照序列。
     * 解决频繁拖拽进度条时的 O(N) 重复运算瓶颈，实现拖拽 O(1) 立即呈现。
     *
     * @param {Object} model 回放模型
     */
    function buildAllSnapshots(model) {
        var runningState = {
            hands: cloneHands(model.initial),
            played: {},
            bids: {},
            bottomCards: [],
            landlordSeat: -1,
            dealerSeat: model.meta.dealerSeat,
            drawnTile: null,
            discards: [],
            exposedSets: {},
            nextSeat: -1,
            bestSeat: -1,
            statusText: '初始发牌',
            lastTrickWinner: -1,
            trickScore: 0,
            needClearPlayed: false
        };

        var seats = Object.keys(runningState.hands);
        for (var s = 0; s < seats.length; s++) {
            sortHand(model, runningState.hands[seats[s]]);
        }

        // 保存 pos = -1 初始快照
        var initialSnapshot = captureSnapshot(runningState);
        initialSnapshot.eventIndex = 0;
        initialSnapshot.eventTime = '';
        initialSnapshot.eventText = '初始发牌';
        model.snapshots.push(initialSnapshot);

        // 依次步进事件并保存快照
        for (var i = 0; i < model.events.length; i++) {
            var evt = model.events[i];
            stepEvent(model, runningState, evt);
            var snap = captureSnapshot(runningState);
            snap.eventIndex = evt.index;
            snap.eventTime = evt.time;
            snap.eventText = evt.text;
            model.snapshots.push(snap);
        }
    }

    /**
     * 解析回放文本并构建快照。
     *
     * @param {string} content 回放文本
     * @param {string} code 回放编码
     * @param {string} gameType 玩法名称
     * @returns {Object} 初始化后的模型对象
     */
    function parse(content, code, gameType) {
        var initialHands = {};
        var events = [];
        var players = {};
        var headerMeta = {
            tableId: '',
            round: 1,
            totalRounds: 1,
            gameType: gameType || '',
            dealerSeat: -1,
            laiZiTileId: 0,
            flipTileId: 0,
            scores: {}
        };

        var lines = (content || '').split(/\r?\n/);
        for (var l = 0; l < lines.length; l++) {
            var line = lines[l].trim();
            if (!line) continue;

            var mTable = line.match(/^桌号:\s*(\d+)/);
            if (mTable) { headerMeta.tableId = mTable[1]; continue; }
            var mType = line.match(/^玩法:\s*(.+)/);
            if (mType) { headerMeta.gameType = mType[1].trim(); continue; }
            var mRound = line.match(/^当前局:\s*(\d+)/);
            if (mRound) { headerMeta.round = +mRound[1]; continue; }
            var mTotal = line.match(/^总局数:\s*(\d+)/);
            if (mTotal) { headerMeta.totalRounds = +mTotal[1]; continue; }
            var mDealer = line.match(/^庄家:\s*座(\d+)/);
            if (mDealer) { headerMeta.dealerSeat = +mDealer[1]; continue; }
            var mLaiZi = line.match(/^翻牌:\s*(\d+)\s*→\s*赖子:\s*(\d+)/);
            if (mLaiZi) {
                headerMeta.flipTileId = +mLaiZi[1];
                headerMeta.laiZiTileId = +mLaiZi[2];
                continue;
            }

            var mPlayer = line.match(/^座(\d+):\s*userId=(-?\d+),\s*nick=(.*)$/);
            if (mPlayer) {
                players[mPlayer[1]] = mPlayer[3];
                continue;
            }

            var mHand = line.match(/^座(\d+):\s*\[([\d,]*)\]$/);
            if (mHand) {
                initialHands[mHand[1]] = mHand[2] ? mHand[2].split(',').map(Number) : [];
                continue;
            }

            var mScore = line.match(/^座(\d+):\s*([+-]?\d+)$/);
            if (mScore) {
                headerMeta.scores[mScore[1]] = +mScore[2];
                continue;
            }

            var mEvent = line.match(/^\[(\d+)\](?:\[([^\]]+)\])?\s*(.*)$/);
            if (mEvent) {
                events.push({
                    index: +mEvent[1],
                    time: mEvent[2] || '',
                    text: mEvent[3]
                });
            }
        }

        var seatCount = Math.max(
            1,
            Object.keys(players).length,
            Object.keys(initialHands).length
        );

        var model = {
            content: content,
            code: code,
            type: headerMeta.gameType || gameType || '',
            meta: headerMeta,
            players: players,
            seatCount: seatCount,
            initial: initialHands,
            events: events,
            snapshots: [],
            pos: -1
        };

        buildAllSnapshots(model);
        return model;
    }

    /**
     * 将模型定位到指定步骤 pos (-1 到 events.length - 1)。
     *
     * @param {Object} model 回放模型对象
     * @param {number} pos 步骤索引 (-1 表示开局未动)
     * @returns {Object|null} 对应快照对象
     */
    function seek(model, pos) {
        if (!model || !model.snapshots || !model.snapshots.length) {
            return null;
        }
        var maxPos = model.snapshots.length - 2;
        var safePos = Math.max(-1, Math.min(pos, maxPos));
        model.pos = safePos;
        return model.snapshots[safePos + 1];
    }

    /**
     * 重建并同步模型自身属性（保持历史兼容）。
     *
     * @param {Object} model 回放模型
     * @param {number} pos 步骤索引
     * @returns {Object} 模型自身
     */
    function rebuild(model, pos) {
        var snap = seek(model, pos);
        if (snap) {
            model.hands = snap.hands;
            model.played = snap.played;
            model.bids = snap.bids;
            model.bottomCards = snap.bottomCards;
            model.landlordSeat = snap.landlordSeat;
            model.drawnTile = snap.drawnTile;
            model.discards = snap.discards;
            model.exposedSets = snap.exposedSets;
            model.nextSeat = snap.nextSeat;
            model.bestSeat = snap.bestSeat;
            model.statusText = snap.statusText;
        }
        return model;
    }

    /**
     * 自动化测试辅助检查函数。
     *
     * @param {string} content 回放文本
     * @param {string} gameType 玩法名称
     * @param {number} pos 步骤索引
     * @returns {Object} 状态检查结果对象
     */
    function inspect(content, gameType, pos) {
        var model = parse(content, 'test_inspect', gameType);
        var snap = seek(model, pos);
        return {
            hands: snap ? snap.hands : {},
            played: snap ? snap.played : {},
            bids: snap ? snap.bids : {},
            bottomCards: snap ? snap.bottomCards : [],
            landlordSeat: snap ? snap.landlordSeat : -1,
            drawnTile: snap ? snap.drawnTile : null,
            discards: snap ? snap.discards : [],
            exposedSets: snap ? snap.exposedSets : {},
            nextSeat: snap ? snap.nextSeat : -1,
            bestSeat: snap ? snap.bestSeat : -1,
            pos: model.pos
        };
    }

    var ReplayModel = {
        parse: parse,
        seek: seek,
        rebuild: rebuild,
        inspect: inspect,
        sortHand: sortHand,
        isMahjong: isMahjong
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = ReplayModel;
    }
    if (global) {
        global.ReplayModel = ReplayModel;
    }
})(typeof window !== 'undefined' ? window : this);
