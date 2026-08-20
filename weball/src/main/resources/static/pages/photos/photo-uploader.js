(function () {
    'use strict';
    function uuid() { return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) { var r = Math.random() * 16 | 0; return (c === 'x' ? r : (r & 3 | 8)).toString(16); }); }
    function body(xhr) { try { return JSON.parse(xhr.responseText); } catch (ignored) { return {}; } }
    window.PhotoUploader = function (options) {
        var tasks = new Map(), polling, running = false, hadActive = false;
        function snapshot() { return Array.from(tasks.values()).sort(function (a, b) { return b.createdAt - a.createdAt; }); }
        function changed() { options.onChange(snapshot()); }
        function uploadOne(task) {
            return new Promise(function (resolve) {
                var xhr = new XMLHttpRequest(), form = new FormData(); form.append('files', task.file);
                xhr.open('POST', options.uploadUrl); xhr.setRequestHeader('X-Session-Token', options.token); xhr.setRequestHeader('X-Upload-Task-ID', task.id); xhr.timeout = 5 * 60 * 1000;
                xhr.upload.onprogress = function (event) { if (event.lengthComputable) { task.status = 'UPLOADING'; task.progress = Math.round(event.loaded / event.total * 100); changed(); } };
                xhr.onload = function () { var result = body(xhr), item = result.results && result.results[0]; task.status = item && item.success ? 'SUCCESS' : 'FAILED'; task.error = item && item.error || (xhr.status >= 400 ? result.msg || '上传失败（' + xhr.status + '）' : null); delete task.file; changed(); resolve(); };
                xhr.onerror = function () { task.status = 'FAILED'; task.error = '网络连接中断'; delete task.file; changed(); resolve(); };
                xhr.ontimeout = function () { task.status = 'FAILED'; task.error = '处理超时，请刷新页面检查任务'; delete task.file; changed(); resolve(); };
                xhr.send(form);
            });
        }
        async function worker(queue) { while (queue.length) await uploadOne(queue.shift()); }
        function active() { return snapshot().some(function (task) { return task.status === 'UPLOADING' || task.status === 'WAITING' || task.status === 'PROCESSING'; }); }
        function poll() {
            clearTimeout(polling);
            fetch(options.tasksUrl, { headers: { 'X-Session-Token': options.token } }).then(function (response) { if (!response.ok) throw Error(); return response.json(); }).then(function (serverTasks) {
                serverTasks.forEach(function (serverTask) {
                    var local = tasks.get(serverTask.id);
                    if (!local || local.status !== 'SUCCESS') tasks.set(serverTask.id, Object.assign(local || {}, serverTask));
                });
                changed();
                var nowActive = active();
                if (hadActive && !nowActive && options.onSettled) options.onSettled();
                hadActive = nowActive;
            }).catch(function () {}).finally(function () { if (active()) polling = setTimeout(poll, 2000); });
        }
        return {
            restore: poll,
            start: async function (fileList) {
                if (running) throw Error('已有上传任务正在提交');
                var files = Array.prototype.slice.call(fileList); if (files.length > 20) throw Error('一次最多上传 20 张');
                running = true;
                var queue = files.map(function (file) { var task = { id: uuid(), filename: file.name, file: file, status: 'WAITING', progress: 0, createdAt: Date.now() }; tasks.set(task.id, task); return task; });
                hadActive = true; changed(); polling = setTimeout(poll, 1000);
                await Promise.all([worker(queue), worker(queue), worker(queue)]);
                running = false; poll();
                return snapshot();
            }
        };
    };
})();
