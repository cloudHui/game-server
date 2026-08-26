(function () {
    const {nextTick} = Vue;
    LearningRegister({
        data: {
            libraryType: 'english',
            libraryQuery: '',
            libraryItems: [],
            libraryLoading: false,
            libraryTip: '',
            libraryStatusTip: '',
            libraryTags: [],
            libraryTag: '',
            libraryPage: 1,
            libraryPageCount: 1,
            libraryTotal: 0,
            libraryPageSize: 4,
            libraryTagPage: 1,
            libraryTagPageSize: 6,
            libraryDynastyPage: 1,
            libraryDynastyPageSize: 4,
            librarySelected: null,
            librarySelectedIndex: -1,
            libraryPanelImage: '',
            textbookFolderPage: 1,
            textbookBookPage: 1,
            textbookPageSize: 4,
            textbookMode: 'browse',
            textbookPrefix: '',
            textbookFolders: [],
            strokeData: null,
            englishAudio: null,
            libraryTypes: [
                {id: 'english', permission: 'ENGLISH', pageSize: 4, name: '英语图卡', icon: '🎧', description: '按标签与搜索'},
                {id: 'vocab', permission: 'ENGLISH', pageSize: 4, name: '常用单词', icon: 'Aa', description: '按标签与搜索'},
                {id: 'character', permission: 'CHINESE', pageSize: 4, name: '汉字笔顺', icon: '✍️', description: '按常用字浏览'},
                {id: 'poetry', permission: 'CHINESE', pageSize: 4, name: '古诗词', icon: '📜', description: '按朝代与诗人'},
                {id: 'dictionary', permission: 'ENGLISH', pageSize: 4, name: '英汉词典', icon: '🔤', description: '按 A-Z 字母'},
                {id: 'textbooks', permission: 'RESOURCES', pageSize: 24, name: '教材目录', icon: '📚', description: '按目录浏览'}
            ]
        },
        computed: {
            libraryPlaceholder() {
                return {
                    textbooks: '输入年级、科目或书名，如：数学',
                    character: '输入一个汉字，如：学',
                    dictionary: '输入英文单词，如：apple',
                    poetry: '输入篇名或作者，如：静夜思',
                    english: '搜索图卡单词，如：dog',
                    vocab: '搜索常用词或中文，如：apple'
                }[this.libraryType] || '输入查询内容';
            },
            libraryHint() {
                return {
                    textbooks: '目录浏览或搜索书名；只打开外部链接，不下载 PDF。',
                    character: '可按「常用」标签或搜索单字；点字看笔顺。',
                    dictionary: '可按字母标签或搜索单词；点条目看释义。',
                    poetry: '默认精选；可按作者标签或搜索篇名。',
                    english: '可按主题标签或搜索；点词后打开详情听发音看图。',
                    vocab: '可按主题标签或搜索；点词后打开详情听美音。'
                }[this.libraryType] || '';
            },
            visibleLibraryTypes() {
                return this.libraryTypes.filter(item => this.hasPerm(item.permission));
            },
            visibleTextbookFolders() {
                return this.pageItems(this.textbookFolders, this.textbookFolderPage, this.textbookPageSize);
            },
            textbookFolderPageCount() {
                return this.pageCount(this.textbookFolders, this.textbookPageSize);
            },
            visibleTextbookItems() {
                return this.pageItems(this.libraryItems, this.textbookBookPage, this.textbookPageSize);
            },
            textbookBookPageCount() {
                return this.pageCount(this.libraryItems, this.textbookPageSize);
            },
            visibleLibraryTags() {
                return this.pageItems(this.libraryTags, this.libraryTagPage, this.libraryTagPageSize);
            },
            libraryTagPageCount() {
                return this.pageCount(this.libraryTags, this.libraryTagPageSize);
            },
            visibleLibraryDynasties() {
                return this.pageItems(this.libraryDynasties, this.libraryDynastyPage, this.libraryDynastyPageSize);
            },
            libraryDynastyPageCount() {
                return this.pageCount(this.libraryDynasties, this.libraryDynastyPageSize);
            },
            libraryFilterLabel() {
                if (this.libraryType === 'dictionary') return '字母';
                if (this.libraryType === 'poetry') return '诗人';
                if (this.libraryType === 'character') return '范围';
                return '分类';
            },
            libraryFilterAllLabel() {
                return this.libraryType === 'dictionary' ? '全部字母' : '全部';
            },
            libraryResultsTitle() {
                return {
                    dictionary: '词条列表',
                    poetry: '古诗词列表',
                    character: '汉字列表',
                    english: '英语图卡',
                    vocab: '常用单词'
                }[this.libraryType] || '学习资料列表';
            },
            libraryFilterHint() {
                if (this.libraryType === 'dictionary') return '按首字母分组，每页显示 6 个字母';
                if (this.libraryType === 'poetry') return '先按朝代，再按诗人分组';
                return '按分类分组，每页显示 6 个标签';
            }
        },
        methods: {
            async selectLibraryType(type) {
                this.libraryType = type;
                this.libraryQuery = '';
                this.libraryItems = [];
                this.libraryTip = '';
                this.libraryTag = '';
                this.libraryDynasty = '';
                this.libraryDynasties = [];
                this.libraryPage = 1;
                this.libraryTags = [];
                this.libraryTotal = 0;
                this.libraryPageCount = 1;
                this.resetLibraryFilterPages();
                const config = this.libraryTypes.find(item => item.id === type);
                this.libraryPageSize = config ? config.pageSize || 24 : 24;
                this.clearLibrarySelection();
                this.textbookPrefix = '';
                this.textbookFolders = [];
                this.textbookFolderPage = 1;
                this.textbookBookPage = 1;
                if (type === 'textbooks') this.textbookMode = 'browse';
                await this.refreshLibraryStatus();
                await this.searchLibrary();
            },
            async refreshLibraryStatus() {
                try {
                    const status = await this.api('library/status');
                    const missing = [];
                    if (this.libraryType === 'english' && !status.english) missing.push('儿童英语图卡包');
                    if (this.libraryType === 'vocab' && !status.vocab) missing.push('常用单词美音包');
                    if (this.libraryType === 'character' && !status.characters) missing.push('汉字笔顺包');
                    if (this.libraryType === 'dictionary' && !status.dictionary) missing.push('英汉词典包');
                    if (this.libraryType === 'poetry' && !status.poetry) missing.push('古诗词全库（精选仍可用）');
                    if (this.libraryType === 'textbooks' && !status.textbooks) missing.push('教材目录');
                    this.libraryStatusTip = missing.length ? '尚未就绪：' + missing.join('、') + '。请确认安装时已解压 datasets。' : '';
                } catch (_) {
                    this.libraryStatusTip = '';
                }
            },
            async searchLibrary() {
                this.clearLibrarySelection();
                if (this.libraryType === 'textbooks') {
                    this.libraryLoading = true;
                    this.libraryTip = '';
                    try {
                        if (this.textbookMode === 'browse' && !this.libraryQuery) {
                            await this.loadTextbookTree();
                            return;
                        }
                        this.libraryItems = await this.api('library/textbooks?query=' + encodeURIComponent(this.libraryQuery || ''));
                        this.textbookFolderPage = 1;
                        this.textbookBookPage = 1;
                        if (!this.libraryItems.length) this.libraryTip = '没有找到匹配教材。';
                    } catch (error) {
                        this.libraryItems = [];
                        this.libraryTip = error.message || '查询失败，请稍后再试';
                        this.showToast(error.message);
                    } finally {
                        this.libraryLoading = false;
                    }
                    return;
                }
                await this.loadLibraryPage(1);
            },
            async loadLibraryPage(page) {
                this.libraryLoading = true;
                this.libraryTip = '';
                this.clearLibrarySelection();
                try {
                    const params = {
                        query: this.libraryQuery || '',
                        tag: this.libraryTag || '',
                        page: String(page || 1),
                        size: String(this.libraryPageSize || 24)
                    };
                    if (this.libraryType === 'poetry') params.dynasty = this.libraryDynasty || '';
                    const q = new URLSearchParams(params);
                    const data = await this.api('library/' + this.libraryType + '?' + q.toString());
                    this.libraryItems = data.items || [];
                    this.libraryTags = data.tags || [];
                    this.libraryDynasties = data.dynasties || [];
                    this.libraryPage = data.page || 1;
                    this.libraryPageCount = data.pageCount || 1;
                    this.libraryTotal = data.total || 0;
                    this.libraryPageSize = data.size || this.libraryPageSize;
                    if (this.libraryTagPage > this.libraryTagPageCount) this.libraryTagPage = 1;
                    if (this.libraryDynastyPage > this.libraryDynastyPageCount) this.libraryDynastyPage = 1;
                    if (!this.libraryItems.length) this.libraryTip = '没有内容，换个筛选或关键词试试。';
                } catch (error) {
                    this.libraryItems = [];
                    this.libraryTip = error.message || '查询失败，请稍后再试';
                    this.showToast(error.message);
                } finally {
                    this.libraryLoading = false;
                }
            },
            selectLibraryTag(tag) {
                this.libraryTag = this.libraryTag === tag ? '' : tag;
                this.loadLibraryPage(1);
            },
            resetLibraryFilterPages() {
                this.libraryTagPage = 1;
                this.libraryDynastyPage = 1;
            },
            changeLibraryTagPage(page) {
                this.changeCollectionPage('libraryTagPage', page, this.libraryTagPageCount);
            },
            changeLibraryDynastyPage(page) {
                this.changeCollectionPage('libraryDynastyPage', page, this.libraryDynastyPageCount);
            },
            clearLibrarySelection() {
                this.closeDetail();
                this.librarySelected = null;
                this.librarySelectedIndex = -1;
                this.libraryPanelImage = '';
                this.strokeData = null;
                if (this.englishAudio) {
                    this.englishAudio.pause();
                    this.englishAudio = null;
                }
            },
            libraryItemKey(item, index) {
                return item.word || item.character || item.title || item.path || index;
            },
            async selectLibraryItem(index) {
                if (index < 0 || index >= this.libraryItems.length) return;
                this.librarySelectedIndex = index;
                this.librarySelected = this.libraryItems[index];
                this.libraryPanelImage = '';
                this.strokeData = null;
                if (this.libraryType === 'character') {
                    try {
                        this.strokeData = await this.api('library/character?value=' + encodeURIComponent(this.librarySelected.character));
                    } catch (error) {
                        this.showToast(error.message);
                    }
                } else if ((this.libraryType === 'english' || this.libraryType === 'vocab') && this.librarySelected.imagePath) {
                    try {
                        this.libraryPanelImage = await this.ensureMediaUrl(this.librarySelected.imagePath);
                    } catch (_) {
                        this.libraryPanelImage = '';
                    }
                }
                this.openDetail('library', this.librarySelected, index, {
                    total: this.libraryItems.length,
                    title: this.libraryTitle(this.librarySelected),
                    meta: this.libraryType === 'poetry' ? '古诗词详情' : '学习库详情'
                });
                await nextTick(() => this.libraryType === 'character' && this.playStrokeAnimation());
            },
            shiftLibraryItem(delta) {
                const next = this.librarySelectedIndex + delta;
                if (next < 0 || next >= this.libraryItems.length) return;
                this.selectLibraryItem(next);
            },
            async playSelectedAudio() {
                const item = this.librarySelected;
                if (!item || !item.word) return;
                try {
                    if (item.audioPath) {
                        const url = await this.ensureMediaUrl(item.audioPath);
                        if (this.englishAudio) this.englishAudio.pause();
                        this.englishAudio = new Audio(url);
                        this.englishAudio.play().catch(() => this.showToast('音频播放被浏览器拦截，请再点一次'));
                    } else {
                        this.speakEnglish(item.word);
                    }
                    await this.saveRecord('英语', this.libraryType === 'vocab' ? '常用单词' : '听说图卡', 1, 1, {word: item.word});
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            async loadTextbookTree() {
                this.libraryLoading = true;
                this.libraryTip = '';
                try {
                    const data = await this.api('library/textbooks/tree?prefix=' + encodeURIComponent(this.textbookPrefix || ''));
                    this.textbookFolders = data.folders || [];
                    this.libraryItems = data.books || [];
                    this.textbookFolderPage = 1;
                    this.textbookBookPage = 1;
                    if (!this.textbookFolders.length && !this.libraryItems.length) this.libraryTip = '这个目录下没有教材。';
                } catch (error) {
                    this.libraryTip = error.message;
                    this.showToast(error.message);
                } finally {
                    this.libraryLoading = false;
                }
            },
            openTextbookFolder(folder) {
                this.textbookPrefix = this.textbookPrefix ? `${this.textbookPrefix}/${folder}` : folder;
                this.loadTextbookTree();
            },
            textbookUp() {
                if (!this.textbookPrefix) return;
                const parts = this.textbookPrefix.split('/');
                parts.pop();
                this.textbookPrefix = parts.join('/');
                this.loadTextbookTree();
            },
            changeTextbookFolderPage(page) {
                this.changeCollectionPage('textbookFolderPage', page, this.textbookFolderPageCount);
            },
            changeTextbookBookPage(page) {
                this.changeCollectionPage('textbookBookPage', page, this.textbookBookPageCount);
            },
            libraryTitle(item) {
                if (item.title) return item.title + (item.dynasty ? ' · ' + item.dynasty : '') + (item.author ? ' · ' + item.author : '');
                if (item.word) return item.word;
                return item.path || item.character || '学习资料';
            },
            libraryText(item) {
                if (item.paragraphs) return (item.paragraphs || []).slice(0, 1).join(' ');
                if (item.translation) return item.translation;
                if (Array.isArray(item.pinyin)) return item.pinyin.join(' ');
                if (item.pinyin) return item.pinyin;
                if (item.definition) return item.definition;
                if (item.path && item.size != null) return this.formatSize(item.size);
                return item.author || '';
            },
            openLibrary(item) {
                if (item.url) window.open(item.url, '_blank', 'noopener');
            },
            async showStrokeFor(character) {
                if (!character) return;
                this.libraryType = 'character';
                this.librarySelected = {character};
                this.librarySelectedIndex = -1;
                this.libraryTag = '';
                this.strokeData = null;
                this.openDetail('library', this.librarySelected, -1, {
                    total: 0,
                    title: character + ' · 笔顺',
                    meta: '汉字笔顺详情',
                    loading: true
                });
                try {
                    this.strokeData = await this.api('library/character?value=' + encodeURIComponent(character));
                    if (this.detail) this.detail.loading = false;
                    await nextTick(() => this.playStrokeAnimation());
                } catch (error) {
                    this.closeDetail();
                    this.showToast(error.message);
                }
            },
            practiceStrokeCharacter() {
                const ch = (this.strokeData && this.strokeData.character) || (this.librarySelected && this.librarySelected.character);
                if (!ch) return;
                this.selectedStage = this.selectedStage || '幼小衔接';
                this.closeDetail();
                this.openView('chinese');
                this.showToast('可在语文区对照“' + ch + '”练写');
            },
            async recordPoetryRead() {
                if (!this.librarySelected || this.libraryType !== 'poetry') return;
                try {
                    await this.saveRecord('语文', '古诗词阅读', 1, 1, {
                        title: this.librarySelected.title,
                        author: this.librarySelected.author
                    });
                    this.showToast('已记录一次诗词阅读');
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            playStrokeAnimation() {
                const canvas = this.$refs.strokeCanvas;
                const data = this.strokeData;
                if (!canvas || !data || !Array.isArray(data.medians)) return;
                const ctx = canvas.getContext('2d');
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                ctx.lineCap = 'round';
                ctx.lineJoin = 'round';
                ctx.strokeStyle = '#7357e8';
                ctx.lineWidth = 8;
                const all = data.medians.flat();
                let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
                all.forEach(p => {
                    if (!p || p.length < 2) return;
                    minX = Math.min(minX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxX = Math.max(maxX, p[0]);
                    maxY = Math.max(maxY, p[1]);
                });
                if (!isFinite(minX)) return;
                const pad = 30;
                const scale = Math.min((canvas.width - pad * 2) / Math.max(1, maxX - minX), (canvas.height - pad * 2) / Math.max(1, maxY - minY));
                const map = (x, y) => ({x: pad + (x - minX) * scale, y: canvas.height - pad - (y - minY) * scale});
                let strokeIndex = 0, pointIndex = 0, drawing = null;
                const step = () => {
                    if (strokeIndex >= data.medians.length) return;
                    const stroke = data.medians[strokeIndex] || [];
                    if (pointIndex === 0) {
                        drawing = null;
                        ctx.beginPath();
                    }
                    if (pointIndex >= stroke.length) {
                        strokeIndex++;
                        pointIndex = 0;
                        setTimeout(step, 180);
                        return;
                    }
                    const p = map(stroke[pointIndex][0], stroke[pointIndex][1]);
                    if (!drawing) {
                        ctx.moveTo(p.x, p.y);
                        drawing = p;
                    } else {
                        ctx.lineTo(p.x, p.y);
                        ctx.stroke();
                        ctx.beginPath();
                        ctx.moveTo(p.x, p.y);
                    }
                    pointIndex++;
                    setTimeout(step, 16);
                };
                step();
            },
            speakEnglish(text) {
                if (!('speechSynthesis' in window)) {
                    this.showToast('当前浏览器不支持英语朗读');
                    return;
                }
                speechSynthesis.cancel();
                const u = new SpeechSynthesisUtterance(text);
                u.lang = 'en-US';
                u.rate = .9;
                speechSynthesis.speak(u);
            }
        }
    });
})();
