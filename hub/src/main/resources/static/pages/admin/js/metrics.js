(function (w) {
    'use strict';

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

    w.loadMetrics = function () {
        var el = document.getElementById('metricsContainer');
        if (!el) return;
        el.innerHTML = '<div style="color:var(--muted);padding:16px;">正在拉取系统监控数据...</div>';

        Admin.get('/metrics').then(function (res) {
            if (!res || res.code !== 0 || !res.data) {
                el.innerHTML = '<div class="msg err">获取系统监控指标失败：' + (res && res.msg ? res.msg : '网络错误') + '</div>';
                return;
            }
            var d = res.data;
            var jvm = d.jvm || {};
            var settles = d.roundsSettled || {};
            var logins = d.logins || {};

            var html = '';
            // 实时业务指标
            var displayUsers = d.totalUsers !== undefined ? d.totalUsers : (d.registerSuccessTotal || 0);
            html += '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:20px;">';
            html += '<div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">实时在线玩家</div><div style="font-size:28px;font-weight:bold;color:var(--accent);margin-top:6px;">' + (d.onlineUsers || 0) + '</div></div>';
            html += '<div class="card" style="margin:0;text-align:center;cursor:pointer;" onclick="switchTab(\'tables\')" title="点击直达实时桌况巡检透视"><div style="color:var(--muted);font-size:13px;">活跃对局桌子 ↗</div><div style="font-size:28px;font-weight:bold;color:var(--ok);margin-top:6px;">' + (d.activeTables || 0) + '</div></div>';
            html += '<div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">累计创建桌数</div><div style="font-size:28px;font-weight:bold;color:#e6a23c;margin-top:6px;">' + (d.tablesCreatedTotal || 0) + '</div></div>';
            html += '<div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">累计解散桌数</div><div style="font-size:28px;font-weight:bold;color:#909399;margin-top:6px;">' + (d.tablesDestroyedTotal || 0) + '</div></div>';
            html += '<div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">系统注册用户</div><div style="font-size:28px;font-weight:bold;color:#67c23a;margin-top:6px;">' + displayUsers + '</div></div>';
            html += '<div class="card" style="margin:0;text-align:center;"><div style="color:var(--muted);font-size:13px;">牌桌调度轮次</div><div style="font-size:28px;font-weight:bold;color:#409eff;margin-top:6px;">' + (d.tableLoopsTotal || 0) + '</div></div>';
            html += '</div>';

            // 详细数据看板
            html += '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px;">';

            // 左边：玩法与认证数据
            html += '<div class="card" style="margin:0;">';
            html += '<h3 style="font-size:16px;margin-bottom:12px;color:var(--ink);">对局与认证度量</h3>';
            html += '<div class="table-scroll"><table style="width:100%;">';
            html += '<thead><tr><th>度量项目</th><th>数值</th></tr></thead><tbody>';
            html += '<tr><td>斗地主结算局数</td><td><strong>' + (settles.ddz || 0) + '</strong></td></tr>';
            html += '<tr><td>跑得快结算局数</td><td><strong>' + (settles.pdk || 0) + '</strong></td></tr>';
            html += '<tr><td>麻将结算局数</td><td><strong>' + (settles.mj || 0) + '</strong></td></tr>';
            html += '<tr><td>拖拉机结算局数</td><td><strong>' + (settles.tractor || 0) + '</strong></td></tr>';
            html += '<tr><td>累计创建桌数</td><td><strong>' + (d.tablesCreatedTotal || 0) + '</strong></td></tr>';
            html += '<tr><td>牌桌销毁解散总数</td><td><strong>' + (d.tablesDestroyedTotal || 0) + '</strong></td></tr>';
            html += '<tr><td>牌桌主循环调度总轮次</td><td><strong>' + (d.tableLoopsTotal || 0) + '</strong></td></tr>';
            html += '<tr><td>系统注册用户总数</td><td><strong>' + (d.totalUsers !== undefined ? d.totalUsers : '-') + '</strong></td></tr>';
            html += '<tr><td>本次运行新注册数</td><td><strong>' + (d.registerSuccessTotal || 0) + '</strong></td></tr>';
            html += '<tr><td>系统总邀请码数</td><td><strong>' + (d.totalInvites !== undefined ? d.totalInvites : '-') + '</strong></td></tr>';
            html += '<tr><td>登录成功次数</td><td><span style="color:var(--ok);font-weight:bold;">' + (logins.success || 0) + '</span></td></tr>';
            html += '<tr><td>登录失败次数</td><td><span style="color:var(--bad);font-weight:bold;">' + (logins.fail || 0) + '</span></td></tr>';
            html += '</tbody></table></div></div>';

            // 右边：JVM 基础指标
            html += '<div class="card" style="margin:0;">';
            html += '<h3 style="font-size:16px;margin-bottom:12px;color:var(--ink);">JVM 运行时性能</h3>';
            html += '<div class="table-scroll"><table style="width:100%;">';
            html += '<thead><tr><th>运行时指标</th><th>当前状态</th></tr></thead><tbody>';
            html += '<tr><td>堆内存占用</td><td><strong>' + (jvm.usedMemoryMb || 0) + ' MB</strong> / ' + (jvm.maxMemoryMb || 0) + ' MB</td></tr>';
            html += '<tr><td>活跃线程数</td><td><strong>' + (jvm.threadCount || 0) + '</strong> 个</td></tr>';
            html += '<tr><td>可用处理器核心</td><td>' + (jvm.availableProcessors || 0) + ' 核</td></tr>';
            html += '<tr><td>服务连续运行</td><td>' + formatUptime(jvm.uptimeSeconds || 0) + '</td></tr>';
            html += '</tbody></table></div></div>';

            html += '</div>';
            el.innerHTML = html;
        }).catch(function (err) {
            el.innerHTML = '<div class="msg err">拉取失败：' + (err.message || '未知异常') + '</div>';
        });
    };
})(window);
