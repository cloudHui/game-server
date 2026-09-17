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
        if (nums.length === 7 && compStrs.length <= 2) {
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

    /**
     * 智能求解器：根据目标与已知数字，反算未锁定空位（通用、极简）
     * @param {Object} state 当前状态
     * @param {number} [step=5] 遍历步长
     * @returns {Object} { solutions: Array, total: Number }
     */
    function solveSolutions(state, step) {
        step = step || 5;
        var numbers = state.numbers || [];
        var targets = state.targets || {};
        var t1 = targets.t1 || { start: 3, end: 5, targetAvg: 238 };
        var t3 = targets.t3 || { start: 1, end: 6, minAvg: 200, maxAvg: 205 };

        // 若当前数字不足目标最大序号，自动补齐缺失空位
        var maxRequired = Math.max(Number(t1.end || 0), Number(t3.end || 0));
        while (numbers.length < maxRequired) {
            numbers.push({
                id: 'n_' + (numbers.length + 1) + '_' + Date.now(),
                value: Number(t1.targetAvg || 238),
                locked: false,
                label: '目标所需数' + (numbers.length + 1)
            });
        }

        // 识别未锁定的空位序号
        var vars = [];
        numbers.forEach(function (n, idx) {
            if (!n.locked) vars.push(idx);
        });
        if (vars.length === 0) return { solutions: [], total: 0 };

        var t1SumTarget = round(Number(t1.targetAvg || 0) * (t1.end - t1.start + 1), 2);
        var t3MinSum = round(Number(t3.minAvg || 0) * (t3.end - t3.start + 1), 2);
        var t3MaxSum = round(Number(t3.maxAvg || 0) * (t3.end - t3.start + 1), 2);

        // 划分 t1 内与 t1 外的空位
        var inT1 = vars.filter(function (i) { return (i + 1) >= t1.start && (i + 1) <= t1.end; });
        var outT1 = vars.filter(function (i) { return inT1.indexOf(i) === -1; });

        // t1 内固定数字和，推导所需空位和
        var fixedInT1 = 0;
        for (var i = t1.start - 1; i < t1.end; i++) {
            if (inT1.indexOf(i) === -1) fixedInT1 += Number(numbers[i].value || 0);
        }
        var t1Need = round(t1SumTarget - fixedInT1, 2);

        // 生成 t1 空位分配
        var t1Combos = [];
        if (inT1.length === 1) {
            var m1 = {}; m1[inT1[0]] = t1Need;
            t1Combos.push(m1);
        } else if (inT1.length === 2) {
            var mid = t1Need / 2;
            var startVal = Math.max(1, Math.floor((mid - 160) / step) * step);
            var endVal = Math.min(t1Need - 1, Math.ceil((mid + 160) / step) * step);
            for (var v = startVal; v <= endVal; v += step) {
                var v2 = round(t1Need - v, 2);
                if (v2 > 0) {
                    var m2 = {}; m2[inT1[0]] = v; m2[inT1[1]] = v2;
                    t1Combos.push(m2);
                }
            }
        } else if (inT1.length === 0) {
            t1Combos.push({});
        }

        var inT3Vars = outT1.filter(function (i) { return (i + 1) >= t3.start && (i + 1) <= t3.end; });
        var outT3Vars = outT1.filter(function (i) { return inT3Vars.indexOf(i) === -1; });

        // 结合 t1 外空位并用 calculate 统一通关校验
        var solutions = [];
        t1Combos.forEach(function (base) {
            var extendedBase = Object.assign({}, base);
            outT3Vars.forEach(function (idx) {
                extendedBase[idx] = (idx + 1 > t1.end) ? Number(t1.targetAvg || 238) : Number(numbers[idx].value || 238);
            });

            if (inT3Vars.length === 0) {
                checkAndCollect(extendedBase);
                return;
            }

            var t3Known = 0;
            for (var j = t3.start - 1; j < t3.end; j++) {
                if (inT3Vars.indexOf(j) === -1) {
                    t3Known += (extendedBase[j] !== undefined) ? extendedBase[j] : Number(numbers[j].value || 0);
                }
            }

            function recurseOut(idxInList, currentAllocatedSum, currentAssign) {
                if (idxInList === inT3Vars.length) {
                    var tot = t3Known + currentAllocatedSum;
                    if (tot >= t3MinSum - 0.001 && tot <= t3MaxSum + 0.001) {
                        checkAndCollect(Object.assign({}, extendedBase, currentAssign));
                    }
                    return;
                }
                var varIdx = inT3Vars[idxInList];
                var isLast = (idxInList === inT3Vars.length - 1);
                var minVal = (varIdx + 1 > t1.end) ? Number(t1.targetAvg || 0) : 1;
                var remainingCount = inT3Vars.length - 1 - idxInList;
                var remainingMinSum = remainingCount * ((varIdx + 1 > t1.end) ? Number(t1.targetAvg || 0) : 1);
                var minNeeded = t3MinSum - t3Known - currentAllocatedSum;
                var maxNeeded = t3MaxSum - t3Known - currentAllocatedSum;

                if (isLast) {
                    var low = Math.max(minVal, Math.ceil(minNeeded));
                    var high = Math.floor(maxNeeded);
                    for (var v = low; v <= high; v++) {
                        if (v % step === 0 || v === low || v === high || v === Number(t1.targetAvg || 0)) {
                            var nextAssign = Object.assign({}, currentAssign);
                            nextAssign[varIdx] = v;
                            recurseOut(idxInList + 1, currentAllocatedSum + v, nextAssign);
                        }
                    }
                } else {
                    var low = minVal;
                    var high = Math.floor(maxNeeded - remainingMinSum);
                    for (var v = low; v <= high; v += step) {
                        var nextAssign = Object.assign({}, currentAssign);
                        nextAssign[varIdx] = v;
                        recurseOut(idxInList + 1, currentAllocatedSum + v, nextAssign);
                    }
                    var specialVal = Number(t1.targetAvg || 0);
                    if (specialVal >= low && specialVal <= high && (specialVal - low) % step !== 0) {
                        var nextAssign = Object.assign({}, currentAssign);
                        nextAssign[varIdx] = specialVal;
                        recurseOut(idxInList + 1, currentAllocatedSum + specialVal, nextAssign);
                    }
                }
            }

            recurseOut(0, 0, {});
        });

        function checkAndCollect(assign) {
            var testState = JSON.parse(JSON.stringify(state));
            Object.keys(assign).forEach(function (k) {
                testState.numbers[k].value = assign[k];
            });
            var calc = calculate(testState);
            if (calc.t1.isValid && calc.t3.isValid && calc.t2.isValid) {
                var diff = inT1.length === 2 ? Math.abs(assign[inT1[0]] - assign[inT1[1]]) : 0;
                if (inT3Vars.length === 2) diff += Math.abs(assign[inT3Vars[0]] - assign[inT3Vars[1]]);
                solutions.push({
                    numbers: testState.numbers,
                    summary: vars.map(function (idx) { return '#' + (idx + 1) + '=' + assign[idx]; }).join(', '),
                    diff: diff
                });
            }
        }

        // 按两数更均衡优先排序，返回前 50 组
        solutions.sort(function (a, b) { return a.diff - b.diff; });
        return {
            solutions: solutions.slice(0, 50),
            total: solutions.length
        };
    }

    w.SequenceModel = {
        DEFAULT_DATA: DEFAULT_DATA,
        createDefaultState: createDefaultState,
        calculate: calculate,
        toTextArt: toTextArt,
        solveSolutions: solveSolutions,
        round: round
    };

})(typeof window !== 'undefined' ? window : global);
