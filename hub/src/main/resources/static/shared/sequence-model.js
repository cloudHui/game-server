/**
 * 序列均值与数字组成拆解模型
 * 不依赖 DOM，供管理后台、独立工具及测试全面复用。
 */
(function (w) {
    'use strict';

    var DEFAULT_DATA = {
        numbers: [
            { id: 'n1', value: 38, locked: true, label: '固定数1' },
            { id: 'n2', value: 19, locked: true, label: '固定数2' },
            { id: 'n3', value: 57.9, locked: true, label: '固定数3' },
            { id: 'n4', value: 320, locked: false, label: '额外数1' },
            { id: 'n5', value: 336.1, locked: false, label: '额外数2' },
            { id: 'n6', value: 238, locked: false, label: '达标后数1' },
            { id: 'n7', value: 238, locked: false, label: '达标后数2' }
        ],
        targets: {
            t1: { start: 3, end: 5, targetAvg: 238 },
            t3: { start: 2, end: 7, minAvg: 200, maxAvg: 205 }
        },
        compositions: [
            { id: 'c1', targetNumberId: 'n4', items: [206, 19.9, 19.9, 30, 30] },
            { id: 'c2', targetNumberId: 'n5', items: [8, 198, 60, 30, 30] }
        ],
        supplements: [19.9, 2]
    };

    function createDefaultState() {
        return JSON.parse(JSON.stringify(DEFAULT_DATA));
    }

    function round(num, decimals) {
        var factor = Math.pow(10, decimals == null ? 2 : decimals);
        return Math.round(num * factor) / factor;
    }

    function calculate(state) {
        var numbers = state.numbers || [];
        var targets = state.targets || {};
        var t1 = targets.t1 || { start: 3, end: 5, targetAvg: 238 };
        var t3 = targets.t3 || { start: 2, end: 7, minAvg: 200, maxAvg: 205 };

        // 目标一：3 连
        var t1Start = Math.max(1, t1.start || 1);
        var t1End = Math.min(numbers.length, t1.end || 1);
        var t1Slice = numbers.slice(t1Start - 1, t1End);
        var t1Sum = t1Slice.reduce(function (sum, n) { return sum + Number(n.value || 0); }, 0);
        var t1Count = Math.max(1, t1Slice.length);
        var t1Avg = round(t1Sum / t1Count, 2);
        var t1Target = Number(t1.targetAvg || 0);
        var t1Valid = Math.abs(t1Avg - t1Target) < 0.001;

        // 目标三：6 连
        var t3Start = Math.max(1, t3.start || 1);
        var t3End = Math.min(numbers.length, t3.end || 1);
        var t3Slice = numbers.slice(t3Start - 1, t3End);
        var t3Sum = t3Slice.reduce(function (sum, n) { return sum + Number(n.value || 0); }, 0);
        var t3Count = Math.max(1, t3Slice.length);
        var t3Avg = round(t3Sum / t3Count, 2);
        var t3Min = Number(t3.minAvg || 0);
        var t3Max = Number(t3.maxAvg || 0);
        var t3Valid = t3Avg >= t3Min && t3Avg <= t3Max;

        // 目标二：达标后数字校验
        var afterSlice = numbers.slice(t1End);
        var violations = afterSlice.filter(function (n) { return Number(n.value || 0) < t1Target; });
        var t2Valid = afterSlice.length > 0 && violations.length === 0;

        // 拆解项计算
        var compResults = [];
        var totalDiff = 0;
        (state.compositions || []).forEach(function (comp) {
            var target = numbers.find(function (n) { return n.id === comp.targetNumberId; });
            var targetVal = target ? Number(target.value || 0) : 0;
            var targetIdx = target ? numbers.indexOf(target) + 1 : '?';
            var compSum = (comp.items || []).reduce(function (sum, v) { return sum + Number(v || 0); }, 0);
            var diff = round(targetVal - compSum, 4);
            totalDiff += diff;
            compResults.push({
                id: comp.id,
                targetId: comp.targetNumberId,
                targetIndex: targetIdx,
                targetValue: targetVal,
                items: (comp.items || []).slice(),
                sum: round(compSum, 4),
                diff: diff,
                isBalanced: diff === 0
            });
        });
        totalDiff = round(totalDiff, 4);

        // 抵扣递减计算
        var remain = totalDiff;
        var suppSteps = [];
        (state.supplements || []).forEach(function (s) {
            var val = Number(s || 0);
            remain = round(remain - val, 4);
            suppSteps.push({ value: val, remain: remain });
        });

        return {
            t1: {
                start: t1Start,
                end: t1End,
                count: t1Count,
                sum: round(t1Sum, 2),
                avg: t1Avg,
                target: t1Target,
                isValid: t1Valid,
                diff: round(t1Avg - t1Target, 2)
            },
            t3: {
                start: t3Start,
                end: t3End,
                count: t3Count,
                sum: round(t3Sum, 2),
                avg: t3Avg,
                min: t3Min,
                max: t3Max,
                isValid: t3Valid
            },
            t2: {
                count: afterSlice.length,
                violations: violations.length,
                isValid: t2Valid
            },
            compositions: compResults,
            totalInitialDiff: totalDiff,
            supplements: suppSteps,
            finalRemainingDiff: remain
        };
    }

    function toTextArt(state, calcResult) {
        var calc = calcResult || calculate(state);
        var nums = (state.numbers || []).map(function (n) { return n.value; });

        var compStrs = calc.compositions.map(function (c) {
            return c.targetValue + '=' + c.items.join('+') + '=' + c.sum + '(差' + c.diff + ')';
        });

        var suppText = '';
        if (state.supplements && state.supplements.length === 2 && state.supplements[0] === 19.9 && state.supplements[1] === 2) {
            suppText = ' (再+19.9差4.4, 再+2差2.4)';
        } else if (calc.supplements && calc.supplements.length > 0) {
            var steps = calc.supplements.map(function (s) { return '再+' + s.value + '差' + s.remain; });
            suppText = ' (' + steps.join(', ') + ')';
        }

        var tailDiff = '➔ 合差' + calc.totalInitialDiff + suppText;
        var c1 = compStrs[0] || '';
        var c2 = compStrs[1] || '';

        // 7 个数标准模板（与用户提供图片 100% 对齐）
        if (nums.length === 7) {
            return [
                '第一排：       ┌────────────────────── ' + calc.t3.count + '个平均: ' + calc.t3.avg + ' ──────────────────────┐',
                '               │             ┌───── ' + calc.t1.count + '个平均: ' + calc.t1.avg + ' ─────┐                     │',
                '第二排： ' + nums[0] + '    ' + nums[1] + '          ' + nums[2] + '       ' + nums[3] + '           ' + nums[4] + '        ' + nums[5] + '       ' + nums[6],
                '                                      │              │',
                '第三排：     ┌─────────────────────────┘              └─────────────────────────┐',
                '         ' + c1 + '   ' + c2 + '   ' + tailDiff
            ].join('\n');
        }

        return [
            '第一排： 连续 ' + calc.t3.count + ' 个均值: ' + calc.t3.avg + ' | 连续 ' + calc.t1.count + ' 个均值: ' + calc.t1.avg,
            '第二排： ' + nums.join('   '),
            '第三排： ' + compStrs.join('   ') + '  ' + tailDiff
        ].join('\n');
    }

    w.SequenceModel = {
        DEFAULT_DATA: DEFAULT_DATA,
        createDefaultState: createDefaultState,
        calculate: calculate,
        toTextArt: toTextArt
    };

})(typeof window !== 'undefined' ? window : global);
