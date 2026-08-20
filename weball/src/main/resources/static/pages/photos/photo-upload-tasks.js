(function () {
    'use strict';
    window.PhotoUploadTaskList = function (options) {
        var opened = false;
        options.toggle.onclick = function () { opened = !opened; options.root.classList.toggle('open', opened); options.list.hidden = !opened; options.toggle.setAttribute('aria-expanded', String(opened)); };
        function label(task) {
            if (task.status === 'UPLOADING') return '上传 ' + task.progress + '%';
            return ({ WAITING: '等待处理', PROCESSING: '处理中', SUCCESS: '已完成', FAILED: '失败' })[task.status] || task.status;
        }
        function escape(value) { var node = document.createElement('div'); node.textContent = value == null ? '' : value; return node.innerHTML; }
        return function render(tasks) {
            options.root.hidden = !tasks.length;
            if (!tasks.length) return;
            var active = tasks.filter(function (task) { return task.status === 'UPLOADING' || task.status === 'WAITING' || task.status === 'PROCESSING'; });
            var failed = tasks.filter(function (task) { return task.status === 'FAILED'; }).length;
            options.text.textContent = active.length ? '上传任务：进行中 ' + active.length + (failed ? '，失败 ' + failed : '') : '上传任务：已完成' + (failed ? '，失败 ' + failed : '');
            options.list.innerHTML = tasks.map(function (task) {
                var progress = task.status === 'UPLOADING' ? '<div class="upload-file-progress"><span style="width:' + task.progress + '%"></span></div>' : '';
                var error = task.error ? '<div class="upload-file-error" title="' + escape(task.error) + '">' + escape(task.error) + '</div>' : '';
                return '<div class="upload-file-list__item"><span class="upload-file-name" title="' + escape(task.filename) + '">' + escape(task.filename) + '</span><span class="upload-file-status ' + task.status.toLowerCase() + '">' + label(task) + '</span>' + progress + error + '</div>';
            }).join('');
        };
    };
})();
