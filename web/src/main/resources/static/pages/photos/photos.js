(function () {
    'use strict';
    var token = localStorage.getItem('sessionId');
    if (!token) { location.href = appUrl('/'); return; }
    var state = { page: 1, size: 24, total: 0, items: [] };
    var $ = function (id) { return document.getElementById(id); };
    var headers = function (json) { var h = { 'X-Session-Token': token }; if (json) h['Content-Type'] = 'application/json'; return h; };
    var viewer = PhotoViewer({ viewer: $('viewer'), stage: $('stage'), image: $('originalImage'), closeButton: $('closeViewer') });
    var renderUploadTasks = PhotoUploadTaskList({ root: $('uploadTasks'), toggle: $('uploadTasksToggle'), text: $('uploadTasksText'), list: $('uploadTasksList') });
    var uploader = PhotoUploader({ uploadUrl: appUrl('/api/photos/upload'), tasksUrl: appUrl('/api/photos/upload/tasks'), token: token, onChange: renderUploadTasks, onSettled: load });

    function api(path, options) {
        options = options || {};
        options.headers = Object.assign(headers(options.body && !(options.body instanceof FormData)), options.headers || {});
        return fetch(appUrl('/api/photos' + path), options).then(async function (response) {
            if (response.status === 401) { location.href = appUrl('/'); throw Error('登录已失效'); }
            if (!response.ok) { var error = await response.json().catch(function () { return { msg: '请求失败' }; }); throw Error(error.msg || '请求失败'); }
            return response.json();
        });
    }
    function esc(value) { var node = document.createElement('div'); node.textContent = value == null ? '' : value; return node.innerHTML; }
    function date(value) { return new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }); }
    function source(value) { return ({ EXIF_ORIGINAL: '相机拍摄', EXIF_DIGITIZED: '相机数字化', EXIF_MODIFIED: '相机时间', FILE_TIME: '文件时间', UPLOAD_TIME: '上传时间' })[value] || value; }
    function showError(error) { $('message').textContent = error.message; $('message').className = 'message error'; }
    function load() {
        $('gallery').innerHTML = '<div class="empty">加载中...</div>';
        api('?page=' + state.page + '&pageSize=' + state.size).then(function (data) { state.items = data.items || []; state.total = data.total || 0; render(); }).catch(showError);
    }
    function render() {
        var session = encodeURIComponent(token);
        $('gallery').innerHTML = state.items.map(function (photo, index) {
            return '<article class="photo-card"><img loading="lazy" decoding="async" data-index="' + index + '" src="' + appUrl(photo.thumbnailUrl) + '?sessionId=' + session + '" alt="' + esc(photo.displayName) + '"><div class="photo-info"><p class="photo-name" title="' + esc(photo.displayName) + '">' + esc(photo.displayName) + '</p><div class="photo-meta">' + date(photo.capturedAt) + ' · ' + source(photo.capturedAtSource) + '<br>上传人：' + esc(photo.ownerUsername) + '</div><div class="photo-actions"><button data-view="' + index + '">查看</button><button class="ghost" data-rename="' + photo.id + '">改名</button><button class="danger" data-delete="' + photo.id + '">删除</button></div></div></article>';
        }).join('');
        $('empty').hidden = state.total !== 0; $('summary').textContent = '共 ' + state.total + ' 张';
        var pages = Math.max(1, Math.ceil(state.total / state.size)); $('pageLabel').textContent = state.page + ' / ' + pages;
        $('prevButton').disabled = state.page <= 1; $('nextButton').disabled = state.page >= pages;
    }
    function openViewer(index) {
        var photo = state.items[index], session = encodeURIComponent(token);
        viewer.open(appUrl(photo.thumbnailUrl) + '?sessionId=' + session, appUrl(photo.originalUrl), headers());
    }
    $('fileInput').onchange = function () { var count = this.files.length; $('selection').textContent = count ? '已选择 ' + count + ' 张照片' : ''; $('uploadButton').disabled = !count; };
    $('uploadButton').onclick = function () {
        var button = this, files = $('fileInput').files; if (!files.length) return;
        button.disabled = true; $('message').className = 'message'; $('message').textContent = '准备上传…';
        uploader.start(files).then(function (tasks) {
            var failed = tasks.filter(function (item) { return item.status === 'FAILED'; });
            var success = tasks.filter(function (item) { return item.status === 'SUCCESS'; }).length;
            $('message').className = 'message' + (failed.length ? ' error' : '');
            $('message').textContent = '成功 ' + success + ' 张' + (failed.length ? '，失败 ' + failed.length + ' 张，点击上传任务查看' : '');
            $('fileInput').value = ''; $('selection').textContent = ''; state.page = 1; load();
        }).catch(showError).finally(function () { button.disabled = !$('fileInput').files.length; });
    };
    $('gallery').onclick = function (event) {
        var view = event.target.dataset.view;
        if (view == null && event.target.tagName === 'IMG') view = event.target.dataset.index;
        if (view != null) { openViewer(Number(view)); return; }
        var id = event.target.dataset.rename;
        if (id) { var photo = state.items.find(function (item) { return String(item.id) === id; }); var name = prompt('新的显示名称', photo.displayName); if (name && name.trim()) api('/' + id, { method: 'PATCH', body: JSON.stringify({ displayName: name.trim() }) }).then(load).catch(showError); return; }
        id = event.target.dataset.delete;
        if (id && confirm('确定删除这张照片？图片将从图库隐藏。')) api('/' + id, { method: 'DELETE' }).then(load).catch(showError);
    };
    $('prevButton').onclick = function () { if (state.page > 1) { state.page--; load(); } };
    $('nextButton').onclick = function () { state.page++; load(); };
    $('refreshButton').onclick = load;
    $('userName').textContent = localStorage.getItem('nickname') || localStorage.getItem('username') || '';
    uploader.restore();
    load();
})();
