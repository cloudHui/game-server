/**
 * 序列均值与数字组成联动分析器 - 独立页面交互控制器
 * 依托 w.SequenceModel 完成运算与三排连线文本格式化，本控制器专注视图渲染与交互逻辑。
 */
(function (w) {
    'use strict';

    var Model = w.SequenceModel;
    if (!Model) {
        console.error('SequenceModel not loaded!');
        return;
    }

    function byId(id) {
        return document.getElementById(id);
    }

    function round(num, decimals) {
        if (Model && typeof Model.round === 'function') return Model.round(num, decimals);
        var factor = Math.pow(10, decimals == null ? 2 : decimals);
        return Math.round(num * factor) / factor;
    }

    var state = Model.createDefaultState();

    function showMsg(msg, isSuccess) {
        var el = byId('seqMsg');
        if (!el) {
            if (!isSuccess) alert(msg);
            return;
        }
        el.textContent = msg;
        el.className = 'msg ' + (isSuccess ? 'msg-success' : 'msg-error');
        el.style.display = 'block';
        clearTimeout(el._timer);
        el._timer = setTimeout(function () {
            el.style.display = 'none';
        }, 3000);
    }

    function renderAll() {
        var calc = Model.calculate(state);
        renderSequence(calc);
        renderCompositions(calc);
        renderSupplements();
        updateSummary(calc);
        updateArtPreview(calc);
        setTimeout(drawConnectionLines, 50);
    }

    // 渲染第二排数字卡片
    function renderSequence(calc) {
        var container = byId('seqSequenceContainer');
        if (!container) return;
        container.innerHTML = '';

        state.numbers.forEach(function (item, index) {
            var seqNum = index + 1;
            var inT1 = seqNum >= calc.t1.start && seqNum <= calc.t1.end;
            var inT3 = seqNum >= calc.t3.start && seqNum <= calc.t3.end;

            var card = document.createElement('div');
            card.id = 'seq-num-card-' + item.id;
            card.className = 'seq-num-card' +
                (item.locked ? ' is-locked' : '') +
                (inT1 && inT3 ? ' in-both' : inT1 ? ' in-t1' : inT3 ? ' in-t3' : '');

            card.innerHTML = [
                '<div class="seq-num-card-head">',
                '  <span class="seq-num-idx' + (item.locked ? ' is-locked' : '') + '">#' + seqNum + '</span>',
                '  <div class="seq-num-actions">',
                '    <button class="seq-icon-btn" onclick="addSequenceCompositionForNumber(\'' + item.id + '\')" title="为此数添加拆解框">🧩</button>',
                '    <button class="seq-icon-btn" onclick="toggleSequenceLock(\'' + item.id + '\')" title="' + (item.locked ? '点击解锁' : '点击锁定') + '">',
                item.locked ? '🔒' : '🔓',
                '    </button>',
                '    <button class="seq-icon-btn" onclick="deleteSequenceNumber(\'' + item.id + '\')" title="删除">✕</button>',
                '  </div>',
                '</div>',
                '<div class="seq-num-card-body">',
                '  <input class="tool-input" type="number" step="any" value="' + item.value + '" ' + (item.locked ? 'disabled' : '') + ' onchange="updateSequenceValue(\'' + item.id + '\', this.value)">',
                '</div>',
                '<div class="seq-num-tags">',
                item.locked ? '<span style="color:#e6a23c;">固定</span>' : '<span>可改</span>',
                inT1 ? '<span style="color:#409eff;font-weight:bold;">[3连]</span>' : '',
                inT3 ? '<span style="color:#67c23a;font-weight:bold;">[6连]</span>' : '',
                '</div>'
            ].join('');

            // 右键菜单支持
            card.oncontextmenu = function (e) {
                e.preventDefault();
                openContextMenu(e, item.id);
            };

            container.appendChild(card);
        });

        // 序列末尾的便捷加号卡片（当目标需要更多数字时高亮提示）
        var maxRequired = Math.max(state.targets.t1.end || 0, state.targets.t3.end || 0);
        var isMissing = state.numbers.length < maxRequired;
        var addBtn = document.createElement('button');
        addBtn.type = 'button';
        addBtn.className = 'seq-num-card' + (isMissing ? ' is-missing' : '');
        addBtn.style.borderStyle = 'dashed';
        addBtn.style.cursor = 'pointer';
        addBtn.style.display = 'flex';
        addBtn.style.flexDirection = 'column';
        addBtn.style.alignItems = 'center';
        addBtn.style.justifyContent = 'center';
        addBtn.style.gap = '4px';
        addBtn.onclick = function () {
            w.addSequenceNumber();
        };
        addBtn.innerHTML = isMissing
            ? '<span style="font-size:16px;">⚡</span><b style="font-size:11px;color:#e6a23c;">补全 #' + (state.numbers.length + 1) + '</b><span style="font-size:10px;color:#909399;">需至 #' + maxRequired + '</span>'
            : '<span style="font-size:18px;">➕</span><span style="font-size:12px;color:#909399;">添加数字</span><span style="font-size:10px;color:#606266;">#' + (state.numbers.length + 1) + '</span>';
        container.appendChild(addBtn);
    }

    // 渲染第三排拆解框
    function renderCompositions(calc) {
        var container = byId('seqCompositionsContainer');
        if (!container) return;
        container.innerHTML = '';

        calc.compositions.forEach(function (comp) {
            var compCard = document.createElement('div');
            compCard.id = 'seq-comp-card-' + comp.id;
            compCard.className = 'seq-comp-card';

            var itemsHtml = comp.items.map(function (val, idx) {
                return '<div class="seq-comp-item">' +
                    '<input type="number" step="any" value="' + val + '" onchange="updateSequenceCompItem(\'' + comp.id + '\', ' + idx + ', this.value)">' +
                    '<button class="seq-icon-btn" onclick="removeSequenceCompItem(\'' + comp.id + '\', ' + idx + ')">×</button>' +
                    '</div>';
            }).join('<b style="color:#909399;font-size:12px;">+</b>');

            var optionsHtml = state.numbers.map(function (n, i) {
                return '<option value="' + n.id + '" ' + (n.id === comp.targetId ? 'selected' : '') + '>#' + (i + 1) + ' (' + n.value + ')</option>';
            }).join('');

            compCard.innerHTML = [
                '<div class="seq-comp-head">',
                '  <div style="display:flex;align-items:center;gap:8px;font-size:12px;">',
                '    <span class="seq-badge seq-badge-purple">拆解 #' + comp.targetIndex + '</span>',
                '    <span>绑定目标:</span>',
                '    <select style="border:1px solid #dcdfe6;border-radius:4px;padding:2px 6px;font-size:12px;" onchange="bindSequenceCompTarget(\'' + comp.id + '\', this.value)">' + optionsHtml + '</select>',
                '    <strong style="font-family:ui-monospace,monospace;font-size:13px;">= ' + comp.targetValue + '</strong>',
                '  </div>',
                '  <button class="seq-icon-btn" style="color:#f56c6c;" onclick="deleteSequenceComposition(\'' + comp.id + '\')">删除 ✕</button>',
                '</div>',
                '<div class="seq-comp-items">',
                itemsHtml,
                '  <button class="btn btn-ghost btn-sm" type="button" style="padding:2px 8px;min-height:24px;font-size:11px;" onclick="addSequenceCompItem(\'' + comp.id + '\')">+ 加项</button>',
                '</div>',
                '<div class="seq-comp-summary">',
                '  <div>组成累加和: <strong style="font-family:ui-monospace,monospace;">' + comp.sum + '</strong></div>',
                '  <div style="display:flex;align-items:center;gap:6px;">' +
                   (comp.isBalanced ? '<span style="color:#67c23a;font-weight:bold;">✔ 刚好平衡</span>' : '<span style="color:#e6a23c;">还差: <strong style="color:#f56c6c;font-family:ui-monospace,monospace;font-size:14px;">' + comp.diff + '</strong></span><button class="btn btn-ghost btn-sm" type="button" style="padding:1px 6px;min-height:20px;font-size:11px;color:#409eff;" onclick="autoBalanceComp(\'' + comp.id + '\')">⚡ 补齐差额</button>') +
                '  </div>',
                '</div>'
            ].join('');

            container.appendChild(compCard);
        });
    }

    // 渲染抵扣递减项
    function renderSupplements() {
        var container = byId('seqSupplementsList');
        if (!container) return;
        container.innerHTML = '';

        state.supplements.forEach(function (val, idx) {
            var item = document.createElement('div');
            item.className = 'seq-supp-item';
            item.innerHTML = [
                '<span>+</span>',
                '<input type="number" step="any" value="' + val + '" onchange="updateSequenceSupplement(' + idx + ', this.value)">',
                '<button class="seq-icon-btn" onclick="removeSequenceSupplement(' + idx + ')">×</button>'
            ].join('');
            container.appendChild(item);
        });
    }

    // 刷新统计指标展示
    function updateSummary(calc) {
        byId('seqT1Sum').textContent = calc.t1.sum;
        byId('seqT1Avg').textContent = calc.t1.avg;
        byId('seqDispSum3').textContent = calc.t1.sum;
        byId('seqDispAvg3').textContent = calc.t1.avg;

        var bT1 = byId('seqBadgeT1');
        bT1.className = 'seq-badge ' + (calc.t1.isValid ? 'seq-badge-success' : 'seq-badge-warn');
        bT1.textContent = calc.t1.isValid ? '✔ 达标 ' + calc.t1.target : '差 ' + (calc.t1.diff > 0 ? '+' + calc.t1.diff : calc.t1.diff);

        byId('seqT3Sum').textContent = calc.t3.sum;
        byId('seqT3Avg').textContent = calc.t3.avg;
        byId('seqDispSum6').textContent = calc.t3.sum;
        byId('seqDispAvg6').textContent = calc.t3.avg;

        var bT3 = byId('seqBadgeT3');
        bT3.className = 'seq-badge ' + (calc.t3.isValid ? 'seq-badge-success' : 'seq-badge-danger');
        bT3.textContent = (calc.t3.isValid ? '✔ 达标 (' : '未达标 (') + calc.t3.avg + ')';

        byId('seqT1EndDisp').textContent = calc.t1.end;
        byId('seqT2Count').textContent = calc.t2.count;
        var bT2 = byId('seqBadgeT2');
        var sT2 = byId('seqT2Status');
        if (calc.t2.count === 0) {
            bT2.className = 'seq-badge seq-badge-info';
            bT2.textContent = '无后续数';
            sT2.textContent = '无后续数';
            sT2.className = 'seq-val-highlight';
        } else if (calc.t2.isValid) {
            bT2.className = 'seq-badge seq-badge-success';
            bT2.textContent = '✔ 全部满足';
            sT2.textContent = '✔ ' + calc.t2.count + ' 个数均 ≥ ' + calc.t1.target;
            sT2.className = 'seq-val-success';
        } else {
            bT2.className = 'seq-badge seq-badge-danger';
            bT2.textContent = '✖ ' + calc.t2.violations + '个未达标';
            sT2.textContent = '✖ 存在小于 ' + calc.t1.target + ' 的数';
            sT2.className = 'seq-val-highlight';
            sT2.style.color = '#f56c6c';
        }

        byId('seqTotalInitialDiff').textContent = calc.totalInitialDiff;
        byId('seqFinalRemainingDiff').textContent = calc.finalRemainingDiff;
    }

    function updateArtPreview(calc) {
        var preview = byId('seqArtPreview');
        if (preview) preview.textContent = Model.toTextArt(state, calc);
    }

    // 动态绘制 SVG 连线
    function drawConnectionLines() {
        var svg = byId('seqSvgConnections');
        var svgLayer = byId('seqSvgLayer');
        if (!svg || !svgLayer) return;
        var container = svgLayer.parentElement;
        if (!container) return;

        svgLayer.setAttribute('width', container.scrollWidth);
        svgLayer.setAttribute('height', container.scrollHeight);

        var cRect = container.getBoundingClientRect();
        svg.innerHTML = '';

        function getCenter(elem) {
            if (!elem) return null;
            var r = elem.getBoundingClientRect();
            return {
                x: r.left + r.width / 2 - cRect.left + container.scrollLeft,
                y: r.top + r.height / 2 - cRect.top + container.scrollTop,
                top: r.top - cRect.top + container.scrollTop,
                bottom: r.bottom - cRect.top + container.scrollTop
            };
        }

        // 6连连线
        var card6 = byId('seqCardAvg6');
        var start6 = state.numbers[state.targets.t3.start - 1];
        var end6 = state.numbers[state.targets.t3.end - 1];
        if (card6 && start6 && end6) {
            var eS6 = byId('seq-num-card-' + start6.id);
            var eE6 = byId('seq-num-card-' + end6.id);
            if (eS6 && eE6) {
                var pC6 = getCenter(card6), pS6 = getCenter(eS6), pE6 = getCenter(eE6);
                var bY6 = pS6.top - 14;
                var p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                p.setAttribute('d', 'M ' + pC6.x + ' ' + pC6.bottom + ' L ' + pC6.x + ' ' + (bY6 - 6) + ' M ' + pS6.x + ' ' + pS6.top + ' L ' + pS6.x + ' ' + bY6 + ' L ' + pE6.x + ' ' + bY6 + ' L ' + pE6.x + ' ' + pE6.top);
                p.setAttribute('stroke', '#67c23a');
                p.setAttribute('stroke-width', '2');
                p.setAttribute('fill', 'none');
                p.setAttribute('stroke-dasharray', '4,4');
                p.setAttribute('opacity', '0.75');
                svg.appendChild(p);
            }
        }

        // 3连连线
        var card3 = byId('seqCardAvg3');
        var start3 = state.numbers[state.targets.t1.start - 1];
        var end3 = state.numbers[state.targets.t1.end - 1];
        if (card3 && start3 && end3) {
            var eS3 = byId('seq-num-card-' + start3.id);
            var eE3 = byId('seq-num-card-' + end3.id);
            if (eS3 && eE3) {
                var pC3 = getCenter(card3), pS3 = getCenter(eS3), pE3 = getCenter(eE3);
                var bY3 = pS3.top - 6;
                var p3 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                p3.setAttribute('d', 'M ' + pC3.x + ' ' + pC3.bottom + ' L ' + pC3.x + ' ' + (bY3 - 4) + ' M ' + pS3.x + ' ' + pS3.top + ' L ' + pS3.x + ' ' + bY3 + ' L ' + pE3.x + ' ' + bY3 + ' L ' + pE3.x + ' ' + pE3.top);
                p3.setAttribute('stroke', '#409eff');
                p3.setAttribute('stroke-width', '2.5');
                p3.setAttribute('fill', 'none');
                p3.setAttribute('opacity', '0.9');
                svg.appendChild(p3);
            }
        }

        // 拆解向下曲线
        state.compositions.forEach(function (comp) {
            var numElem = byId('seq-num-card-' + comp.targetNumberId);
            var compElem = byId('seq-comp-card-' + comp.id);
            if (numElem && compElem) {
                var pN = getCenter(numElem), pCp = getCenter(compElem);
                var midY = (pN.bottom + pCp.top) / 2;
                var curve = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                curve.setAttribute('d', 'M ' + pN.x + ' ' + pN.bottom + ' C ' + pN.x + ' ' + midY + ', ' + pCp.x + ' ' + midY + ', ' + pCp.x + ' ' + pCp.top);
                curve.setAttribute('stroke', '#a855f7');
                curve.setAttribute('stroke-width', '2');
                curve.setAttribute('fill', 'none');
                curve.setAttribute('stroke-dasharray', '4,4');
                curve.setAttribute('opacity', '0.8');
                svg.appendChild(curve);
            }
        });
    }

    // 全局交互函数绑定
    w.copySequenceArt = function () {
        var text = Model.toTextArt(state);
        if (!navigator.clipboard) {
            if (w.AppDialog && w.AppDialog.prompt) return w.AppDialog.prompt('计算结果图', text, '复制文本');
            return alert('剪贴板不可用，请手动复制下方文本');
        }
        navigator.clipboard.writeText(text).then(function () {
            showMsg('三排连线文本结果已成功复制到剪贴板！', true);
        }).catch(function () {
            showMsg('复制失败，请手动在下方文本框全选复制', false);
        });
    };

    w.toggleSequenceLock = function (id) {
        var item = state.numbers.find(function (n) { return n.id === id; });
        if (item) { item.locked = !item.locked; renderAll(); }
    };

    w.updateSequenceValue = function (id, val) {
        var item = state.numbers.find(function (n) { return n.id === id; });
        if (item) {
            item.value = Number(val) || 0;
            renderAll();
        }
    };

    w.deleteSequenceNumber = function (id) {
        if (state.numbers.length <= 3) return alert('请至少保留 3 个数字！');
        state.numbers = state.numbers.filter(function (n) { return n.id !== id; });
        renderAll();
    };

    w.addSequenceNumber = function () {
        state.numbers.push({ id: 'n_' + Date.now(), value: 238, locked: false, label: '新增数' });
        renderAll();
    };

    w.bindSequenceCompTarget = function (compId, targetId) {
        var comp = state.compositions.find(function (c) { return c.id === compId; });
        if (comp) { comp.targetNumberId = targetId; renderAll(); }
    };

    w.updateSequenceCompItem = function (compId, idx, val) {
        var comp = state.compositions.find(function (c) { return c.id === compId; });
        if (comp && comp.items[idx] !== undefined) { comp.items[idx] = Number(val) || 0; renderAll(); }
    };

    w.addSequenceCompItem = function (compId) {
        var comp = state.compositions.find(function (c) { return c.id === compId; });
        if (comp) { comp.items.push(10); renderAll(); }
    };

    w.removeSequenceCompItem = function (compId, idx) {
        var comp = state.compositions.find(function (c) { return c.id === compId; });
        if (comp) { comp.items.splice(idx, 1); renderAll(); }
    };

    w.deleteSequenceComposition = function (compId) {
        state.compositions = state.compositions.filter(function (c) { return c.id !== compId; });
        renderAll();
    };

    w.addSequenceComposition = function () {
        state.compositions.push({ id: 'c_' + Date.now(), targetNumberId: state.numbers[0] ? state.numbers[0].id : '', items: [100, 50] });
        renderAll();
    };

    // 为指定数字卡片创建拆解组成框（支持右键菜单或按钮触发）
    w.addSequenceCompositionForNumber = function (targetId) {
        var target = state.numbers.find(function (n) { return n.id === targetId; });
        var targetVal = target ? Number(target.value || 0) : 100;
        var items = suggestCompositionItems(targetVal);

        state.compositions.push({
            id: 'c_' + Date.now(),
            targetNumberId: targetId,
            items: items
        });
        renderAll();
        var targetIdx = target ? state.numbers.indexOf(target) + 1 : '';
        showMsg('已成功为 #' + targetIdx + ' (目标值: ' + targetVal + ') 添加组成拆解框！', true);
    };

    // 智能分解建议（优先常用规整档位）
    function suggestCompositionItems(val) {
        val = Number(val || 0);
        if (val <= 0) return [10, 10];
        var tiers = [648, 328, 198, 98, 68, 30, 19.9, 8, 6];
        var items = [];
        var rem = val;
        for (var i = 0; i < tiers.length; i++) {
            while (rem >= tiers[i] && items.length < 5) {
                items.push(tiers[i]);
                rem = round(rem - tiers[i], 2);
            }
        }
        if (rem > 0 && items.length < 6) {
            items.push(rem);
        }
        return items.length > 0 ? items : [val];
    }

    // 自动补齐拆解差额
    w.autoBalanceComp = function (compId) {
        var comp = state.compositions.find(function (c) { return c.id === compId; });
        if (!comp) return;
        var target = state.numbers.find(function (n) { return n.id === comp.targetNumberId; });
        var targetVal = target ? Number(target.value || 0) : 0;
        var currentSum = (comp.items || []).reduce(function (s, v) { return s + Number(v || 0); }, 0);
        var diff = round(targetVal - currentSum, 2);
        if (diff !== 0) {
            comp.items.push(diff);
            renderAll();
            showMsg('已自动补齐差额 ' + diff + '，当前刚好平衡！', true);
        }
    };

    w.updateSequenceSupplement = function (idx, val) {
        state.supplements[idx] = Number(val) || 0;
        var calc = Model.calculate(state);
        updateSummary(calc);
        updateArtPreview(calc);
    };

    w.removeSequenceSupplement = function (idx) {
        state.supplements.splice(idx, 1);
        renderSupplements();
        var calc = Model.calculate(state);
        updateSummary(calc);
        updateArtPreview(calc);
    };

    w.addSequenceSupplement = function () {
        state.supplements.push(5);
        renderSupplements();
        var calc = Model.calculate(state);
        updateSummary(calc);
        updateArtPreview(calc);
    };

    w.resetSequenceData = function () {
        state = Model.createDefaultState();
        byId('seqT1Start').value = state.targets.t1.start;
        byId('seqT1End').value = state.targets.t1.end;
        byId('seqT1Val').value = state.targets.t1.targetAvg;
        byId('seqT3Start').value = state.targets.t3.start;
        byId('seqT3End').value = state.targets.t3.end;
        byId('seqT3Min').value = state.targets.t3.minAvg;
        byId('seqT3Max').value = state.targets.t3.maxAvg;
        renderAll();
        showMsg('已重置为默认示例数据', true);
    };

    function ensureNumberCapacity() {
        var maxRequired = Math.max(state.targets.t1.end || 0, state.targets.t3.end || 0);
        while (state.numbers.length < maxRequired) {
            var seq = state.numbers.length + 1;
            state.numbers.push({
                id: 'n_' + Date.now() + '_' + seq,
                value: Number(state.targets.t1.targetAvg || 238),
                locked: false,
                label: '目标数' + seq
            });
        }
    }

    ['seqT1Start', 'seqT1End', 'seqT1Val', 'seqT3Start', 'seqT3End', 'seqT3Min', 'seqT3Max'].forEach(function (id) {
        var el = byId(id);
        if (el) {
            el.addEventListener('input', function () {
                state.targets.t1.start = parseInt(byId('seqT1Start').value, 10) || 1;
                state.targets.t1.end = parseInt(byId('seqT1End').value, 10) || 1;
                state.targets.t1.targetAvg = parseFloat(byId('seqT1Val').value) || 0;
                state.targets.t3.start = parseInt(byId('seqT3Start').value, 10) || 1;
                state.targets.t3.end = parseInt(byId('seqT3End').value, 10) || 1;
                state.targets.t3.minAvg = parseFloat(byId('seqT3Min').value) || 0;
                state.targets.t3.maxAvg = parseFloat(byId('seqT3Max').value) || 0;
                ensureNumberCapacity();
                renderAll();
            });
        }
    });

    // 自动求解与翻页
    var currentSolutions = [];
    var currentSolutionIdx = 0;

    w.triggerSequenceSolve = function () {
        ensureNumberCapacity();
        var res = Model.solveSolutions(state);
        var panel = byId('seqSolverPanel');

        if (!res.solutions || res.solutions.length === 0) {
            if (panel) panel.style.display = 'none';
            showMsg('未找到满足所有目标的方案，请放宽目标均值或解锁更多数字', false);
            return;
        }

        currentSolutions = res.solutions;
        currentSolutionIdx = 0;

        if (panel) {
            panel.style.display = currentSolutions.length > 1 ? 'inline-flex' : 'none';
        }

        applySolution(0);
        showMsg(currentSolutions.length > 1 ? ('已计算出 ' + res.total + ' 组达标方案，可点击 ◀ ▶ 切换') : '已自动填入唯一达标解！', true);
    };

    function applySolution(index) {
        if (!currentSolutions || currentSolutions.length === 0) return;
        if (index < 0) index = currentSolutions.length - 1;
        if (index >= currentSolutions.length) index = 0;
        currentSolutionIdx = index;

        var sol = currentSolutions[currentSolutionIdx];
        if (sol && sol.numbers) {
            sol.numbers.forEach(function (sn, i) {
                if (!state.numbers[i]) {
                    state.numbers.push({
                        id: sn.id || ('n_' + Date.now() + '_' + (i + 1)),
                        value: sn.value,
                        locked: sn.locked || false,
                        label: sn.label || ('目标数' + (i + 1))
                    });
                } else {
                    state.numbers[i].value = sn.value;
                }
            });
        }

        var curEl = byId('seqSolCurrentIdx');
        var totEl = byId('seqSolTotalCount');
        if (curEl) curEl.textContent = currentSolutionIdx + 1;
        if (totEl) totEl.textContent = currentSolutions.length;

        renderAll();
    }

    w.prevSolution = function () { applySolution(currentSolutionIdx - 1); };
    w.nextSolution = function () { applySolution(currentSolutionIdx + 1); };

    // 右键上下文菜单事件处理
    var currentCtxId = null;
    function openContextMenu(e, numId) {
        currentCtxId = numId;
        var menu = byId('seqContextMenu');
        if (!menu) return;
        menu.style.display = 'block';
        var x = e.clientX;
        var y = e.clientY;
        if (x + 190 > window.innerWidth) x = window.innerWidth - 190;
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
    }

    document.addEventListener('click', function () {
        var menu = byId('seqContextMenu');
        if (menu) menu.style.display = 'none';
    });

    var ctxAddBtn = byId('seqCtxAddComp');
    if (ctxAddBtn) {
        ctxAddBtn.onclick = function () {
            if (currentCtxId) w.addSequenceCompositionForNumber(currentCtxId);
        };
    }
    var ctxLockBtn = byId('seqCtxToggleLock');
    if (ctxLockBtn) {
        ctxLockBtn.onclick = function () {
            if (currentCtxId) w.toggleSequenceLock(currentCtxId);
        };
    }

    window.addEventListener('keydown', function (e) {
        if (['INPUT', 'SELECT', 'TEXTAREA'].indexOf(document.activeElement.tagName) !== -1) return;
        if (currentSolutions.length > 1) {
            if (e.key === 'ArrowLeft') {
                e.preventDefault();
                w.prevSolution();
            } else if (e.key === 'ArrowRight') {
                e.preventDefault();
                w.nextSolution();
            }
        }
    });

    window.addEventListener('resize', drawConnectionLines);
    var canvasCard = document.querySelector('.seq-canvas-card');
    if (canvasCard) canvasCard.addEventListener('scroll', drawConnectionLines);

    renderAll();
})(window);
