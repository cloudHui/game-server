/** 大厅页登录态与退出（各入口页共用） */
(function () {
    var sessionId = localStorage.getItem('sessionId');
    if (!sessionId) {
        window.location.href = appUrl('/');
        return;
    }
    var displayName = localStorage.getItem('nickname') || localStorage.getItem('username') || '玩家';
    var userEl = document.getElementById('userDisplay');
    var welcomeEl = document.getElementById('welcomeName');
    if (userEl) userEl.textContent = displayName;
    if (welcomeEl) welcomeEl.textContent = displayName;
    var adminLink = document.getElementById('adminLink');
    if (adminLink && localStorage.getItem('isAdmin') === '1') {
        adminLink.style.display = 'inline';
    }
    window.logout = function () {
        fetch(appUrl('/api/logout'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId: sessionId })
        }).catch(function () {});
        localStorage.clear();
        window.location.href = appUrl('/');
    };
})();
