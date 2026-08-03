/** 拖拉机手牌：主色排序、一次发完后的本地发牌动画 */
window.pokerHandScale = 0.72;
var DEAL_CARD_MS = 100;

/**
 * 从左到右：
 * 副牌方梅红黑（各花色小到大）→主花色普通牌→副级牌方梅红黑
 * →主级牌→小王→大王。
 */
function sortHandCards(cards) {
    var level = gameState.levelRank || 15;
    var trump = gameState.trumpSuit || 0;
    cards.sort(function (a, b) {
        var ka = tractorHandOrder(a, level, trump);
        var kb = tractorHandOrder(b, level, trump);
        return ka - kb || a - b;
    });
}

function tractorHandOrder(cardId, level, trump) {
    if (cardId === 516) return 800000;
    if (cardId === 517) return 900000;
    var suit = Math.floor(cardId / 100);
    var val = cardId % 100;
    if (val === level) {
        return suit === trump ? 700000 : 600000 + suit * 100;
    }
    if (trump > 0 && suit === trump) {
        return 500000 + val;
    }
    return suit * 10000 + val;
}

function updateTrumpMeta(level, trump) {
    if (level) gameState.levelRank = level;
    if (typeof trump === 'number') gameState.trumpSuit = trump;
    var levelMap = {11: 'J', 12: 'Q', 13: 'K', 14: 'A', 15: '2'};
    var suitMap = {0: '无主', 1: '方块', 2: '梅花', 3: '红桃', 4: '黑桃'};
    var el = document.getElementById('multipleInfo');
    if (el) {
        el.textContent = '当前打 ' + (levelMap[gameState.levelRank] || gameState.levelRank)
            + ' ｜ 主花色 ' + (suitMap[gameState.trumpSuit] != null ? suitMap[gameState.trumpSuit] : '未定')
            + ' ｜ 闲分 ' + (gameState.defenderScore || 0);
    }
}

function stopDealAnim() {
    if (gameState.dealAnimTimer) {
        clearInterval(gameState.dealAnimTimer);
        gameState.dealAnimTimer = null;
    }
    gameState.pendingDealCards = null;
}

/** 服务端一次推满手后，本地按 100ms 一张翻开，25 张共 2.5 秒；重连/刷新跳过动画。 */
function playLocalDealAnim(fullMine, opponentCounts, opts) {
    opts = opts || {};
    stopDealAnim();
    sortHandCards(fullMine);
    if (opts.skip || gameState.skipDealAnim) {
        gameState.myCards = fullMine.slice();
        gameState.opponentCounts = opponentCounts || {};
        gameState.dealFlashIds = [];
        gameState.dealing = false;
        gameState.skipDealAnim = false;
        renderMyCards();
        renderOpponentHands();
        return;
    }
    gameState.dealing = true;
    gameState.layoutCardCount = fullMine.length;
    gameState.myCards = [];
    gameState.opponentCounts = {};
    gameState.pendingDealCards = fullMine.slice();
    var targets = opponentCounts || {};
    var roleIds = Object.keys(targets);
    roleIds.forEach(function (rid) {
        gameState.opponentCounts[rid] = 0;
    });
    renderMyCards();
    renderOpponentHands();
    showCenterMsg('发牌中，可抢主', 1500);

    var idx = 0;
    var total = fullMine.length;
    gameState.dealAnimTimer = setInterval(function () {
        var card = fullMine[idx++];
        gameState.myCards.push(card);
        gameState.dealFlashIds = [card];
        sortHandCards(gameState.myCards);
        // 对手张数同步上涨
        roleIds.forEach(function (rid) {
            var cap = targets[rid] || 0;
            if ((gameState.opponentCounts[rid] || 0) < cap) {
                gameState.opponentCounts[rid] = (gameState.opponentCounts[rid] || 0) + 1;
            }
        });
        renderMyCards();
        renderOpponentHands();

        if (idx >= total) {
            stopDealAnim();
            gameState.dealing = false;
            gameState.layoutCardCount = 0;
            roleIds.forEach(function (rid) {
                gameState.opponentCounts[rid] = targets[rid] || 0;
            });
            sortHandCards(gameState.myCards);
            gameState.dealFlashIds = [];
            renderMyCards();
            renderOpponentHands();
            showCenterMsg('发牌完成，可继续亮主', 1200);
        }
    }, DEAL_CARD_MS);
}

function handleNotCard(data) {
    if (!data || !data.nCards) return;
    var mine = [];
    var opponentCounts = {};
    var bottom = [];
    var maxCount = 0;
    var landlordCandidate = 0;
    for (var i = 0; i < data.nCards.length; i++) {
        var nc = data.nCards[i];
        if (!nc.cards) continue;
        if (nc.roleId === 0) {
            for (var b = 0; b < nc.cards.length; b++) {
                if (nc.cards[b].value) bottom.push(nc.cards[b].value);
            }
            continue;
        }
        if (nc.roleId === userId) {
            for (var j = 0; j < nc.cards.length; j++) {
                mine.push(nc.cards[j].value);
            }
        } else {
            opponentCounts[nc.roleId] = nc.cards.length;
        }
        if (nc.cards.length > maxCount) {
            maxCount = nc.cards.length;
            landlordCandidate = nc.roleId;
        }
    }
    gameState.bottomCards = bottom;
    renderDizhuCards(bottom);
    if (landlordCandidate && (bottom.length || maxCount > 25)) {
        gameState.landlordId = landlordCandidate;
    }

    // 扣底/拿底：直接刷新，不播发牌动画
    if (bottom.length || maxCount > 25) {
        stopDealAnim();
        gameState.dealing = false;
        gameState.myCards = mine;
        gameState.opponentCounts = opponentCounts;
        sortHandCards(gameState.myCards);
        gameState.selectedCards.clear();
        gameState.selectedCardIndexes.clear();
        gameState.dealFlashIds = [];
        renderMyCards();
        renderOpponentHands();
        showCenterMsg(maxCount > 25
            ? (userId === gameState.landlordId ? '拿到底牌，30秒内放回8张' : '庄家扣底中')
            : '底牌已扣（仅庄家可见)', 1800);
        return;
    }

    // 开局满手：本地动画；重连若已有手牌则跳过
    var skip = gameState.skipDealAnim
        || (gameState.myCards.length >= 25 && mine.length >= 25);
    if (mine.length >= 25 && !bottom.length) {
        playLocalDealAnim(mine, opponentCounts, {skip: skip});
        if (!skip) gameState.selectedCards.clear();
        if (!skip) gameState.selectedCardIndexes.clear();
        return;
    }

    stopDealAnim();
    gameState.myCards = mine;
    gameState.opponentCounts = opponentCounts;
    sortHandCards(gameState.myCards);
    gameState.selectedCards.clear();
    gameState.selectedCardIndexes.clear();
    gameState.dealFlashIds = [];
    renderMyCards();
    renderOpponentHands();
}
