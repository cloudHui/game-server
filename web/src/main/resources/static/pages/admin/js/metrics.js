(function (w) {
    'use strict';

    var historyData = [];
    var maxHistoryPoints = 15;
    var autoRefreshTimer = null;
    var autoRefreshEnabled = true;

    function formatUptime(seconds) {
        if (!seconds) return '0秒';
        var d = Math.floor(seconds / 86400);
        var h = Math.floor((seconds % 86400) / 3600);
        var m = Math.floor((seconds % 3600) / 60);
        var s = seconds % 60;
        var res = '';
        if (d > 0) res += d + '天 ';
        if (h > 0 || d > 0) res += h + '小时 ';
        if (m > 0 || h > 0 || d > 0) res += m + '分 ';
        res += s + '秒';
        return res;
    }

    w.toggleMetricsAutoRefresh = function () {
        autoRefreshEnabled = !autoRefreshEnabled;
        var btn = document.getElementById('metricsAutoBtn');
        if (btn) {
            btn.innerText = autoRefreshEnabled ? '自动刷新: 开启 (5s)' : '自动刷新: 暂停';
            btn.className = autoRefreshEnabled ? 'btn btn-primary' : 'btn btn-ghost';
        }
        if (autoRefreshEnabled) {
            startTimer();
        } else if (autoRefreshTimer) {
            clearInterval(autoRefreshTimer);
            autoRefreshTimer = null;
        }
    };

    function startTimer() {
        if (autoRefreshTimer) clearInterval(autoRefreshTimer);
        autoRefreshTimer = setInterval(function () {
            var panel = document.getElementById('panel-metrics');
            if (panel && panel.classList.contains('active') && autoRefreshEnabled) {
                w.loadMetrics(true);
            }
        }, 5000);
    }

    w.loadMetrics = function (silent) {
        var el = document.getElementById('metricsContainer');
        if (!el) return;
        if (!silent && historyData.length === 0) {
            el.innerHTML = '<div style="color:var(--muted);padding:16px;">正在拉取系统监控数据...</div>';
        }

        Admin.get('/metrics').then(function (res) {
            if (!res || res.code !== 0 || !res.data) {
                if (!silent) el.innerHTML = '<div class="msg err">获取系统监控指标失败：' + (res && res.msg ? res.msg : '网络错误') + '</div>';
                return;
            }
            var d = res.data;
            var jvm = d.jvm || {};
            var settles = d.roundsSettled || {};
            var logins = d.logins || {};

            // 记录时序指标
            var nowLabel = new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
            historyData.push({
                time: nowLabel,
                online: d.onlineUsers || 0,
                tables: d.activeTables || 0,
                mem: jvm.usedMemoryMb || 0
            });
            if (historyData.length > maxHistoryPoints) {
                historyData.shift();
            }

            renderDashboard(el, d, jvm, settles, logins);
            startTimer();
        }).catch(function (err) {
            if (!silent) el.innerHTML = '<div class="msg err">拉取失败：' + (err.message || '未知异常') + '</div>';
        });
    };

    function renderDashboard(el, d, jvm, settles, logins) {
        var displayUsers = d.totalUsers !== undefined ? d.totalUsers : (d.registerSuccessTotal || 0);
        var autoBtnText = autoRefreshEnabled ? '自动刷新: 开启 (5s)' : '自动刷新: 暂停';
        var autoBtnClass = autoRefreshEnabled ? 'btn btn-primary' : 'btn btn-ghost';

        var html = '';
        // 顶部操作栏
        html += '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">';
        html += '  <div style="font-size:14px;color:var(--muted);">实时采样监控点数: ' + historyData.length + ' / ' + maxHistoryPoints + '</div>';
        html += '  <div style="display:flex;gap:8px;">';
        html += '    <button id="metricsAutoBtn" class="' + autoBtnClass + '" onclick="toggleMetricsAutoRefresh()">' + autoBtnText + '</button>';
        html += '    <button class="btn btn-ghost" onclick="loadMetrics(false)">手动刷新</button>';
        html += '  </div>';
        html += '</div>';

        // 核心 KPI 卡片
        html += '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:20px;">';
        html += '  <div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">实时在线玩家</div><div style="font-size:30px;font-weight:bold;color:#10b981;margin-top:6px;">' + (d.onlineUsers || 0) + '</div></div>';
        html += '  <div class="card" style="margin:0;text-align:center;cursor:pointer;" onclick="switchTab(\'tables\')" title="点击直达实时桌况巡检透视"><div style="color:var(--muted);font-size:13px;">活跃对局桌数 ↗</div><div style="font-size:30px;font-weight:bold;color:#3b82f6;margin-top:6px;">' + (d.activeTables || 0) + '</div></div>';
        html += '  <div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">累计创建桌数</div><div style="font-size:30px;font-weight:bold;color:#f59e0b;margin-top:6px;">' + (d.tablesCreatedTotal || 0) + '</div></div>';
        html += '  <div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">系统注册用户</div><div style="font-size:30px;font-weight:bold;color:#8b5cf6;margin-top:6px;">' + displayUsers + '</div></div>';
        html += '  <div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">JVM 堆内存</div><div style="font-size:30px;font-weight:bold;color:#ec4899;margin-top:6px;">' + (jvm.usedMemoryMb || 0) + ' <span style="font-size:14px;color:var(--muted);">MB</span></div></div>';
        html += '  <div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">持续运行时长</div><div style="font-size:18px;font-weight:bold;color:var(--ink);margin-top:12px;">' + formatUptime(jvm.uptimeSeconds || 0) + '</div></div>';
        html += '</div>';

        // 动态图表看板区 (Canvas 实时渲染)
        html += '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;margin-bottom:20px;">';

        // 1. 实时走势折线图
        html += '  <div class="card" style="margin:0;">';
        html += '    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">';
        html += '      <h3 style="font-size:15px;margin:0;color:var(--ink);">在线与桌况动态走势</h3>';
        html += '      <div style="font-size:12px;color:var(--muted);"><span style="color:#10b981;">● 在线人数</span> &nbsp; <span style="color:#3b82f6;">● 活跃桌数</span></div>';
        html += '    </div>';
        html += '    <canvas id="chartTrend" width="460" height="200" style="width:100%;height:200px;display:block;border-radius:6px;background:rgba(0,0,0,0.03);"></canvas>';
        html += '  </div>';

        // 2. 对局玩法结算分布环形图
        html += '  <div class="card" style="margin:0;">';
        html += '    <h3 style="font-size:15px;margin-bottom:12px;color:var(--ink);">各棋牌玩法局数结算占比</h3>';
        html += '    <div style="display:flex;align-items:center;justify-content:space-around;">';
        html += '      <canvas id="chartGames" width="200" height="200" style="width:180px;height:180px;display:block;"></canvas>';
        html += '      <div id="gamesLegend" style="font-size:13px;line-height:1.8;"></div>';
        html += '    </div>';
        html += '  </div>';

        // 3. JVM 性能仪表环
        html += '  <div class="card" style="margin:0;">';
        html += '    <h3 style="font-size:15px;margin-bottom:12px;color:var(--ink);">JVM 内存与处理器健康度</h3>';
        html += '    <div style="display:flex;align-items:center;justify-content:space-around;">';
        html += '      <canvas id="chartJvm" width="200" height="200" style="width:180px;height:180px;display:block;"></canvas>';
        html += '      <div style="font-size:13px;line-height:1.8;color:var(--ink);">';
        html += '        <div>堆内存: <strong>' + (jvm.usedMemoryMb || 0) + ' / ' + (jvm.maxMemoryMb || 1) + ' MB</strong></div>';
        html += '        <div>活跃线程: <strong style="color:#3b82f6;">' + (jvm.threadCount || 0) + '</strong> 个</div>';
        html += '        <div>处理器核心: <strong>' + (jvm.availableProcessors || 0) + '</strong> 核</div>';
        html += '        <div>调度轮次: <strong>' + (d.tableLoopsTotal || 0) + '</strong></div>';
        html += '      </div>';
        html += '    </div>';
        html += '  </div>';
        html += '</div>';

        // 详细数据表格
        html += '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;">';
        html += '  <div class="card" style="margin:0;">';
        html += '    <h3 style="font-size:15px;margin-bottom:12px;color:var(--ink);">认证与调度详细度量</h3>';
        html += '    <div class="table-scroll"><table style="width:100%;">';
        html += '      <thead><tr><th>度量项目</th><th>数值</th></tr></thead><tbody>';
        html += '      <tr><td>斗地主结算局数</td><td><strong>' + (settles.ddz || 0) + '</strong></td></tr>';
        html += '      <tr><td>跑得快结算局数</td><td><strong>' + (settles.pdk || 0) + '</strong></td></tr>';
        html += '      <tr><td>麻将结算局数</td><td><strong>' + (settles.mj || 0) + '</strong></td></tr>';
        html += '      <tr><td>拖拉机结算局数</td><td><strong>' + (settles.tractor || 0) + '</strong></td></tr>';
        html += '      <tr><td>登录成功次数</td><td><span style="color:#10b981;font-weight:bold;">' + (logins.success || 0) + '</span></td></tr>';
        html += '      <tr><td>登录失败次数</td><td><span style="color:#ef4444;font-weight:bold;">' + (logins.fail || 0) + '</span></td></tr>';
        html += '    </tbody></table></div>';
        html += '  </div>';
        html += '</div>';

        el.innerHTML = html;

        // 渲染 Canvas 动态图表
        renderTrendChart();
        renderGamesChart(settles);
        renderJvmGauge(jvm);
    }

    function renderTrendChart() {
        var canvas = document.getElementById('chartTrend');
        if (!canvas) return;
        var ctx = canvas.getContext('2d');
        var w = canvas.width;
        var h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        var padL = 36, padR = 16, padT = 20, padB = 26;
        var plotW = w - padL - padR;
        var plotH = h - padT - padB;

        // 计算最大值
        var maxVal = 5;
        for (var i = 0; i < historyData.length; i++) {
            if (historyData[i].online > maxVal) maxVal = historyData[i].online;
            if (historyData[i].tables > maxVal) maxVal = historyData[i].tables;
        }
        maxVal = Math.ceil(maxVal * 1.25);

        // 绘制背景参考网格
        ctx.strokeStyle = 'rgba(150,150,150,0.18)';
        ctx.lineWidth = 1;
        ctx.fillStyle = 'rgba(150,150,150,0.7)';
        ctx.font = '10px monospace';
        ctx.textAlign = 'right';

        var gridSteps = 4;
        for (var g = 0; g <= gridSteps; g++) {
            var y = padT + plotH - (plotH * (g / gridSteps));
            ctx.beginPath();
            ctx.moveTo(padL, y);
            ctx.lineTo(w - padR, y);
            ctx.stroke();
            var valLabel = Math.round((maxVal * g) / gridSteps);
            ctx.fillText(String(valLabel), padL - 6, y + 3);
        }

        if (historyData.length < 2) {
            ctx.fillStyle = 'rgba(150,150,150,0.6)';
            ctx.textAlign = 'center';
            ctx.fillText('正在采集时序采样点...', w / 2, h / 2);
            return;
        }

        function drawSeries(key, color, fillColor) {
            ctx.beginPath();
            var firstX = padL, firstY = 0;
            for (var idx = 0; idx < historyData.length; idx++) {
                var x = padL + (plotW * (idx / (historyData.length - 1)));
                var y = padT + plotH - (plotH * (historyData[idx][key] / maxVal));
                if (idx === 0) {
                    firstX = x;
                    firstY = y;
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            }
            ctx.strokeStyle = color;
            ctx.lineWidth = 2.5;
            ctx.stroke();

            // 填充渐变半透明色
            ctx.lineTo(padL + plotW, padT + plotH);
            ctx.lineTo(padL, padT + plotH);
            ctx.closePath();
            var grad = ctx.createLinearGradient(0, padT, 0, padT + plotH);
            grad.addColorStop(0, fillColor);
            grad.addColorStop(1, 'rgba(0,0,0,0)');
            ctx.fillStyle = grad;
            ctx.fill();

            // 数据点高亮
            for (var p = 0; p < historyData.length; p++) {
                var px = padL + (plotW * (p / (historyData.length - 1)));
                var py = padT + plotH - (plotH * (historyData[p][key] / maxVal));
                ctx.fillStyle = color;
                ctx.beginPath();
                ctx.arc(px, py, 3.5, 0, Math.PI * 2);
                ctx.fill();
            }
        }

        drawSeries('online', '#10b981', 'rgba(16,185,129,0.22)');
        drawSeries('tables', '#3b82f6', 'rgba(59,130,246,0.22)');
    }

    function renderGamesChart(settles) {
        var canvas = document.getElementById('chartGames');
        var legendEl = document.getElementById('gamesLegend');
        if (!canvas) return;
        var ctx = canvas.getContext('2d');
        var w = canvas.width;
        var h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        var data = [
            { name: '斗地主', val: settles.ddz || 0, color: '#f59e0b' },
            { name: '跑得快', val: settles.pdk || 0, color: '#3b82f6' },
            { name: '麻将', val: settles.mj || 0, color: '#10b981' },
            { name: '拖拉机', val: settles.tractor || 0, color: '#ec4899' }
        ];

        var total = 0;
        for (var i = 0; i < data.length; i++) total += data[i].val;

        var cx = w / 2;
        var cy = h / 2;
        var r = Math.min(cx, cy) - 14;
        var innerR = r * 0.58;

        if (total === 0) {
            ctx.strokeStyle = 'rgba(150,150,150,0.25)';
            ctx.lineWidth = 14;
            ctx.beginPath();
            ctx.arc(cx, cy, (r + innerR) / 2, 0, Math.PI * 2);
            ctx.stroke();
            ctx.fillStyle = 'rgba(150,150,150,0.6)';
            ctx.font = '12px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('暂无对局', cx, cy + 4);
            if (legendEl) legendEl.innerHTML = '<div style="color:var(--muted);">无结算数据</div>';
            return;
        }

        var startAngle = -Math.PI / 2;
        var legendHtml = '';
        for (var d = 0; d < data.length; d++) {
            var item = data[d];
            var slice = (item.val / total) * Math.PI * 2;
            var endAngle = startAngle + slice;

            ctx.beginPath();
            ctx.arc(cx, cy, r, startAngle, endAngle);
            ctx.arc(cx, cy, innerR, endAngle, startAngle, true);
            ctx.closePath();
            ctx.fillStyle = item.color;
            ctx.fill();

            startAngle = endAngle;

            var pct = Math.round((item.val / total) * 100);
            legendHtml += '<div><span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:' + item.color + ';margin-right:6px;"></span>' +
                item.name + ': <strong>' + item.val + '</strong> (' + pct + '%)</div>';
        }

        // 中心总计
        ctx.fillStyle = 'var(--ink, #1f2937)';
        ctx.font = 'bold 16px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(String(total), cx, cy - 2);
        ctx.font = '10px sans-serif';
        ctx.fillStyle = 'rgba(150,150,150,0.8)';
        ctx.fillText('总对局', cx, cy + 14);

        if (legendEl) legendEl.innerHTML = legendHtml;
    }

    function renderJvmGauge(jvm) {
        var canvas = document.getElementById('chartJvm');
        if (!canvas) return;
        var ctx = canvas.getContext('2d');
        var w = canvas.width;
        var h = canvas.height;
        ctx.clearRect(0, 0, w, h);

        var used = jvm.usedMemoryMb || 0;
        var max = jvm.maxMemoryMb || 1;
        var pct = Math.min(100, Math.max(0, Math.round((used / max) * 100)));

        var cx = w / 2;
        var cy = h / 2 + 10;
        var r = Math.min(cx, cy) - 20;

        var startA = Math.PI * 0.8;
        var endA = Math.PI * 2.2;
        var totalAngle = endA - startA;

        // 背景灰弧
        ctx.beginPath();
        ctx.arc(cx, cy, r, startA, endA);
        ctx.strokeStyle = 'rgba(150,150,150,0.2)';
        ctx.lineWidth = 14;
        ctx.lineCap = 'round';
        ctx.stroke();

        // 占用进度弧
        var progA = startA + (totalAngle * (pct / 100));
        ctx.beginPath();
        ctx.arc(cx, cy, r, startA, progA);
        var color = pct > 85 ? '#ef4444' : (pct > 65 ? '#f59e0b' : '#10b981');
        ctx.strokeStyle = color;
        ctx.lineWidth = 14;
        ctx.lineCap = 'round';
        ctx.stroke();

        // 中心百分比
        ctx.fillStyle = color;
        ctx.font = 'bold 24px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(pct + '%', cx, cy - 6);
        ctx.font = '11px sans-serif';
        ctx.fillStyle = 'rgba(150,150,150,0.8)';
        ctx.fillText('堆内存负载', cx, cy + 14);
    }
})(window);
