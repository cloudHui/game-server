(function (w) {
    'use strict';
    var mount = document.getElementById('replayMount');
    if (!mount) return;
    mount.innerHTML = '<div class="card" id="replayDetailCard" style="display:none">'
        + '<div class="replay-view-header"><h2 id="replayDetailTitle">牌局回放</h2><div>'
        + '<button class="btn btn-ghost" onclick="ReplayPlayer.latest()">追到最新</button> '
        + '<button class="btn btn-danger" onclick="ReplayPlayer.close()">结束观看并退出</button></div></div>'
        + '<div class="replay-player" id="replayPlayer"><div class="replay-controls">'
        + '<strong id="replayCodeLabel"></strong><label class="replay-view-label">观看座位 <select id="replayViewSeat" onchange="ReplayPlayer.viewSeat(Number(this.value))"></select></label><button class="replay-copy" onclick="ReplayPlayer.copyCode()">复制回放码</button></div>'
        + '<div class="replay-table">'
        + [2, 1, 3, 0].map(function (seat) { return '<div class="replay-seat s' + seat + '"><span class="replay-seat-name"></span><div class="replay-hand"></div></div>'; }).join('')
        + '<div class="replay-center"><div id="replayEventCards" class="replay-event-cards"></div><div id="replayEvent" class="replay-event"></div><div id="replayDecision" class="replay-decision"></div><span id="replayStep"></span></div></div>'
        + '<div class="replay-controls"><button onclick="ReplayPlayer.move(-1)">上一步</button><button id="replayPlay" onclick="ReplayPlayer.toggle()">播放</button><button onclick="ReplayPlayer.move(1)">下一步</button>'
        + '<select id="replaySpeed"><option value="0.5">0.5×</option><option value="1" selected>1×</option><option value="2">2×</option><option value="4">4×</option><option value="8">8×</option><option value="16">16×</option></select>'
        + '<input id="replayRange" type="range" min="0" value="0" oninput="ReplayPlayer.seek(Number(this.value)-1)"></div>'
        + '<div class="replay-log" id="replayLog"></div><details class="replay-raw"><summary>原始审计文本</summary><pre id="replayRaw"></pre></details></div></div>';
    w.ReplayShell = {mount: mount};
})(window);
