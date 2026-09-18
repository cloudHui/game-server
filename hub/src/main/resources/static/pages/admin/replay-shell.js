/**
 * 回放容器外壳装载器 (ReplayShell)
 *
 * 核心职责：
 * 1. 动态装配回放控制台外壳（顶部导航栏、全仿真牌桌容器、底部时间轴与播放控制栏、右侧对局日志抽屉）；
 * 2. 提供全明牌/对手暗牌视角切换、倍速播放（0.5x~16x）、快进快退及复制回放码功能。
 */
(function (global) {
    'use strict';

    var mount = document.getElementById('replayMount');
    if (!mount) return;

    mount.innerHTML = '<div class="card" id="replayDetailCard" style="display:none">'
        + '<div class="replay-view-header">'
        + '  <h2 id="replayDetailTitle">牌局回放</h2>'
        + '  <div class="replay-header-actions">'
        + '    <button class="btn btn-ghost" onclick="ReplayPlayer.toggleSpectatorMode()">切换明牌/暗牌</button> '
        + '    <button class="btn btn-ghost" onclick="ReplayPlayer.latest()">追到最新</button> '
        + '    <button class="btn btn-danger" onclick="ReplayPlayer.close()">退出回放</button>'
        + '  </div>'
        + '</div>'
        + '<div class="replay-player" id="replayPlayer">'
        + '  <div class="replay-controls top-controls">'
        + '    <strong id="replayCodeLabel"></strong>'
        + '    <label class="replay-view-label"><select id="replayViewSeat" onchange="ReplayPlayer.viewSeat(Number(this.value))"></select></label>'
        + '    <button class="replay-copy" onclick="ReplayPlayer.copyCode()">复制回放码</button>'
        + '  </div>'
        + ReplayTableView.shell()
        + '  <div class="replay-controls bottom-controls">'
        + '    <button onclick="ReplayPlayer.move(-1)">上一步</button>'
        + '    <button id="replayPlay" onclick="ReplayPlayer.toggle()">播放</button>'
        + '    <button onclick="ReplayPlayer.move(1)">下一步</button>'
        + '    <select id="replaySpeed" aria-label="播放速度">'
        + '      <option value="0.5">0.5×</option>'
        + '      <option value="1" selected>1.0×</option>'
        + '      <option value="2">2.0×</option>'
        + '      <option value="4">4.0×</option>'
        + '    </select>'
        + '    <input id="replayRange" type="range" min="0" value="0" oninput="ReplayPlayer.seek(Number(this.value)-1)">'
        + '    <span id="replayStep" class="replay-step-badge">0 / 0</span>'
        + '  </div>'
        + '  <button class="replay-log-button" onclick="ReplayPlayer.toggleLog()">对局日志</button>'
        + '  <div class="replay-log-mask" id="replayLogMask" onclick="ReplayPlayer.closeLog(event)">'
        + '    <section class="replay-log-panel" onclick="event.stopPropagation()">'
        + '      <div class="replay-log-title">'
        + '        <strong>对局日志</strong>'
        + '        <button onclick="ReplayPlayer.closeLog()">关闭</button>'
        + '      </div>'
        + '      <div class="replay-log" id="replayLog"></div>'
        + '      <details class="replay-raw">'
        + '        <summary>原始文本</summary>'
        + '        <pre id="replayRaw"></pre>'
        + '      </details>'
        + '    </section>'
        + '  </div>'
        + '</div>'
        + '</div>';
})(typeof window !== 'undefined' ? window : this);
