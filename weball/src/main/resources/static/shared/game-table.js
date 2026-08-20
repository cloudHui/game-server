/**
 * 牌桌页公共能力：会话、WebSocket、导航、中央提示、小结算壳、准备。
 * 玩法页只保留专属逻辑，避免 doudizhu/mahjong/game 三份拷贝。
 */
(function (w) {
  'use strict';
  var displayedRound = 0;
  var displayedTotalRounds = 0;
  var completedRound = 0;

  function loadSession() {
    return {
      sessionId: localStorage.getItem('sessionId'),
      userId: parseInt(localStorage.getItem('userId') || '0', 10),
      nickname: localStorage.getItem('nickname') || '玩家',
      tableId: parseInt(localStorage.getItem('tableId') || '0', 10),
      roomId: parseInt(localStorage.getItem('roomId') || '0', 10)
    };
  }

  function requireSessionOrRedirect(session) {
    if (!session.sessionId || !session.tableId) {
      w.location.href = appUrl('/');
      return false;
    }
    return true;
  }

  function isQuickRobotRoom(roomId) {
    return w.RoomConfig ? w.RoomConfig.isQuick(roomId) : false;
  }

  function gamePageByType(gameType) {
    if (gameType === 1) return '/pages/games/mahjong/index.html';
    if (gameType === 3) return '/pages/games/paodekuai/index.html';
    if (gameType === 4) return '/pages/games/tractor/index.html';
    return '/pages/games/doudizhu/index.html';
  }

  function setWsStatus(connected, text) {
    var el = document.getElementById('wsStatus');
    if (!el) return;
    el.textContent = text || (connected ? '已连接' : '已断开');
    el.className = 'ws-status ' + (connected ? 'connected' : 'disconnected');
  }

  /**
   * 创建牌桌 WebSocket 客户端。
   * @param {object} opts
   * @param {string} opts.sessionId
   * @param {function} opts.onAuthed 认证成功回调
   * @param {function} opts.onPush 推送消息回调（非 seq 回调）
   */
  function createGameWs(opts) {
    var ws = null;
    var heartbeatTimer = null;
    var seqCounter = 0;
    var pending = {};
    var closed = false;

    function send(action, data, callback) {
      var seq = ++seqCounter;
      if (callback) pending[seq] = callback;
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: action, seq: seq, data: data || {} }));
      }
      return seq;
    }

    function connect() {
      console.info('[牌桌连接尝试]', { sessionId: opts.sessionId });
      var protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(protocol + '//' + location.host + appUrl('/ws/game'));
      ws.onopen = function () {
        console.info('[牌桌连接建立]');
        setWsStatus(true, '已连接');
        send('auth', { sessionId: opts.sessionId }, function (resp) {
          if (resp.code !== 0) return;
          startHeartbeat();
          if (opts.onAuthed) opts.onAuthed(resp);
        });
      };
      ws.onmessage = function (event) {
        var data = JSON.parse(event.data);
        console.info('[牌桌消息]', data);
        var seq = data.seq;
        if (seq && pending[seq]) {
          pending[seq](data);
          delete pending[seq];
          return;
        }
        if (opts.onPush) opts.onPush(data);
      };
      ws.onclose = function () {
        console.warn('[牌桌连接断开]');
        setWsStatus(false, '已断开');
        if (!closed) setTimeout(connect, 3000);
      };
      ws.onerror = function () {
        console.log('WebSocket错误');
      };
    }

    function stopReconnect() {
      closed = true;
      stopHeartbeat();
      if (ws) ws.onclose = null;
    }

    /** 认证后立即发送并每十秒续发牌桌心跳，供 Game 判断网页是否仍存活。 */
    function startHeartbeat() {
      stopHeartbeat();
      send('heartbeat', { tableId: opts.tableId });
      heartbeatTimer = setInterval(function () {
        send('heartbeat', { tableId: opts.tableId });
      }, 10000);
    }

    /** 停止页面心跳，避免总结算或主动退出后继续维持桌内状态。 */
    function stopHeartbeat() {
      if (!heartbeatTimer) return;
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }

    return { connect: connect, send: send, stopReconnect: stopReconnect, getWs: function () { return ws; } };
  }

  /** 使用牌桌顶部现有弱化文字样式展示当前局数，不额外占用桌面空间。 */
  function renderRoundInfo(currentRound, totalRounds) {
    var el = document.getElementById('roundInfo');
    if (!el) return;
    var current = Number(currentRound || 0), total = Number(totalRounds || 0);
    if (current > 0) displayedRound = current;
    if (total > 0) displayedTotalRounds = total;
    el.textContent = current > 0 && total > 0 ? ('第 ' + current + ' / ' + total + ' 局') : '牌桌加载中...';
  }

  /** 应用服务端桌信息并返回房间号，统一维护局数和本地房间上下文。 */
  function applyTableInfo(info, fallbackRoomId) {
    info = info || {};
    renderRoundInfo(info.currentRound, info.totalRounds);
    var roomId = Number(info.roomId || fallbackRoomId || 0);
    if (roomId) localStorage.setItem('roomId', String(roomId));
    return roomId;
  }

  /** 记录刚完成的局，供下一次发牌时准确推进顶部局数。 */
  function noteRoundCompleted(round) {
    completedRound = Math.max(completedRound, Number(round || 0));
  }

  /** 新一轮发牌时从已完成局数推进，不依赖各玩法重复维护局数。 */
  function noteRoundStarted() {
    if (!completedRound || completedRound < displayedRound) return;
    renderRoundInfo(Math.min(completedRound + 1, displayedTotalRounds), displayedTotalRounds);
  }

  function showCenterMsg(msg, duration) {
    var el = document.getElementById('centerMsg');
    if (!el) return;
    el.textContent = msg;
    el.className = 'center-message show';
    setTimeout(function () { el.className = 'center-message'; }, duration || 2000);
  }

  function hideActions() {
    var bar = document.getElementById('actionBar');
    if (bar) bar.style.display = 'none';
  }

  function roomListPage() {
    var path = w.location.pathname;
    if (path.indexOf('/mahjong/') >= 0) return '/pages/games/mahjong/rooms.html';
    if (path.indexOf('/paodekuai/') >= 0) return '/pages/games/paodekuai/rooms.html';
    if (path.indexOf('/tractor/') >= 0) return '/pages/games/tractor/rooms.html';
    return '/pages/games/doudizhu/rooms.html';
  }

  function backToLobby() {
    w.location.href = appUrl(roomListPage());
  }

  function exitRoom(sendFn) {
    sendFn('leave', {}, function (resp) {
      if (resp && resp.code === 0) {
        localStorage.removeItem('tableId');
        w.location.href = appUrl(roomListPage());
      } else {
        AppDialog.alert((resp && resp.msg) || '退出房间失败');
      }
    });
  }

  function doPrepare(sendFn, onFail) {
    sendFn('op', { choice: 7 }, function (resp) {
      if (resp.code !== 0) {
        showCenterMsg(resp.msg || '准备失败');
        if (onFail) onFail(resp);
      }
    });
    hideActions();
  }

  var settleTimer = null;
  var finalSettleVisible = false;

  function closeSettle() {
    var overlay = document.getElementById('settleOverlay');
    if (overlay) overlay.className = 'settle-overlay';
    var cd = document.getElementById('settleCountdown');
    if (cd) cd.textContent = '';
    if (settleTimer) {
      clearInterval(settleTimer);
      settleTimer = null;
    }
  }

  /**
   * 展示小结算壳；autoNext 时 15 秒倒计时后自动关闭（开局由服务端超时处理）。
   * @param {object} opt title/meta/rowsHtml/handsHtml/autoNext/onAutoClose
   */
  function showSettle(opt) {
    opt = opt || {};
    document.getElementById('settleTitle').textContent = opt.title || '结算';
    document.getElementById('settleMeta').textContent = opt.meta || '';
    document.getElementById('settleRows').innerHTML = opt.rowsHtml || '';
    var hands = document.getElementById('settleHands');
    if (hands) hands.innerHTML = opt.handsHtml || '';
    document.getElementById('settleOverlay').className = 'settle-overlay show';
    var cd = document.getElementById('settleCountdown');
    if (settleTimer) {
      clearInterval(settleTimer);
      settleTimer = null;
    }
    if (!cd) return;
    // 小结算始终展示等待倒计时；有人点准备后机器人会自动跟上
    var left = typeof opt.waitSec === 'number' ? opt.waitSec : 15;
    var tip = opt.autoNext
      ? '小结算展示中，' + left + ' 秒后自动准备下一局'
      : '等待准备中，' + left + ' 秒后开始下一局（点准备可提前，机器人自动准备）';
    cd.textContent = tip;
    settleTimer = setInterval(function () {
      left -= 1;
      if (left <= 0) {
        clearInterval(settleTimer);
        settleTimer = null;
        closeSettle();
        if (opt.onAutoClose) opt.onAutoClose();
        return;
      }
      cd.textContent = opt.autoNext
        ? '小结算展示中，' + left + ' 秒后自动准备下一局'
        : '等待准备中，' + left + ' 秒后开始下一局（点准备可提前，机器人自动准备）';
    }, 1000);
  }

  /** 展示不会被解散通知立即关闭的总结算，并提供唯一的返回入口。 */
  function showFinalSettle(opt, gameWs) {
    finalSettleVisible = true;
    if (gameWs) gameWs.stopReconnect();
    localStorage.removeItem('tableId');
    showSettle(opt);
    if (settleTimer) {
      clearInterval(settleTimer);
      settleTimer = null;
    }
    var cd = document.getElementById('settleCountdown');
    if (cd) cd.textContent = '本桌已结束';
    var bar = document.getElementById('actionBar');
    if (bar) {
      bar.innerHTML = '<button class="action-btn btn-prepare" type="button">返回房间列表</button>';
      bar.style.display = 'flex';
      bar.firstChild.onclick = backToLobby;
    }
  }

  /** 使用所有玩法共用的座位总分结构展示总结算。 */
  function showScoreFinal(data, gameWs) {
    if (!data) return;
    var rows = '';
    (data.totalScores || []).forEach(function (item) {
      rows += '<div class="row"><span>座位 ' + item.seat + '</span><span>' + item.score + ' 分</span></div>';
    });
    showFinalSettle({
      title: '总结算',
      meta: '完成 ' + (data.completedRounds || 0) + ' / ' + (data.totalRounds || 0) + ' 局',
      rowsHtml: rows
    }, gameWs);
  }

  /** 处理服务端解散通知；已有总结算时保留面板，否则立即返回房间列表。 */
  function handleTableDestroyed(gameWs) {
    if (gameWs) gameWs.stopReconnect();
    localStorage.removeItem('tableId');
    if (!finalSettleVisible) backToLobby();
  }

  w.GameTable = {
    loadSession: loadSession,
    requireSessionOrRedirect: requireSessionOrRedirect,
    isQuickRobotRoom: isQuickRobotRoom,
    gamePageByType: gamePageByType,
    setWsStatus: setWsStatus,
    createGameWs: createGameWs,
    showCenterMsg: showCenterMsg,
    hideActions: hideActions,
    backToLobby: backToLobby,
    renderRoundInfo: renderRoundInfo,
    applyTableInfo: applyTableInfo,
    noteRoundCompleted: noteRoundCompleted,
    noteRoundStarted: noteRoundStarted,
    exitRoom: exitRoom,
    doPrepare: doPrepare,
    showSettle: showSettle,
    showScoreFinal: showScoreFinal,
    handleTableDestroyed: handleTableDestroyed,
    closeSettle: closeSettle
  };
})(window);
