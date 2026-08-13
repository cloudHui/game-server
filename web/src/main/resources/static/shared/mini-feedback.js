(function (w) {
    'use strict';
    // Shared child-game feedback API. All methods are safe no-ops when speech is
    // unavailable; stop() must be called by hosts with a custom page lifecycle.
    // speak/readGoal(text, interrupt?), praise(text?), stop(), setEnabled(boolean).
    var toast, timer, lastText = '', lastAt = 0, enabled = true;
    function speak(text, interrupt) {
        if (!enabled || !text || !w.speechSynthesis || !w.SpeechSynthesisUtterance) return;
        var now = Date.now();
        if (String(text) === lastText && now - lastAt < 350) return;
        lastText = String(text); lastAt = now;
        if (interrupt !== false) w.speechSynthesis.cancel();
        var u = new SpeechSynthesisUtterance(String(text));
        u.lang = 'zh-CN'; u.rate = 0.92;
        w.speechSynthesis.speak(u);
    }
    function node() {
        if (toast && toast.isConnected) return toast;
        toast = document.createElement('div');
        toast.setAttribute('role', 'status');
        toast.style.cssText = 'position:fixed;left:50%;top:22%;z-index:10050;transform:translate(-50%,-50%) scale(.92);padding:14px 28px;border-radius:18px;background:rgba(22,163,74,.94);color:#fff;font:900 30px/1.2 "Microsoft YaHei",sans-serif;box-shadow:0 10px 30px #0005;pointer-events:none;opacity:0;transition:opacity .25s,transform .25s';
        document.body.appendChild(toast);
        return toast;
    }
    function praise(text) {
        var n = node(); clearTimeout(timer);
        n.textContent = text || '很棒！'; n.style.opacity = '1';
        n.style.transform = 'translate(-50%,-50%) scale(1)'; speak(n.textContent);
        timer = setTimeout(function () {
            n.style.opacity = '0'; n.style.transform = 'translate(-50%,-50%) scale(.92)';
        }, 1000);
    }
    function stop() {
        clearTimeout(timer);
        if (w.speechSynthesis) w.speechSynthesis.cancel();
        if (toast) toast.style.opacity = '0';
    }
    function setEnabled(value) { enabled = value !== false; if (!enabled) stop(); }
    w.addEventListener('pagehide', stop);
    document.addEventListener('visibilitychange', function () { if (document.hidden) stop(); });
    w.MiniFeedback = {speak:speak,readGoal:speak,praise:praise,stop:stop,setEnabled:setEnabled};
})(window);
