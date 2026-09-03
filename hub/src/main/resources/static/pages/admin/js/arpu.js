(function (w) {
    'use strict';
    var lastJson = '';
    var Tools = w.AdminTools;
    var byId = Tools.byId;
    var escapeHtml = Tools.escapeHtml;
    var numeric = Tools.numberOrNull;
    var numberText = Tools.numberText;

    function stat(label, value, foot, apiValue) {
        var api = numeric(apiValue);
        return '<div class="arpu-stat"><span class="arpu-stat-label">' + escapeHtml(label) +
            '</span><strong class="arpu-stat-value">' + escapeHtml(numberText(value)) +
            '</strong><span class="arpu-stat-foot">' + escapeHtml(foot) +
            (api === null ? '' : '<span class="arpu-stat-api">接口参考 ' + escapeHtml(api.toFixed(2)) + '</span>') +
            '</span></div>';
    }

    function renderStats(data, payload) {
        var calculated = data.calculated || {};
        var available3 = Number(calculated.available3) || 0;
        var available6 = Number(calculated.available6) || 0;
        byId('arpuStats').innerHTML = [
            stat('近 3 个月平均值', calculated.average3, '按 ' + available3 + ' 个可用月份计算', payload.avg_3),
            stat('近 6 个月平均值', calculated.average6, '按 ' + available6 + ' 个可用月份计算', payload.avg_6)
        ].join('');
    }

    function renderMonthly(payload) {
        var rows = Array.isArray(payload.arpu) ? payload.arpu : [];
        byId('arpuMonthly').innerHTML = rows.length ? rows.map(function (item) {
            var value = item && item.arpu;
            var parsed = numeric(value);
            return '<tr><td>' + escapeHtml(item && item.month ? item.month : '-') + '</td><td class="' +
                (parsed === null ? 'arpu-unavailable' : 'arpu-number') + '">' +
                escapeHtml(parsed === null ? (value || '暂不可用') : parsed.toFixed(2)) +
                '</td><td>' + (parsed === null ? '<span class="badge badge-bad">暂不可用</span>' :
                    '<span class="badge badge-ok">可计算</span>') + '</td></tr>';
        }).join('') : '<tr><td colspan="3">接口未返回月度明细</td></tr>';
    }

    function render(data) {
        var payload = data.data || {};
        lastJson = JSON.stringify(payload, null, 2);
        renderStats(data, payload);
        renderMonthly(payload);
        byId('arpuRawJson').textContent = lastJson;
        byId('arpuRequestUrl').textContent = data.requestUrl || '';
        byId('arpuRequestUrl').hidden = !data.requestUrl;
        byId('arpuResult').hidden = false;
    }

    w.checkArpu = function () {
        var phone = byId('arpuPhone').value.trim();
        var button = byId('arpuQueryButton');
        if (!/^1[3-9]\d{9}$/.test(phone)) {
            byId('arpuResult').hidden = true;
            return Admin.msg('arpuMsg', '请输入 11 位有效手机号', false);
        }
        button.disabled = true;
        Admin.msg('arpuMsg', '正在查询…');
        Admin.post('/arpu/check', {phoneNo: phone}).then(function (data) {
            if (data.code !== 0) {
                byId('arpuResult').hidden = true;
                return Admin.msg('arpuMsg', data.msg || '查询失败', false);
            }
            render(data);
            Admin.msg('arpuMsg', '查询完成，已展示原始 JSON 和计算结果。', true);
        }).catch(function () {
            byId('arpuResult').hidden = true;
            Admin.msg('arpuMsg', '网络错误，请稍后重试', false);
        }).finally(function () {
            button.disabled = false;
        });
    };

    w.copyArpuJson = function () {
        if (!lastJson) return;
        if (!navigator.clipboard) return AppDialog.prompt('原始 JSON', lastJson, '复制 JSON');
        navigator.clipboard.writeText(lastJson).then(function () {
            Admin.msg('arpuMsg', 'JSON 已复制', true);
        });
    };

    byId('arpuPhone').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') w.checkArpu();
    });
})(window);
