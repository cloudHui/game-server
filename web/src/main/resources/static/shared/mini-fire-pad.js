/**
 * 字母/数字射击手机虚拟键盘：在窄屏显示，点击回调与物理键盘一致。
 */
(function (w) {
  function clear(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
  }

  function addBtn(host, label, value, className, onKey) {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = label;
    btn.dataset.key = value;
    if (className) btn.className = className;
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      onKey(value);
    });
    host.appendChild(btn);
  }

  /** 挂载字母垫 A–Z */
  function mountLetters(host, onKey) {
    if (!host) return;
    clear(host);
    host.className = 'mini-fire-pad letters';
    var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    for (var i = 0; i < alphabet.length; i++) {
      var ch = alphabet.charAt(i);
      addBtn(host, ch, ch.toLowerCase(), '', onKey);
    }
  }

  /** 挂载数字垫 0–9，含退格与清空 */
  function mountDigits(host, onKey) {
    if (!host) return;
    clear(host);
    host.className = 'mini-fire-pad digits';
    for (var i = 1; i <= 9; i++) addBtn(host, String(i), String(i), '', onKey);
    addBtn(host, '退格', 'Backspace', 'util', onKey);
    addBtn(host, '0', '0', '', onKey);
    addBtn(host, '清空', 'Escape', 'util', onKey);
  }

  w.MiniFirePad = { mountLetters: mountLetters, mountDigits: mountDigits };
})(window);
