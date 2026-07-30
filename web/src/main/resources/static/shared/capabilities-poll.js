/** 每分钟轮询 /api/capabilities，控制联网玩法「敬请期待」 */
(function (w) {
  var CAP = { onlineTables: false, miniOnline: true, miniLocal: true, learning: true };
  var listeners = [];
  var timer = null;
  var refreshing = false;

  function applyDom() {
    document.querySelectorAll('[data-need-online]').forEach(function (el) {
      var need = el.getAttribute('data-need-online');
      var ok = !!CAP[need];
      el.classList.toggle('is-waiting', !ok);
      var tip = el.querySelector('.waiting-tip');
      if (!ok) {
        if (!tip) {
          tip = document.createElement('span');
          tip.className = 'waiting-tip';
          tip.textContent = '敬请期待';
          el.appendChild(tip);
        }
        if (el.tagName === 'A') {
          el.addEventListener('click', blockClick, true);
        }
      } else if (tip) {
        tip.remove();
        if (el.tagName === 'A') {
          el.removeEventListener('click', blockClick, true);
        }
      }
    });
    listeners.forEach(function (fn) { try { fn(CAP); } catch (e) {} });
  }

  function blockClick(e) {
    e.preventDefault();
    e.stopPropagation();
    alert('联网服务尚未启动，敬请期待');
  }

  async function refresh() {
    if (refreshing || (w.AppQuality && !w.AppQuality.canRequest())) return;
    refreshing = true;
    try {
      var url = (typeof appUrl === 'function') ? appUrl('/api/capabilities') : '/api/capabilities';
      var r = await fetch(url, { credentials: 'include' });
      if (r.ok) {
        CAP = await r.json();
      }
    } catch (e) { /* 保留上次能力状态，避免短暂断网让入口闪烁。 */ }
    finally {
      refreshing = false;
      applyDom();
    }
  }

  w.Capabilities = {
    get: function () { return CAP; },
    onChange: function (fn) { listeners.push(fn); },
    refresh: refresh,
    start: function () {
      refresh();
      if (timer) clearInterval(timer);
      timer = setInterval(refresh, 60000);
      if (w.AppQuality) w.AppQuality.onResume(refresh);
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { w.Capabilities.start(); });
  } else {
    w.Capabilities.start();
  }
})(window);
