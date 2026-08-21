const {createApp} = Vue;
createApp({
    data() {
        return {
            token: '',
            me: null,
            tab: 'dashboard',
            toast: '',
            timer: null,
            idleTimer: null,
            idleMs: 10 * 60 * 1000,
            stats: {},
            users: [],
            online: [],
            invites: [],
            words: [],
            contents: [],
            templates: [],
            records: [],
            mistakes: [],
            resources: [],
            selectedUser: '',
            modal: '',
            modalTitle: '',
            form: {},
            reportPreview: '',
            uploadSubject: 'chinese',
            stages: ['幼小衔接', '一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
            folders: [{id: 'chinese', name: '语文'}, {id: 'math', name: '数学'}, {
                id: 'english',
                name: '英语'
            }, {id: 'history', name: '历史'}, {id: 'chemistry', name: '化学'}, {
                id: 'picture-books',
                name: '绘本'
            }, {id: 'poems', name: '古诗'}, {id: 'worksheets', name: '练习题'}],
            permissionList: [{id: 'CHINESE', name: '语文'}, {id: 'MATH', name: '数学'}, {
                id: 'ENGLISH',
                name: '英语'
            }, {id: 'HISTORY', name: '历史'}, {id: 'CHEMISTRY', name: '化学'}, {
                id: 'PRIMARY',
                name: '小学区'
            }, {id: 'RESOURCES', name: '资源'}, {id: 'MISTAKES', name: '错题'}, {
                id: 'RECORDS',
                name: '记录'
            }, {id: 'STATS', name: '统计'}, {id: 'PRINT', name: '打印'}],
            nav: [{id: 'dashboard', name: '统计概览', icon: '📊'}, {
                id: 'users',
                name: '用户与权限',
                icon: '👤'
            }, {id: 'online', name: '当前在线', icon: '🟢'}, {id: 'words', name: '汉字词语', icon: '语'}, {
                id: 'content',
                name: '学科内容',
                icon: '📚'
            }, {id: 'templates', name: '文字题模板', icon: '🧮'}, {
                id: 'records',
                name: '学习记录',
                icon: '📋'
            }, {id: 'mistakes', name: '错题管理', icon: '📝'}, {
                id: 'resources',
                name: '资源文件',
                icon: '📁'
            }, {id: 'email', name: '每日邮件', icon: '✉️'}]
        };
    },
    async mounted() {
        /* game session via cookie */
        this.userStats = {};
        this.selectedUserName = '';
        this.statsBusy = false;
        this.onlineBusy = false;
        this.onUserActivity = () => this.bumpIdle();
        ['pointerdown', 'keydown', 'touchstart', 'scroll'].forEach(evt => window.addEventListener(evt, this.onUserActivity, {passive: true}));
        try {
            this.me = await this.api('auth/me');
            if (this.me.role !== 'ADMIN') {
                const err = new Error('需要管理员权限');
                err.status = 401;
                throw err;
            }
            try {
                const reg = await this.api('auth/registration');
                this.idleMs = Math.max(1, Number(reg.idleMinutes || 10)) * 60 * 1000;
            } catch (_) {
            }
            await Promise.all([this.loadStats().catch(e => {
                if (this.isAuthError(e)) throw e;
                this.notify('统计加载失败：' + e.message);
            }), this.loadUsers().catch(e => {
                if (this.isAuthError(e)) throw e;
                this.notify('用户列表加载失败：' + e.message);
            })]);
            this.bumpIdle();
            this.timer = setInterval(() => {
                if (window.AppQuality && !window.AppQuality.canRequest()) return;
                if (this.tab === 'dashboard') this.loadStats();
                if (this.tab === 'online') this.loadOnline();
            }, 30000);
        } catch (error) {
            this.notify('后台加载失败：' + error.message);
            if (this.isAuthError(error)) setTimeout(() => location.href = './', 1200);
        }
    },
    beforeUnmount() {
        clearInterval(this.timer);
        clearTimeout(this.idleTimer);
        ['pointerdown', 'keydown', 'touchstart', 'scroll'].forEach(evt => window.removeEventListener(evt, this.onUserActivity));
    },
    methods: {
        apiUrl(path) {
            const base = (typeof appUrl === 'function') ? appUrl('/api/learning/') : '/api/learning/';
            return base.replace(/\/$/, '/') + String(path || '').replace(/^\//, '');
        },
        async api(path, options = {}) {
            const headers = {...(options.headers || {})};
            if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
            const r = await fetch(this.apiUrl(path), {credentials: 'include', ...options, headers});
            if (!r.ok) {
                let m = '操作失败';
                try {
                    m = (await r.json()).message || m;
                } catch (_) {
                }
                if (r.status === 401) {
                    location.href = (typeof appUrl === 'function') ? appUrl('/') : '/';
                }
                const err = new Error(m);
                err.status = r.status;
                throw err;
            }
            if (path !== 'auth/heartbeat') this.bumpIdle();
            const type = r.headers.get('content-type') || '';
            return type.includes('json') ? r.json() : r.text();
        },
        bumpIdle() {
            if (!this.token) return;
            clearTimeout(this.idleTimer);
            this.idleTimer = setTimeout(() => {
                location.href = (typeof appUrl === 'function') ? appUrl('/') : '/';
            }, this.idleMs);
        },
        isAuthError(error) {
            if (!error) return false;
            if (error.status === 401) return true;
            return /请先登录|登录已过期|需要管理员|账号不可用|请先修改初始密码|未操作/.test(error.message || '');
        },
        notify(message) {
            this.toast = message;
            setTimeout(() => this.toast = '', 2200);
        },
        async selectTab(tab) {
            this.tab = tab;
            try {
                if (tab === 'dashboard') await this.loadStats();
                if (tab === 'users') await this.loadUsers();
                if (tab === 'online') await this.loadOnline();
                if (tab === 'words') this.words = await this.api('admin/words');
                if (tab === 'content') this.contents = await this.api('admin/content');
                if (tab === 'templates') this.templates = await this.api('admin/templates');
                if (tab === 'resources') this.resources = await this.api('resources');
                if (tab === 'email') await this.loadPreview();
                if ((tab === 'records' || tab === 'mistakes') && !this.users.length) await this.loadUsers();
            } catch (e) {
                this.notify(e.message);
            }
        },
        refresh() {
            this.selectTab(this.tab);
        },
        async loadStats() {
            if (this.statsBusy) return;
            this.statsBusy = true;
            try {
                this.stats = await this.api('admin/stats');
            } finally {
                this.statsBusy = false;
            }
        }, async loadUsers() {
            this.users = await this.api('admin/users');
        }, async loadOnline() {
            if (this.onlineBusy) return;
            this.onlineBusy = true;
            try {
                this.online = await this.api('admin/online');
            } finally {
                this.onlineBusy = false;
            }
        },
        newUser() {
            this.modal = 'user';
            this.modalTitle = '添加用户';
            this.form = {
                username: '',
                name: '',
                role: 'USER',
                stage: '幼小衔接',
                enabled: true,
                permissions: ['CHINESE', 'MATH', 'ENGLISH', 'PRIMARY', 'RESOURCES', 'MISTAKES', 'RECORDS', 'STATS', 'PRINT']
            };
        },
        editUser(user) {
            this.modal = 'user';
            this.modalTitle = '编辑用户与权限';
            this.form = JSON.parse(JSON.stringify(user));
        },
        async resetPassword(user) {
            if (!await AppDialog.confirm(`把 ${user.name} 的密码重置为123456？`)) return;
            try {
                const r = await this.api(`admin/users/${user.id}/reset-password`, {method: 'POST'});
                this.notify(r.message);
            } catch (e) {
                this.notify(e.message);
            }
        },
        async deleteUser(user) {
            if (!await AppDialog.confirm(`确定删除用户 ${user.name} 及其学习记录和错题？`)) return;
            try {
                await this.api(`admin/users/${user.id}`, {method: 'DELETE'});
                await this.loadUsers();
                this.notify('用户已删除');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async viewUserStats(user) {
            try {
                this.selectedUser = user.id;
                this.selectedUserName = user.name;
                this.userStats = await this.api(`admin/stats/${user.id}`);
                this.tab = 'userstats';
            } catch (e) {
                this.notify(e.message);
            }
        },
        editWord(word) {
            this.modal = 'word';
            this.modalTitle = word.id ? '编辑汉字' : '添加汉字';
            this.form = JSON.parse(JSON.stringify(word));
        },
        async deleteWord(word) {
            if (!await AppDialog.confirm(`删除“${word.character}”？`)) return;
            try {
                await this.api(`admin/words/${word.id}`, {method: 'DELETE'});
                this.words = await this.api('admin/words');
            } catch (e) {
                this.notify(e.message);
            }
        },
        editContent(item) {
            this.modal = 'content';
            this.modalTitle = item.id ? '编辑学科内容' : '添加学科内容';
            this.form = JSON.parse(JSON.stringify(item));
        },
        async deleteContent(item) {
            if (!await AppDialog.confirm(`删除“${item.title}”？`)) return;
            try {
                await this.api(`admin/content/${item.id}`, {method: 'DELETE'});
                this.contents = await this.api('admin/content');
            } catch (e) {
                this.notify(e.message);
            }
        },
        editTemplate(item) {
            this.modal = 'template';
            this.modalTitle = item.id ? '编辑文字题模板' : '添加文字题模板';
            this.form = JSON.parse(JSON.stringify(item));
        },
        async deleteTemplate(item) {
            if (!await AppDialog.confirm('删除这个文字题模板？')) return;
            try {
                await this.api(`admin/templates/${item.id}`, {method: 'DELETE'});
                this.templates = await this.api('admin/templates');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async saveModal() {
            try {
                if (this.modal === 'user') {
                    if (this.form.id) await this.api(`admin/users/${this.form.id}`, {
                        method: 'PUT',
                        body: JSON.stringify(this.form)
                    }); else await this.api('admin/users', {method: 'POST', body: JSON.stringify(this.form)});
                    await this.loadUsers();
                }
                if (this.modal === 'word') {
                    await this.api('admin/words', {method: 'POST', body: JSON.stringify(this.form)});
                    this.words = await this.api('admin/words');
                }
                if (this.modal === 'content') {
                    await this.api('admin/content', {method: 'POST', body: JSON.stringify(this.form)});
                    this.contents = await this.api('admin/content');
                }
                if (this.modal === 'template') {
                    await this.api('admin/templates', {method: 'POST', body: JSON.stringify(this.form)});
                    this.templates = await this.api('admin/templates');
                }
                this.modal = '';
                this.notify('保存成功');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async loadUserData() {
            if (!this.selectedUser) {
                this.records = [];
                this.mistakes = [];
                return;
            }
            try {
                if (this.tab === 'records') this.records = await this.api(`admin/records/${this.selectedUser}`); else this.mistakes = await this.api(`admin/mistakes/${this.selectedUser}`);
            } catch (e) {
                this.notify(e.message);
            }
        },
        async addRecord() {
            if (!this.selectedUser) {
                this.notify('请先选择用户');
                return;
            }
            const values = await AppDialog.form({title: '添加学习记录', fields: [
                {name: 'subject', label: '学科', value: '数学'}, {name: 'module', label: '模块名称', value: '管理员补录'},
                {name: 'total', label: '完成数量', type: 'number', value: 1, min: 0}, {name: 'correct', label: '正确数量', type: 'number', value: 1, min: 0}
            ], validate: v => !Number.isFinite(v.total) || !Number.isFinite(v.correct) || v.total < 0 || v.correct < 0 || v.correct > v.total ? '数量填写不正确' : ''});
            if (!values) return;
            const {subject, module, total, correct} = values;
            try {
                await this.api(`admin/records/${this.selectedUser}`, {
                    method: 'POST',
                    body: JSON.stringify({subject, module, stage: '幼小衔接', total, correct, durationSeconds: 0})
                });
                await this.loadUserData();
                this.notify('记录已添加');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async addMistake() {
            if (!this.selectedUser) {
                this.notify('请先选择用户');
                return;
            }
            const values = await AppDialog.form({title: '添加错题', fields: [
                {name: 'subject', label: '学科', value: '数学'}, {name: 'question', label: '题目或汉字', value: ''},
                {name: 'correctAnswer', label: '正确答案', value: ''}
            ], validate: v => !v.question ? '请填写题目或汉字' : ''});
            if (!values) return;
            const {subject, question, correctAnswer} = values;
            try {
                await this.api(`admin/mistakes/${this.selectedUser}`, {
                    method: 'POST',
                    body: JSON.stringify({subject, module: '管理员补录', question, userAnswer: '', correctAnswer})
                });
                await this.loadUserData();
                this.notify('错题已添加');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async editRecord(item) {
            const values = await AppDialog.form({title: '编辑学习记录', fields: [
                {name: 'module', label: '模块名称', value: item.module}, {name: 'total', label: '完成数量', type: 'number', value: item.total, min: 0},
                {name: 'correct', label: '正确数量', type: 'number', value: item.correct, min: 0}
            ], validate: v => v.correct > v.total ? '正确数量不能大于完成数量' : ''});
            if (!values) return;
            const {module, total, correct} = values;
            try {
                await this.api(`admin/records/${this.selectedUser}/${item.id}`, {
                    method: 'PUT',
                    body: JSON.stringify({...item, module, total, correct})
                });
                await this.loadUserData();
            } catch (e) {
                this.notify(e.message);
            }
        },
        async deleteRecord(item) {
            if (!await AppDialog.confirm('删除这条学习记录？')) return;
            try {
                await this.api(`admin/records/${this.selectedUser}/${item.id}`, {method: 'DELETE'});
                await this.loadUserData();
            } catch (e) {
                this.notify(e.message);
            }
        },
        async editMistake(item) {
            const values = await AppDialog.form({title: '编辑错题', fields: [
                {name: 'answer', label: '正确答案', value: item.correctAnswer},
                {name: 'status', label: '状态', value: item.status, options: ['待复习', '复习中', '基本掌握', '已掌握']}
            ]});
            if (!values) return;
            const {answer, status} = values;
            try {
                await this.api(`admin/mistakes/${this.selectedUser}/${item.id}`, {
                    method: 'PUT',
                    body: JSON.stringify({...item, correctAnswer: answer, status})
                });
                await this.loadUserData();
            } catch (e) {
                this.notify(e.message);
            }
        },
        async deleteMistake(item) {
            if (!await AppDialog.confirm('删除这条错题？')) return;
            try {
                await this.api(`admin/mistakes/${this.selectedUser}/${item.id}`, {method: 'DELETE'});
                await this.loadUserData();
            } catch (e) {
                this.notify(e.message);
            }
        },
        async uploadResource() {
            const file = this.$refs.upload.files[0];
            if (!file) {
                this.notify('请选择文件');
                return;
            }
            const data = new FormData();
            data.append('file', file);
            try {
                await this.api('resources?subject=' + encodeURIComponent(this.uploadSubject), {
                    method: 'POST',
                    body: data
                });
                this.resources = await this.api('resources');
                this.$refs.upload.value = '';
                this.notify('上传成功');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async deleteResource(item) {
            if (!await AppDialog.confirm(`删除 ${item.path}？`)) return;
            try {
                await this.api('resources?path=' + encodeURIComponent(item.path), {method: 'DELETE'});
                this.resources = await this.api('resources');
            } catch (e) {
                this.notify(e.message);
            }
        },
        async loadPreview() {
            try {
                this.reportPreview = (await this.api('admin/report/preview')).content;
            } catch (e) {
                this.notify(e.message);
            }
        },
        async sendReport() {
            if (!await AppDialog.confirm('立即发送今天的统计邮件？')) return;
            try {
                const r = await this.api('admin/report/send', {method: 'POST'});
                this.notify(r.message);
            } catch (e) {
                this.notify(e.message);
            }
        },
        duration(s) {
            return s < 60 ? s + '秒' : s < 3600 ? Math.round(s / 60) + '分钟' : (s / 3600).toFixed(1) + '小时';
        }, date(v) {
            return v ? new Date(v).toLocaleString('zh-CN') : '-';
        }, size(v) {
            return v < 1024 ? v + ' B' : v < 1048576 ? (v / 1024).toFixed(1) + ' KB' : (v / 1048576).toFixed(1) + ' MB';
        }
    }
}).mount('#admin');
