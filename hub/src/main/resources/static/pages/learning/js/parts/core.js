(function () {
    LearningRegister({
        data: {
            loading: false,
            error: '',
            toast: '',
            toastTimer: null,
            token: '',
            authRestoring: true,
            authMode: 'login',
            displayName: '',
            confirmPassword: '',
            inviteToken: '',
            regOptions: {openRegister: true, idleMinutes: 10},
            nameInput: '',
            passwordInput: '',
            student: null,
            view: 'home',
            heartbeatTimer: null,
            idleTimer: null,
            idleMs: 10 * 60 * 1000,
            dashboard: {},
            statsData: {},
            showPassword: false,
            passwordForm: {oldPassword: '', newPassword: '', confirmPassword: ''},
            selectedStage: '幼小衔接',
            stages: ['幼小衔接', '一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
            stagePage: 1,
            stagePageSize: 8,
            subjectName: '',
            subjectItems: [],
            subjectPage: 1,
            subjectPageSize: 4,
            statsTab: 'overview',
            statsTabs: [
                {id: 'overview', label: '总览'},
                {id: 'subjects', label: '学科'},
                {id: 'review', label: '复习进度'}
            ],
            homeLearningModules: [
                {id: 'chinese', permission: 'CHINESE', icon: '语', title: '语文区', description: '识字、听写、手写练习', meta: '从本阶段汉字开始', accent: '#d97745', view: 'chinese'},
                {id: 'math', permission: 'MATH', icon: '数', title: '数学区', description: '加减法、文字题、奥数思维', meta: '按难度生成一组练习', accent: '#3978c8', view: 'math'},
                {id: 'english', permission: 'ENGLISH', icon: '英', title: '英语区', description: '字母、单词、听力', meta: '从词卡或学习内容开始', accent: '#269264', subject: '英语'},
                {id: 'primary', permission: 'PRIMARY', icon: '小', title: '小学区', description: '幼小衔接至六年级', meta: '选择阶段查看内容', accent: '#7357e8', view: 'stages'},
                {id: 'history', permission: 'HISTORY', icon: '史', title: '历史区', description: '人物、故事、时间线', meta: '按专题阅读学习卡', accent: '#b87922', subject: '历史'},
                {id: 'chemistry', permission: 'CHEMISTRY', icon: '化', title: '化学区', description: '知识卡片、小测验', meta: '逐条打开内容详情', accent: '#168d91', subject: '化学'}
            ],
            homeManagementModules: [
                {id: 'mistakes', permission: 'MISTAKES', icon: '📝', title: '错题复习', description: '待复习错题', meta: '先处理还没掌握的题目', view: 'mistakes'},
                {id: 'stats', permission: 'STATS', icon: '📈', title: '学习统计', description: '趋势、正确率、阶段进度', meta: '查看最近学习变化', view: 'stats'},
                {id: 'records', permission: 'RECORDS', icon: '📊', title: '学习记录', description: '每次练习都会留下足迹', meta: '按时间查看完成情况', view: 'records'},
                {id: 'print', permission: 'PRINT', icon: '🖨️', title: '题目打印', description: '随机算术题与文字题', meta: '生成后预览或保存 PDF', view: 'print'},
                {id: 'resources', permission: 'RESOURCES', icon: '📚', title: '资源中心', description: '教材、汉字、词典、诗词', meta: '按模块浏览学习资源', view: 'resources'}
            ]
        },
        computed: {
            greeting() {
                const h = new Date().getHours();
                return h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好';
            },
            currentFeature() {
                if (this.view === 'chinese') return this.chineseMode === 'dictation' ? '语文听写' : '识字手写';
                if (this.view === 'math') return this.mathQuestions.length && !this.mathFinished ? '算术答题' : '数学区';
                if (this.view === 'olympiad') return this.olympiadTopic ? '奥数·' + this.olympiadTopic.name : '奥数专题';
                if (this.view === 'print') return '题目打印';
                return '';
            },
            enabledSubjectItems() {
                return (this.subjectItems || []).filter(item => item.enabled);
            },
            subjectPageCount() {
                return this.pageCount(this.enabledSubjectItems, this.subjectPageSize);
            },
            visibleSubjectItems() {
                return this.pageItems(this.enabledSubjectItems, this.subjectPage, this.subjectPageSize);
            },
            stagePageCount() {
                return this.pageCount(this.stages, this.stagePageSize);
            },
            visibleStages() {
                const start = (this.stagePage - 1) * this.stagePageSize;
                return this.pageItems(this.stages, this.stagePage, this.stagePageSize).map((stage, index) => ({
                    name: stage,
                    index: start + index
                }));
            },
            visibleHomeLearningModules() {
                return this.homeLearningModules.filter(item => this.hasPerm(item.permission));
            },
            visibleHomeManagementModules() {
                return this.homeManagementModules
                    .filter(item => this.hasPerm(item.permission))
                    .map(item => item.id === 'mistakes'
                        ? {...item, description: (this.dashboard.pendingMistakes || 0) + ' 条待复习'}
                        : item);
            },
            statsOverviewCards() {
                const today = this.statsData.today || {};
                return [
                    {id: 'study', label: '今日学习', value: this.formatDuration(today.studySeconds || 0), note: (today.sessions || 0) + ' 次练习'},
                    {id: 'completed', label: '今日完成', value: today.completed || 0, note: '题 / 字'},
                    {id: 'accuracy', label: '今日正确率', value: (today.accuracy || 0) + '%', note: '新增错题 ' + (today.newMistakes || 0)},
                    {id: 'streak', label: '连续学习', value: (this.statsData.habits || {}).streakDays || 0, note: '天'}
                ];
            },
            statsSubjectCards() {
                const chinese = this.statsData.chinese || {};
                const math = this.statsData.math || {};
                return [
                    {
                        id: 'chinese', title: '语文统计', subtitle: '识字与听写掌握情况', badge: '语文',
                        items: [
                            {label: '已学汉字', value: (chinese.learned || 0) + ' 个'},
                            {label: '认识 / 模糊 / 未学', value: (chinese.known || 0) + ' / ' + (chinese.fuzzy || 0) + ' / ' + (chinese.unknown || 0)},
                            {label: '听写正确率', value: (chinese.dictationAccuracy || 0) + '%'}
                        ]
                    },
                    {
                        id: 'math', title: '数学统计', subtitle: '练习数量与答题速度', badge: '数学',
                        items: [
                            {label: '累计完成', value: (math.total || 0) + ' 题'},
                            {label: '正确率', value: (math.accuracy || 0) + '%'},
                            {label: '平均每题', value: (math.averageSeconds || 0) + ' 秒'}
                        ]
                    }
                ];
            }
        },
        methods: {
            apiUrl(path) {
                const base = (typeof appUrl === 'function') ? appUrl('/api/learning/') : '/api/learning/';
                return base.replace(/\/$/, '/') + String(path || '').replace(/^\//, '');
            },
            openHomeModule(item) {
                if (!item) return;
                if (item.view) this.openView(item.view);
                else if (item.subject) this.openSubject(item.subject);
            },
            async api(path, options = {}) {
                const headers = {...(options.headers || {})};
                if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
                const response = await fetch(this.apiUrl(path), {credentials: 'include', ...options, headers});
                if (!response.ok) {
                    let message = '请求失败，请稍后再试';
                    try {
                        message = (await response.json()).message || message;
                    } catch (_) {
                    }
                    if (response.status === 401) {
                        this.redirectHome();
                        throw new Error(message);
                    }
                    throw new Error(message);
                }
                if (path !== 'auth/heartbeat') this.bumpIdle();
                const type = response.headers.get('content-type') || '';
                return type.includes('json') ? response.json() : response.text();
            },
            redirectHome() {
                location.href = (typeof appUrl === 'function') ? appUrl('/') : '/';
            },
            async loadRegistrationOptions() {
                try {
                    const q = this.inviteToken ? ('?invite=' + encodeURIComponent(this.inviteToken)) : '';
                    this.regOptions = await this.api('auth/registration' + q);
                    this.idleMs = Math.max(1, Number(this.regOptions.idleMinutes || 10)) * 60 * 1000;
                } catch (_) {
                    this.regOptions = {openRegister: true, idleMinutes: 10};
                }
            },
            enterStudent() {
                this.redirectHome();
            },
            registerStudent() {
                this.redirectHome();
            },
            afterLogin() {
                this.selectedStage = this.student.stage || '幼小衔接';
                this.view = 'home';
                this.viewStack = [];
                this.statsTab = 'overview';
                this.closeDetail();
                if (this.visibleLibraryTypes && this.visibleLibraryTypes.length && !this.visibleLibraryTypes.some(item => item.id === this.libraryType)) this.libraryType = this.visibleLibraryTypes[0].id;
                if (this.student.mustChangePassword) {
                    this.passwordForm = {oldPassword: '123456', newPassword: '', confirmPassword: ''};
                    this.showPassword = true;
                } else this.loadDashboard();
                clearInterval(this.heartbeatTimer);
                this.sendHeartbeat();
                this.heartbeatTimer = setInterval(() => this.sendHeartbeat(), 30000);
                this.bumpIdle();
            },
            bumpIdle() {
                if (!this.student) return;
                clearTimeout(this.idleTimer);
                this.idleTimer = setTimeout(() => this.idleLogout(), this.idleMs);
            },
            idleLogout() {
                this.clearSession();
                this.redirectHome();
            },
            leaveStudent() {
                this.clearSession();
                this.redirectHome();
            },
            clearSession() {
                clearInterval(this.heartbeatTimer);
                clearTimeout(this.idleTimer);
                this.token = '';
                this.student = null;
                this.authRestoring = false;
                this.view = 'home';
                this.viewStack = [];
                this.closeDetail();
                localStorage.removeItem('learningToken');
                if (this.revokeMediaUrls) this.revokeMediaUrls();
            },
            async sendHeartbeat() {
                if (!this.student || this.heartbeatBusy || (window.AppQuality && !window.AppQuality.canRequest())) return;
                this.heartbeatBusy = true;
                try {
                    await this.api('auth/heartbeat', {
                        method: 'POST',
                        body: JSON.stringify({
                            page: this.pageName(),
                            feature: this.currentFeature,
                            device: this.deviceType()
                        })
                    });
                } catch (_) {
                } finally {
                    this.heartbeatBusy = false;
                }
            },
            deviceType() {
                const ua = navigator.userAgent;
                return /iPad|Tablet/i.test(ua) ? '平板' : /Mobile|Android|iPhone/i.test(ua) ? '手机' : '电脑';
            },
            pageName() {
                return {
                    home: '首页',
                    chinese: '语文区',
                    math: '数学区',
                    olympiad: '奥数思维',
                    mistakes: '错题库',
                    records: '学习记录',
                    resources: '资源中心',
                    stats: '学习统计',
                    print: '题目打印',
                    stages: '小学阶段',
                    subject: this.subjectName + '区'
                }[this.view] || this.view;
            },
            hasPerm(permission) {
                return this.student && ('ADMIN' === this.student.role || (this.student.permissions || []).includes(permission));
            },
            openAdmin() {
                if (this.student && this.student.mustChangePassword) {
                    this.showToast('请先修改初始密码，再进入管理后台');
                    return;
                }
                window.location.href = (typeof appUrl === 'function') ? appUrl('/pages/admin/admin.html') : '/pages/admin/admin.html';
            },
            async changePassword() {
                if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
                    this.showToast('两次输入的新密码不一致');
                    return;
                }
                try {
                    await this.api('auth/password', {method: 'POST', body: JSON.stringify(this.passwordForm)});
                    this.student.mustChangePassword = false;
                    this.showPassword = false;
                    this.passwordForm = {oldPassword: '', newPassword: '', confirmPassword: ''};
                    await this.loadDashboard();
                    this.showToast('密码已修改');
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            async loadDashboard() {
                try {
                    this.statsData = await this.api('stats');
                    this.dashboard = this.statsData.today || {};
                } catch (error) {
                    this.dashboard = {};
                    if (this.hasPerm('STATS')) this.showToast(error.message);
                }
            },
            goHome() {
                this.view = 'home';
                this.viewStack = [];
                this.statsTab = 'overview';
                this.mathQuestions = [];
                this.dictationWord = null;
                this.loadDashboard();
                this.closeDetail();
                this.sendHeartbeat();
            },
            goBack() {
                if (this.detail) {
                    this.closeDetail();
                    return;
                }
                if (this.preview) {
                    this.closePreview();
                    return;
                }
                if (this.printPreview) {
                    this.closePrintPreview();
                    return;
                }
                if (this.view === 'olympiad' && this.olympiadTopic) {
                    this.leaveOlympiad();
                    return;
                }
                if (this.view === 'resources' && this.libraryType === 'textbooks' && this.textbookPrefix) {
                    this.textbookUp();
                    return;
                }
                if (this.view === 'home') {
                    location.href = (typeof appUrl === 'function') ? appUrl('/pages/lobby/index.html') : '../lobby/index.html';
                    return;
                }
                const previous = this.viewStack.pop();
                if (previous) {
                    this.view = previous.view;
                    this.subjectName = previous.subjectName || '';
                    this.selectedStage = previous.selectedStage || this.selectedStage;
                    this.sendHeartbeat();
                    return;
                }
                this.goHome();
            },
            async openView(name, options = {}) {
                if (this.view !== name && !options.replace) {
                    this.viewStack.push({
                        view: this.view,
                        subjectName: this.subjectName,
                        selectedStage: this.selectedStage
                    });
                }
                if (this.view !== name) this.closeDetail();
                this.view = name;
                if (name === 'chinese') await this.loadWords();
                if (name === 'mistakes') await this.loadMistakes();
                if (name === 'records') await this.loadRecords();
                if (name === 'resources') {
                    await this.searchLibrary();
                    await this.loadResources();
                }
                if (name === 'stats') await this.loadStats();
                if (name === 'print' && !this.printQuestions.length) await this.generatePrintable();
                this.sendHeartbeat();
            },
            async openSubject(subject) {
                this.subjectPage = 1;
                await this.openView('subject');
                this.subjectName = subject;
                try {
                    this.subjectItems = await this.api('content?subject=' + encodeURIComponent(subject));
                } catch (error) {
                    this.showToast(error.message);
                }
                this.sendHeartbeat();
            },
            changeSubjectPage(page) {
                this.changeCollectionPage('subjectPage', page, this.subjectPageCount);
            },
            changeStagePage(page) {
                this.changeCollectionPage('stagePage', page, this.stagePageCount);
            },
            chooseStage(stage) {
                this.selectedStage = stage;
                this.showToast('已选择' + stage);
                this.openView('chinese');
            },
            showToast(message) {
                this.toast = message;
                clearTimeout(this.toastTimer);
                this.toastTimer = setTimeout(() => this.toast = '', 2200);
            },
            saveRecord(subject, module, total, correct, details = {}, durationSeconds = 0) {
                return this.api('records', {
                    method: 'POST',
                    body: JSON.stringify({
                        subject,
                        module,
                        stage: this.selectedStage,
                        total,
                        correct,
                        durationSeconds,
                        details
                    })
                });
            },
            addMistake(item) {
                return this.api('mistakes', {
                    method: 'POST',
                    body: JSON.stringify({stage: this.selectedStage, ...item})
                });
            },
            async loadMistakes() {
                try {
                    const q = this.mistakeSubject ? '?subject=' + encodeURIComponent(this.mistakeSubject) : '';
                    this.mistakeList = await this.api('mistakes' + q);
                    this.mistakePage = 1;
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            changeMistakePage(page) {
                this.changeCollectionPage('mistakePage', page, this.mistakePageCount);
            },
            async reviewMistake(item, correct) {
                try {
                    await this.api(`mistakes/${item.id}/review`, {method: 'POST', body: JSON.stringify({correct})});
                    this.showToast(correct ? '复习成功' : '下次继续加油');
                    await this.loadMistakes();
                    this.closeDetail();
                    await this.loadDashboard();
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            statusClass(status) {
                return status === '已掌握' ? 'status-mastered' : status === '待复习' ? 'status-pending' : 'status-learning';
            },
            async loadRecords() {
                try {
                    this.recordList = await this.api('records');
                    this.recordPage = 1;
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            async loadStats() {
                try {
                    this.statsData = await this.api('stats');
                    this.dashboard = this.statsData.today || {};
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            formatDate(value) {
                return value ? new Date(value).toLocaleString('zh-CN', {
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit'
                }) : '';
            },
            formatDuration(seconds) {
                return seconds < 60 ? seconds + '秒' : seconds < 3600 ? Math.round(seconds / 60) + '分钟' : (seconds / 3600).toFixed(1) + '小时';
            },
            formatDetails(details) {
                if (!details || typeof details !== 'object') return '暂无补充信息';
                const entries = Object.entries(details);
                return entries.length ? entries.map(item => item[0] + '：' + item[1]).join(' · ') : '暂无补充信息';
            }
        }
    });
})();
