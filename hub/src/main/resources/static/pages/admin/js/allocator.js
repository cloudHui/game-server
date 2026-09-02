(function (w) {
    'use strict';
    var lastResult = null;

    function byId(id) {
        return document.getElementById(id);
    }

    function escapeHtml(value) {
        var node = document.createElement('div');
        node.textContent = value == null ? '' : String(value);
        return node.innerHTML;
    }

    function numberText(value) {
        var number = Number(value);
        return isFinite(number) ? number.toFixed(2) : '--';
    }

    function parseKnownValues() {
        var raw = byId('allocatorKnown').value.trim();
        if (!raw) return [];
        var parts = raw.split(/[\s,，]+/);
        if (parts.length > 5) throw new Error('最多只能输入 5 个已知值');
        return parts.map(function (part) {
            if (!/^\d+$/.test(part)) throw new Error('已知值必须是大于等于 0 的整数');
            var value = Number(part);
            if (!isFinite(value) || value > 2147483647) throw new Error('已知值超出整数范围');
            return value;
        });
    }

    function readAverage(id, label, optional) {
        var raw = byId(id).value.trim();
        if (optional && !raw) return null;
        var value = Number(raw);
        if (!raw || !isFinite(value) || value < 0) {
            throw new Error(label + '必须是大于等于 0 的有效数字');
        }
        return value;
    }

    function metric(label, value, suffix) {
        return '<div class="tool-metric"><span class="tool-metric-label">' + escapeHtml(label) +
            '</span><strong class="tool-metric-value">' + escapeHtml(value) +
            (suffix ? '<small>' + escapeHtml(suffix) + '</small>' : '') + '</strong></div>';
    }

    function renderResult(data) {
        var values = data.values || [];
        var knownCount = Number(data.knownCount) || 0;
        var localText = data.subStartIndex ? '第 ' + data.subStartIndex + '—' + (Number(data.subStartIndex) + 2) + ' 项' : '未设置';
        byId('allocatorMetrics').innerHTML = [
            metric('目标总和', data.totalTargetSum, '整数'),
            metric('实际总和', data.totalSum, '整数'),
            metric('连续 3 项窗口', localText, '')
        ].join('');
        byId('allocatorValues').innerHTML = values.map(function (value, index) {
            var known = index < knownCount;
            return '<div class="allocator-value' + (known ? ' known' : '') + '">' +
                '<span class="allocator-value-label">第 ' + (index + 1) + ' 项</span>' +
                '<strong>' + escapeHtml(value) + '</strong>' +
                '<em>' + (known ? '已知输入' : '推算分配') + '</em></div>';
        }).join('');
        var checks = ['<div class="allocator-check"><span>✓</span><div><strong>总均值校验</strong><p>总和 ' +
            escapeHtml(data.totalSum) + ' / 目标 ' + escapeHtml(data.totalTargetSum) +
            '，实际均值 ' + numberText(data.actualTotalAverage) + '</p></div></div>'];
        if (data.subStartIndex) {
            checks.push('<div class="allocator-check"><span>✓</span><div><strong>局部均值校验</strong><p>第 ' +
                data.subStartIndex + '—' + (Number(data.subStartIndex) + 2) + ' 项总和 ' +
                escapeHtml(data.subSum) + ' / 目标 ' + escapeHtml(data.subTargetSum) +
                '，实际均值 ' + numberText(data.actualSubAverage) + '</p></div></div>');
            byId('allocatorResultHint').textContent = '已自动锁定第 ' + data.subStartIndex + ' 个位置开始的连续 3 项。';
        } else {
            byId('allocatorResultHint').textContent = '已按总均值完成 6 个整数的均匀分配。';
        }
        byId('allocatorChecks').innerHTML = checks.join('');
        byId('allocatorResult').hidden = false;
    }

    w.calculateAllocator = function () {
        var button = byId('allocatorCalculate');
        var payload;
        try {
            payload = {
                knownValues: parseKnownValues(),
                totalAverage: readAverage('allocatorTotalAverage', '总期望均值', false),
                subAverage: readAverage('allocatorSubAverage', '连续 3 个值的期望均值', true)
            };
        } catch (error) {
            byId('allocatorResult').hidden = true;
            return Admin.msg('allocatorMsg', error.message, false);
        }
        button.disabled = true;
        Admin.msg('allocatorMsg', '正在计算…');
        Admin.post('/integer-allocator/calculate', payload).then(function (data) {
            if (data.code !== 0) {
                lastResult = null;
                byId('allocatorResult').hidden = true;
                return Admin.msg('allocatorMsg', data.msg || '无法计算', false);
            }
            lastResult = data;
            renderResult(data);
            Admin.msg('allocatorMsg', '计算完成，可复制结果。', true);
        }).catch(function () {
            lastResult = null;
            byId('allocatorResult').hidden = true;
            Admin.msg('allocatorMsg', '网络错误，请稍后重试', false);
        }).finally(function () {
            button.disabled = false;
        });
    };

    w.resetAllocator = function () {
        byId('allocatorKnown').value = '';
        byId('allocatorTotalAverage').value = '10';
        byId('allocatorSubAverage').value = '';
        byId('allocatorResult').hidden = true;
        lastResult = null;
        Admin.msg('allocatorMsg', '');
    };

    w.copyAllocatorResult = function () {
        if (!lastResult) return;
        var text = '整数分配结果：' + (lastResult.values || []).join('、') +
            '\n总和：' + lastResult.totalSum + '，实际均值：' + numberText(lastResult.actualTotalAverage);
        if (lastResult.subStartIndex) {
            text += '\n局部窗口：第 ' + lastResult.subStartIndex + '—' + (Number(lastResult.subStartIndex) + 2) +
                ' 项，实际均值：' + numberText(lastResult.actualSubAverage);
        }
        if (!navigator.clipboard) return AppDialog.prompt('计算结果', text, '复制结果');
        navigator.clipboard.writeText(text).then(function () {
            Admin.msg('allocatorMsg', '结果已复制', true);
        });
    };
})(window);

