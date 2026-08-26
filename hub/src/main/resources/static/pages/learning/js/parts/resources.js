(function () {
    LearningRegister({
        data: {
            resourceList: [],
            resourceSubject: '',
            resourceMode: 'library',
            resourceTabs: [{id: 'library', label: '学习库'}, {id: 'uploads', label: '家庭资料'}],
            resourcePage: 1,
            resourcePageSize: 4,
            mediaUrls: {},
            preview: null,
            resourceFolders: [
                {id: 'english', name: '英语文件', icon: '📗'}, {id: 'chinese', name: '语文文件', icon: '📕'}, {
                    id: 'math',
                    name: '数学文件',
                    icon: '📘'
                },
                {id: 'history', name: '历史文件', icon: '📙'}, {
                    id: 'chemistry',
                    name: '化学文件',
                    icon: '🧪'
                }, {id: 'picture-books', name: '绘本', icon: '🖼️'},
                {id: 'worksheets', name: '练习题', icon: '✏️'}, {id: '', name: '全部上传', icon: '🗂️'}
            ]
        },
        computed: {
            visibleResources() {
                return this.pageItems(this.resourceList, this.resourcePage, this.resourcePageSize);
            },
            resourcePageCount() {
                return this.pageCount(this.resourceList, this.resourcePageSize);
            }
        },
        methods: {
            async loadResources() {
                try {
                    const q = this.resourceSubject ? '?subject=' + encodeURIComponent(this.resourceSubject) : '';
                    this.resourceList = await this.api('resources' + q);
                    this.resourcePage = 1;
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            changeResourcePage(page) {
                this.changeCollectionPage('resourcePage', page, this.resourcePageCount);
            },
            async ensureMediaUrl(path) {
                if (!path) return '';
                if (this.mediaUrls[path]) return this.mediaUrls[path];
                const response = await fetch(this.apiUrl('resources/file?path=' + encodeURIComponent(path)), {credentials: 'include'});
                if (!response.ok) {
                    let message = '资源打开失败';
                    try {
                        message = (await response.json()).message || message;
                    } catch (_) {
                    }
                    if (response.status === 401) this.clearSession();
                    throw new Error(message);
                }
                this.bumpIdle();
                const url = URL.createObjectURL(await response.blob());
                this.mediaUrls = {...this.mediaUrls, [path]: url};
                return url;
            },
            revokeMediaUrls() {
                Object.values(this.mediaUrls || {}).forEach(url => {
                    try {
                        URL.revokeObjectURL(url);
                    } catch (_) {
                    }
                });
                this.mediaUrls = {};
                if (this.preview && this.preview.url) {
                    try {
                        URL.revokeObjectURL(this.preview.url);
                    } catch (_) {
                    }
                }
                this.preview = null;
            },
            async previewResource(resource) {
                try {
                    const path = typeof resource === 'string' ? resource : resource.path;
                    const ext = (path.split('.').pop() || '').toLowerCase();
                    const kind = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext) ? 'image' : ['mp3', 'wav', 'ogg'].includes(ext) ? 'audio' : ext === 'mp4' ? 'video' : 'file';
                    const url = await this.ensureMediaUrl(path);
                    this.preview = {name: path.split('/').pop(), kind, url, path};
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            closePreview() {
                this.preview = null;
            },
            async openResource(path) {
                try {
                    const url = await this.ensureMediaUrl(path);
                    window.open(url, '_blank');
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            fileIcon(path) {
                const e = path.split('.').pop().toLowerCase();
                return e === 'pdf' ? '📕' : ['mp3', 'wav', 'ogg'].includes(e) ? '🎵' : ['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(e) ? '🖼️' : e === 'mp4' ? '🎬' : '📄';
            },
            formatSize(size) {
                return size < 1024 ? size + ' B' : size < 1048576 ? (size / 1024).toFixed(1) + ' KB' : (size / 1048576).toFixed(1) + ' MB';
            }
        }
    });
})();
