/** 兼容随机 context-path：本机根路径为空，外网为 /{随机路径} */
(function (w) {
  var parts = w.location.pathname.split('/').filter(Boolean);
  var base = '';
  if (parts.length && parts[0].indexOf('.') < 0) {
    base = '/' + parts[0];
  }
  w.APP_BASE = base;
  // 与服务端 TableState.TABLE_DIS 保持一致：桌子已解散。
  w.TABLE_STATE_DIS = 9;
  w.appUrl = function (path) {
    if (path == null || path === '') {
      return base || '/';
    }
    if (path.charAt(0) !== '/') {
      path = '/' + path;
    }
    return base + path;
  };

  // 仅对「会与服务器交互」的按钮防连点：本地跳转/本地操作不受 3 秒限制。
  // 判定方式：点击后若同步触发 fetch / XHR / WebSocket.send，则对该按钮生效。
  (function () {
    var lastClick = typeof WeakMap !== 'undefined' ? new WeakMap() : null;
    var cooldown = 3000;
    var pendingButton = null;
    var pendingArmedAt = 0;

    function tip() {
      var el = document.getElementById('global-click-tip');
      if (!el) {
        el = document.createElement('div');
        el.id = 'global-click-tip';
        el.style.cssText = 'position:fixed;left:50%;top:18%;transform:translate(-50%,-50%);z-index:99999;padding:10px 18px;border-radius:20px;background:rgba(25,25,25,.88);color:#fff;font-size:14px;pointer-events:none;opacity:0;transition:opacity .25s';
        document.body.appendChild(el);
      }
      el.textContent = '请稍后';
      el.style.opacity = '1';
      clearTimeout(el._timer);
      el._timer = setTimeout(function () { el.style.opacity = '0'; }, 900);
    }

    function readStamp(button) {
      return (lastClick && lastClick.get(button)) || button._lastClick || 0;
    }

    function writeStamp(button, now) {
      if (lastClick) lastClick.set(button, now);
      else button._lastClick = now;
    }

    function clearStamp(button) {
      if (lastClick) lastClick.delete(button);
      else delete button._lastClick;
    }

    /** 点击同步路径上出现服务端交互时，确认保留防连点时间戳。 */
    function noteServerInteraction() {
      if (!pendingButton) return;
      pendingButton._serverClick = Date.now();
    }

    function disarmIfLocal(button, armedAt) {
      if (pendingButton === button) pendingButton = null;
      if (!button._serverClick || button._serverClick < armedAt) {
        clearStamp(button);
      }
    }

    function patchNetworkHooks() {
      if (typeof w.fetch === 'function') {
        var rawFetch = w.fetch;
        w.fetch = function () {
          noteServerInteraction();
          return rawFetch.apply(this, arguments);
        };
      }
      if (w.XMLHttpRequest && w.XMLHttpRequest.prototype) {
        var rawXhrSend = w.XMLHttpRequest.prototype.send;
        w.XMLHttpRequest.prototype.send = function () {
          noteServerInteraction();
          return rawXhrSend.apply(this, arguments);
        };
      }
      if (w.WebSocket && w.WebSocket.prototype) {
        var rawWsSend = w.WebSocket.prototype.send;
        w.WebSocket.prototype.send = function () {
          noteServerInteraction();
          return rawWsSend.apply(this, arguments);
        };
      }
    }

    document.addEventListener('click', function (event) {
      var button = event.target.closest && event.target.closest('button');
      if (!button || button.disabled) return;
      // 显式本地操作：永不拦截。
      if (button.getAttribute('data-local') === '1') return;
      var now = Date.now();
      var previous = readStamp(button);
      if (previous && now - previous < cooldown) {
        event.preventDefault();
        event.stopImmediatePropagation();
        tip();
        return;
      }
      // 先临时记时防双击竞态；若本次点击未触发服务端交互，则在微任务中撤销。
      writeStamp(button, now);
      pendingButton = button;
      pendingArmedAt = now;
      queueMicrotask(function () {
        disarmIfLocal(button, pendingArmedAt);
      });
    }, true);

    patchNetworkHooks();
  })();
})(window);
