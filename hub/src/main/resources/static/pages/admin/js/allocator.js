(function (w) {
    'use strict';
    var lastResult = null;
    var MAX_VALUE = 99999;
    var VALUE_COUNT = 6;
    var Tools = w.AdminTools;
    var byId = Tools.byId;
    var escapeHtml = Tools.escapeHtml;
    var numberText = Tools.numberText;

    function knownInputs() {
        return Array.prototype.slice.call(document.querySelectorAll('.allocator-known-input'));
    }

    function resultInputs() {
        return Array.prototype.slice.call(document.querySelectorAll('.allocator-result-input'));
    }

    function integerValue(raw, label) {
        return Tools.nonNegativeInteger(raw, label, MAX_VALUE);
    }

    function readKnownValues() {
        var values = [];
        var count = 0;
        knownInputs().forEach(function (input, index) {
            var raw = input.value.trim();
            if (!raw) {
                values.push(null);
                return;
            }
            values.push(integerValue(raw, '第 ' + (index + 1) + ' 个月数值'));
            count++;
        });
        if (count > 5) throw new Error('最多只能填写 5 个已知数值，第 6 个留给系统计算');
        return values.length === VALUE_COUNT ? values : values.concat([null, null, null, null, null, null]).slice(0, VALUE_COUNT);
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

    function sum(values, start, length) {
        var from = start || 0;
        var end = length == null ? values.length : from + length;
        var total = 0;
        for (var i = from; i < end; i++) total += Number(values[i]) || 0;
        return total;
    }

    function currentValues() {
        var values = [];
        var invalid = false;
        resultInputs().forEach(function (input, index) {
            try {
                values.push(integerValue(input.value, '第 ' + (index + 1) + ' 个月结果'));
                input.classList.remove('is-invalid');
            } catch (error) {
                invalid = true;
                values.push(null);
                input.classList.add('is-invalid');
            }
        });
        return {values: values, invalid: invalid || values.length !== VALUE_COUNT || values.some(function (value) { return value === null; })};
    }

    function hasSubConstraint() {
        return lastResult && Number(lastResult.subStartIndex) > 0;
    }

    function rangeText(startIndex) {
        return '第 ' + startIndex + '—' + (Number(startIndex) + 2) + ' 个月';
    }

    function narrativeText(values) {
        var totalSum = sum(values);
        var totalAverage = totalSum / VALUE_COUNT;
        var totalValues = values.join('、');
        if (hasSubConstraint()) {
            var start = Number(lastResult.subStartIndex) - 1;
            var localValues = values.slice(start, start + 3);
            var localSum = sum(values, start, 3);
            return '从第 ' + lastResult.subStartIndex + ' 个月开始，取 ' + rangeText(lastResult.subStartIndex) +
                '（' + localValues.join('、') + '），3 个月平均值是 ' + numberText(localSum / 3) +
                '；6 个月数值（' + totalValues + '）平均值是 ' + numberText(totalAverage) + '。';
        }
        return '未设置连续 3 个月均值；6 个月数值（' + totalValues + '）平均值是 ' + numberText(totalAverage) + '。';
    }

    function check(title, message, valid) {
        return '<div class="allocator-check ' + (valid ? 'is-ok' : 'is-warn') + '"><span>' +
            (valid ? '✓' : '!') + '</span><div><strong>' + escapeHtml(title) + '</strong><p>' +
            escapeHtml(message) + '</p></div></div>';
    }

    function renderSummary() {
        if (!lastResult) return;
        var state = currentValues();
        var values = state.values;
        var narrative = byId('allocatorNarrative');
        if (state.invalid) {
            byId('allocatorMetrics').innerHTML = [
                metric('6 个月平均值', '--', '请补全结果'),
                metric('连续 3 个月平均值', '--', hasSubConstraint() ? rangeText(lastResult.subStartIndex) : '未设置'),
                metric('6 个月总和', '--', '')
            ].join('');
            narrative.className = 'allocator-narrative is-invalid';
            narrative.textContent = '六个结果框都要填写 0 到 99999 的整数，填写完整后自动重算。';
            byId('allocatorChecks').innerHTML = '';
            return;
        }

        var totalSum = sum(values);
        var actualTotalAverage = totalSum / VALUE_COUNT;
        var targetTotalSum = Number(lastResult.totalTargetSum);
        var totalValid = totalSum === targetTotalSum;
        var checks = [check('6 个月均值', '总和 ' + totalSum + ' / 6，实际平均值 ' + numberText(actualTotalAverage) +
            '；目标平均值 ' + numberText(lastResult.requestedTotalAverage), totalValid)];
        var subAverage = '--';
        if (hasSubConstraint()) {
            var start = Number(lastResult.subStartIndex) - 1;
            var subSum = sum(values, start, 3);
            subAverage = numberText(subSum / 3);
            checks.push(check('连续 3 个月均值', rangeText(lastResult.subStartIndex) + '总和 ' + subSum + ' / 3，实际平均值 ' +
                subAverage + '；目标平均值 ' + numberText(lastResult.requestedSubAverage), subSum === Number(lastResult.subTargetSum)));
        }
        byId('allocatorMetrics').innerHTML = [
            metric('6 个月平均值', numberText(actualTotalAverage), '目标 ' + numberText(lastResult.requestedTotalAverage)),
            metric('连续 3 个月平均值', subAverage, hasSubConstraint() ? rangeText(lastResult.subStartIndex) : '未设置'),
            metric('6 个月总和', totalSum, totalValid ? '符合目标' : '已修改')
        ].join('');
        narrative.className = 'allocator-narrative';
        narrative.textContent = narrativeText(values);
        byId('allocatorChecks').innerHTML = checks.join('');
        lastResult.values = values.slice();
        lastResult.totalSum = totalSum;
        lastResult.actualTotalAverage = actualTotalAverage;
        if (hasSubConstraint()) {
            lastResult.subSum = sum(values, Number(lastResult.subStartIndex) - 1, 3);
            lastResult.actualSubAverage = lastResult.subSum / 3;
        }
    }

    function renderResult(data, knownMask) {
        var values = (data.values || []).slice(0, VALUE_COUNT);
        while (values.length < VALUE_COUNT) values.push(0);
        lastResult = Object.assign({}, data, {values: values, knownMask: knownMask || []});
        byId('allocatorValues').innerHTML = values.map(function (value, index) {
            var known = !!lastResult.knownMask[index];
            return '<div class="allocator-value' + (known ? ' known' : '') + '">' +
                '<span class="allocator-value-label">第 ' + (index + 1) + ' 个月</span>' +
                '<input class="allocator-value-input allocator-result-input" type="text" inputmode="numeric" maxlength="5"' +
                ' data-index="' + index + '" value="' + escapeHtml(value) + '" aria-label="第 ' + (index + 1) + ' 个月结果">' +
                '<em>' + (known ? '已知输入' : '系统补齐') + '</em></div>';
        }).join('');
        byId('allocatorResult').hidden = false;
        byId('allocatorResultHint').textContent = hasSubConstraint()
            ? '系统从第 ' + data.subStartIndex + ' 个月开始匹配连续 3 个月均值；修改结果后实际均值即时更新。'
            : '未设置连续 3 个月均值；修改结果后 6 个月实际均值即时更新。';
        renderSummary();
    }

    function updateKnownCount() {
        var count = knownInputs().filter(function (input) { return input.value.trim() !== ''; }).length;
        var badge = byId('allocatorKnownCount');
        badge.textContent = '已填 ' + count + ' / 5';
        badge.classList.toggle('is-full', count === 5);
        badge.classList.toggle('is-invalid', count > 5);
        return count;
    }

    function normalizeFiveDigits(input) {
        if (input.value.length > 5) input.value = input.value.slice(0, 5);
    }

    w.calculateAllocator = function () {
        var button = byId('allocatorCalculate');
        var payload;
        var knownMask;
        try {
            var slots = readKnownValues();
            knownMask = slots.map(function (value) { return value !== null; });
            payload = {
                knownValues: slots,
                totalAverage: readAverage('allocatorTotalAverage', '6 个月均值', false),
                subAverage: readAverage('allocatorSubAverage', '连续 3 个月均值', true)
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
            renderResult(data, knownMask);
            Admin.msg('allocatorMsg', '计算完成，可直接修改结果六格。', true);
        }).catch(function () {
            lastResult = null;
            byId('allocatorResult').hidden = true;
            Admin.msg('allocatorMsg', '网络错误，请稍后重试', false);
        }).finally(function () {
            button.disabled = false;
        });
    };

    w.resetAllocator = function () {
        knownInputs().forEach(function (input) { input.value = ''; input.classList.remove('is-invalid'); });
        byId('allocatorTotalAverage').value = '10';
        byId('allocatorSubAverage').value = '';
        updateKnownCount();
        byId('allocatorResult').hidden = true;
        lastResult = null;
        Admin.msg('allocatorMsg', '');
    };

    w.copyAllocatorResult = function () {
        if (!lastResult) return;
        var values = lastResult.values || [];
        var text = '六个月结果：' + values.join('、') + '\n' + narrativeText(values);
        if (!navigator.clipboard) return AppDialog.prompt('计算结果', text, '复制结果');
        navigator.clipboard.writeText(text).then(function () {
            Admin.msg('allocatorMsg', '结果已复制', true);
        });
    };

    knownInputs().forEach(function (input) {
        input.addEventListener('input', function () {
            normalizeFiveDigits(input);
            var count = updateKnownCount();
            if (count > 5) {
                input.value = '';
                updateKnownCount();
                Admin.msg('allocatorMsg', '最多只能填写 5 个已知数值，第 6 个留给系统计算', false);
            }
        });
    });
    byId('allocatorValues').addEventListener('input', function (event) {
        if (!event.target.classList.contains('allocator-result-input')) return;
        normalizeFiveDigits(event.target);
        renderSummary();
    });
    updateKnownCount();
})(window);
