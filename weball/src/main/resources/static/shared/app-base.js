/** 兼容随机 context-path：本机根路径为空，外网为 /{随机路径} */
(function (w) {
    /** 生产页面错误脱敏：控制台保留定位信息，但不输出站点地址、部署前缀或凭据。 */
    (function () {
        var location = w.location || {};
        var origin = String(location.origin || '');
        var host = String(location.host || '');
        var firstPath = String(location.pathname || '').split('/').filter(Boolean)[0] || '';
        var sensitive = /([?&](?:sessionId|token|invite|password|authorization|code)=)[^&#\s]*/ig;
        function escapeRegExp(value) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
        function sanitizeText(value) {
            var text = String(value == null ? '' : value);
            text = text.replace(sensitive, '$1[已隐藏]');
            text = text.replace(/\b(?:https?|wss?):\/\/[^\s/]+/ig, '[站点]');
            if (origin) text = text.split(origin).join('[站点]');
            if (host) text = text.replace(new RegExp(escapeRegExp(host), 'ig'), '[站点]');
            if (firstPath) text = text.replace(new RegExp('/' + escapeRegExp(firstPath) + '(?=/|\\b)', 'g'), '');
            return text;
        }
        function sanitize(value, depth, seen) {
            depth = depth || 0;
            if (typeof value === 'string') return sanitizeText(value);
            if (value == null || typeof value === 'number' || typeof value === 'boolean') return value;
            if (value instanceof Error) return {name: value.name, message: sanitizeText(value.message), stack: sanitizeText(value.stack || '')};
            if (typeof value !== 'object' || depth >= 4) return sanitizeText(value);
            seen = seen || [];
            if (seen.indexOf(value) >= 0) return '[循环引用]';
            seen.push(value);
            var copy = Array.isArray(value) ? [] : {};
            Object.keys(value).slice(0, 50).forEach(function (key) {
                copy[key] = /password|token|session|authorization|invite/i.test(key)
                    ? '[已隐藏]' : sanitize(value[key], depth + 1, seen);
            });
            seen.pop();
            return copy;
        }
        var consoleObject = w.console;
        if (consoleObject) {
            ['log', 'info', 'warn', 'error', 'debug'].forEach(function (name) {
                if (typeof consoleObject[name] !== 'function') return;
                var original = consoleObject[name].bind(consoleObject);
                consoleObject[name] = function () {
                    original.apply(null, Array.prototype.map.call(arguments, function (arg) { return sanitize(arg); }));
                };
            });
        }
        w.addEventListener('error', function (event) {
            if (event.target && event.target !== w) {
                consoleObject && consoleObject.error('[资源加载失败]', String(event.target.tagName || 'RESOURCE').toLowerCase());
            } else {
                consoleObject && consoleObject.error('[页面运行错误]', sanitizeText(event.message || '未知错误'),
                    sanitizeText(event.filename || '').replace(/^.*\//, '') + ':' + (event.lineno || 0) + ':' + (event.colno || 0));
            }
            if (event.preventDefault) event.preventDefault();
        }, true);
        w.addEventListener('unhandledrejection', function (event) {
            consoleObject && consoleObject.error('[未处理的异步错误]', sanitize(event.reason));
            if (event.preventDefault) event.preventDefault();
        });
        w.AppErrorPrivacy = {sanitizeText: sanitizeText, sanitize: sanitize};
    })();

    /** 若依式公共消息/确认/表单浮层；所有接口均返回 Promise。 */
    (function () {
        var activeResolve = null;
        function ensure() {
            var mask = document.getElementById('app-dialog-mask');
            if (mask) return mask;
            var style = document.createElement('style');
            style.textContent = '.app-dialog-mask{position:fixed;inset:0;z-index:999999;display:flex;align-items:center;justify-content:center;padding:16px;background:rgba(15,23,42,.48)}.app-dialog-mask[hidden]{display:none}.app-dialog{width:min(430px,100%);overflow:hidden;border-radius:6px;background:#fff;color:#303133;box-shadow:0 16px 46px #0005;font:14px/1.5 "PingFang SC","Microsoft YaHei",sans-serif}.app-dialog-head{display:flex;align-items:center;justify-content:space-between;padding:15px 20px;border-bottom:1px solid #ebeef5}.app-dialog-head strong{font-size:16px}.app-dialog-x{border:0;background:none;color:#909399;font-size:24px;cursor:pointer}.app-dialog-body{padding:20px}.app-dialog-message{white-space:pre-wrap;word-break:break-word}.app-dialog-field{margin-top:14px}.app-dialog-field:first-child{margin-top:0}.app-dialog-field label{display:block;margin-bottom:6px;color:#606266;font-size:13px}.app-dialog-field input,.app-dialog-field select{display:block;width:100%;height:38px;padding:0 11px;border:1px solid #dcdfe6;border-radius:4px;background:#fff;color:#303133;font-size:14px;box-sizing:border-box}.app-dialog-error{min-height:18px;padding-top:6px;color:#f56c6c;font-size:12px}.app-dialog-foot{display:flex;justify-content:flex-end;gap:10px;padding:10px 20px 18px}.app-dialog-foot button{min-width:72px;padding:8px 15px;border:1px solid #dcdfe6;border-radius:4px;background:#fff;color:#606266;cursor:pointer}.app-dialog-foot .primary{border-color:#409eff;background:#409eff;color:#fff}';
            document.head.appendChild(style);
            mask = document.createElement('div'); mask.id = 'app-dialog-mask'; mask.className = 'app-dialog-mask'; mask.hidden = true;
            mask.innerHTML = '<section class="app-dialog" role="dialog" aria-modal="true"><header class="app-dialog-head"><strong></strong><button class="app-dialog-x" type="button" aria-label="关闭">×</button></header><div class="app-dialog-body"></div><footer class="app-dialog-foot"><button class="cancel" type="button">取消</button><button class="primary" type="button">确定</button></footer></section>';
            document.body.appendChild(mask);
            mask.querySelector('.app-dialog-x').onclick = mask.querySelector('.cancel').onclick = function () { finish(null); };
            mask.onclick = function (event) { if (event.target === mask) finish(null); };
            return mask;
        }
        function finish(value) {
            var mask = document.getElementById('app-dialog-mask'); if (mask) mask.hidden = true;
            var resolve = activeResolve; activeResolve = null; if (resolve) resolve(value);
        }
        function open(opt) {
            opt = opt || {};
            if (activeResolve) finish(null);
            var mask = ensure(), body = mask.querySelector('.app-dialog-body'), cancel = mask.querySelector('.cancel');
            mask.querySelector('.app-dialog-head strong').textContent = opt.title || '提示';
            body.innerHTML = '';
            if (opt.message) { var message = document.createElement('div'); message.className = 'app-dialog-message'; message.textContent = opt.message; body.appendChild(message); }
            (opt.fields || []).forEach(function (field) {
                var row = document.createElement('div'); row.className = 'app-dialog-field';
                var label = document.createElement('label'); label.textContent = field.label || field.name; row.appendChild(label);
                var input = field.options ? document.createElement('select') : document.createElement('input');
                input.dataset.field = field.name; input.type = field.type || 'text';
                if (field.min != null) input.min = field.min; if (field.max != null) input.max = field.max;
                (field.options || []).forEach(function (value) { var option = document.createElement('option'); option.value = value; option.textContent = value; input.appendChild(option); });
                input.value = field.value == null ? '' : field.value;
                row.appendChild(input); body.appendChild(row);
            });
            var error = document.createElement('div'); error.className = 'app-dialog-error'; body.appendChild(error);
            cancel.hidden = opt.cancel === false; mask.hidden = false;
            return new Promise(function (resolve) {
                activeResolve = resolve;
                mask.querySelector('.primary').onclick = function () {
                    if (!(opt.fields || []).length) return finish(true);
                    var values = {};
                    body.querySelectorAll('[data-field]').forEach(function (input) { values[input.dataset.field] = input.type === 'number' ? Number(input.value) : input.value; });
                    var problem = opt.validate && opt.validate(values); if (problem) { error.textContent = problem; return; }
                    finish(values);
                };
                var first = body.querySelector('input,select'); if (first) setTimeout(function () { first.focus(); }, 0);
            });
        }
        w.AppDialog = {
            alert: function (message, title) { return open({title: title || '提示', message: String(message || ''), cancel: false}); },
            confirm: function (message, title) { return open({title: title || '确认操作', message: String(message || '')}).then(function (v) { return v === true; }); },
            prompt: function (label, value, title) { return open({title: title || '请输入', fields: [{name: 'value', label: label, value: value == null ? '' : value}]}).then(function (v) { return v ? v.value : null; }); },
            form: open
        };
    })();

    var parts = w.location.pathname.split('/').filter(Boolean);
    var base = '';
    if (parts.length && parts[0].indexOf('.') < 0) {
        base = '/' + parts[0];
    }
    w.APP_BASE = base;
    // 与服务端 TableState.TABLE_DIS 保持一致：桌子已解散。
    w.TABLE_STATE_DIS = 9;
    w.appUrl = function (path) {
        if (path == null || path === '') {
            return base || '/';
        }
        if (path.charAt(0) !== '/') {
            path = '/' + path;
        }
        return base + path;
    };

    /** 公共体验层随基础脚本加载，确保所有子页面共享可访问性和移动端规则。 */
    (function () {
        var resumeListeners = [];
        var script = document.currentScript;
        var href = script && script.src
            ? new URL('app-quality.css', script.src).href
            : w.appUrl('/shared/app-quality.css');
        if (!document.querySelector('link[data-app-quality]')) {
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = href;
            link.dataset.appQuality = '1';
            document.head.appendChild(link);
        }

        function networkTip(message, persistent) {
            var el = document.getElementById('global-network-status');
            if (!el) {
                el = document.createElement('div');
                el.id = 'global-network-status';
                el.setAttribute('role', 'status');
                el.setAttribute('aria-live', 'polite');
                document.body.appendChild(el);
            }
            el.textContent = message;
            el.classList.add('show');
            clearTimeout(el._timer);
            if (!persistent) {
                el._timer = setTimeout(function () {
                    el.classList.remove('show');
                }, 1800);
            }
        }

        function resume() {
            if (document.hidden || !w.navigator.onLine) return;
            resumeListeners.forEach(function (listener) {
                try {
                    listener();
                } catch (e) { /* 单页回调互不影响。 */
                }
            });
        }

        w.AppQuality = {
            canRequest: function () {
                return !document.hidden && w.navigator.onLine;
            },
            onResume: function (listener) {
                resumeListeners.push(listener);
            }
        };
        w.addEventListener('offline', function () {
            networkTip('网络已断开，恢复后会自动重试', true);
        });
        w.addEventListener('online', function () {
            networkTip('网络已恢复', false);
            resume();
        });
        document.addEventListener('visibilitychange', resume);
        if (!w.navigator.onLine) {
            document.addEventListener('DOMContentLoaded', function () {
                networkTip('网络已断开，恢复后会自动重试', true);
            });
        }
    })();

    // 仅对「会与服务器交互」的按钮防连点：本地跳转/本地操作不受 3 秒限制。
    // 判定方式：点击后若同步触发 fetch / XHR / WebSocket.send，则对该按钮生效。
    (function () {
        var lastClick = typeof WeakMap !== 'undefined' ? new WeakMap() : null;
        var cooldown = 3000;
        var pendingButton = null;
        var pendingArmedAt = 0;

        function tip() {
            var el = document.getElementById('global-click-tip');
            if (!el) {
                el = document.createElement('div');
                el.id = 'global-click-tip';
                el.setAttribute('role', 'status');
                el.setAttribute('aria-live', 'polite');
                el.style.cssText = 'position:fixed;left:50%;top:18%;transform:translate(-50%,-50%);z-index:99999;padding:10px 18px;border-radius:20px;background:rgba(25,25,25,.88);color:#fff;font-size:14px;pointer-events:none;opacity:0;transition:opacity .25s';
                document.body.appendChild(el);
            }
            el.textContent = '请稍后';
            el.style.opacity = '1';
            clearTimeout(el._timer);
            el._timer = setTimeout(function () {
                el.style.opacity = '0';
            }, 900);
        }

        function readStamp(button) {
            return (lastClick && lastClick.get(button)) || button._lastClick || 0;
        }

        function writeStamp(button, now) {
            if (lastClick) lastClick.set(button, now);
            else button._lastClick = now;
        }

        function clearStamp(button) {
            if (lastClick) lastClick.delete(button);
            else delete button._lastClick;
        }

        /** 点击同步路径上出现服务端交互时，确认保留防连点时间戳。 */
        function noteServerInteraction() {
            if (!pendingButton) return;
            pendingButton._serverClick = Date.now();
        }

        function disarmIfLocal(button, armedAt) {
            if (pendingButton === button) pendingButton = null;
            if (!button._serverClick || button._serverClick < armedAt) {
                clearStamp(button);
            }
        }

        function patchNetworkHooks() {
            if (typeof w.fetch === 'function') {
                var rawFetch = w.fetch;
                w.fetch = function () {
                    noteServerInteraction();
                    return rawFetch.apply(this, arguments);
                };
            }
            if (w.XMLHttpRequest && w.XMLHttpRequest.prototype) {
                var rawXhrSend = w.XMLHttpRequest.prototype.send;
                w.XMLHttpRequest.prototype.send = function () {
                    noteServerInteraction();
                    return rawXhrSend.apply(this, arguments);
                };
            }
            if (w.WebSocket && w.WebSocket.prototype) {
                var rawWsSend = w.WebSocket.prototype.send;
                w.WebSocket.prototype.send = function () {
                    noteServerInteraction();
                    return rawWsSend.apply(this, arguments);
                };
            }
        }

        document.addEventListener('click', function (event) {
            var button = event.target.closest && event.target.closest('button');
            if (!button || button.disabled) return;
            // 显式本地操作：永不拦截。
            if (button.getAttribute('data-local') === '1') return;
            var now = Date.now();
            var previous = readStamp(button);
            if (previous && now - previous < cooldown) {
                event.preventDefault();
                event.stopImmediatePropagation();
                tip();
                return;
            }
            // 先临时记时防双击竞态；若本次点击未触发服务端交互，则在微任务中撤销。
            writeStamp(button, now);
            pendingButton = button;
            pendingArmedAt = now;
            queueMicrotask(function () {
                disarmIfLocal(button, pendingArmedAt);
            });
        }, true);

        patchNetworkHooks();
    })();
})(window);
