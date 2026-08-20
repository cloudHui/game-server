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
            libraryPageSize: 24,
            librarySelected: null,
            librarySelectedIndex: -1,
            libraryPanelImage: '',
            textbookMode: 'browse',
            textbookPrefix: '',
            textbookFolders: [],
            strokeData: null,
            englishAudio: null,
            libraryTypes: [
                {id: 'english', name: '英语图卡', icon: '🎧'},
                {id: 'vocab', name: '常用单词', icon: 'Aa'},
                {id: 'character', name: '汉字笔顺', icon: '✍️'},
                {id: 'poetry', name: '古诗词', icon: '📜'},
                {id: 'dictionary', name: '英汉词典', icon: '🔤'},
                {id: 'textbooks', name: '教材目录', icon: '📚'}
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
                    english: '可按主题标签或搜索；点词后在侧栏听发音看图。',
                    vocab: '可按主题标签或搜索；点词后在侧栏听美音。'
                }[this.libraryType] || '';
            },
            visibleLibraryTypes() {
                return this.libraryTypes.filter(item => {
                    if (item.id === 'english' || item.id === 'dictionary' || item.id === 'vocab') return this.hasPerm('ENGLISH');
                    if (item.id === 'character' || item.id === 'poetry') return this.hasPerm('CHINESE');
                    return this.hasPerm('RESOURCES');
                });
            }
        },
        methods: {
            async selectLibraryType(type) {
                this.libraryType = type;
                this.libraryQuery = '';
                this.libraryItems = [];
                this.libraryTip = '';
                this.libraryTag = '';
                this.libraryPage = 1;
                this.libraryTags = [];
                this.libraryTotal = 0;
                this.libraryPageCount = 1;
                this.libraryPageSize = ({
                    vocab: 30,
                    dictionary: 30,
                    poetry: 20,
                    character: 48,
                    english: 24
                })[type] || 24;
                this.clearLibrarySelection();
                this.textbookPrefix = '';
                this.textbookFolders = [];
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
                    const q = new URLSearchParams({
                        query: this.libraryQuery || '',
                        tag: this.libraryTag || '',
                        page: String(page || 1),
                        size: String(this.libraryPageSize || 24)
                    });
                    const data = await this.api('library/' + this.libraryType + '?' + q.toString());
                    this.libraryItems = data.items || [];
                    this.libraryTags = data.tags || [];
                    this.libraryPage = data.page || 1;
                    this.libraryPageCount = data.pageCount || 1;
                    this.libraryTotal = data.total || 0;
                    this.libraryPageSize = data.size || this.libraryPageSize;
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
            changeLibraryPage(delta) {
                const next = this.libraryPage + delta;
                if (next < 1 || next > this.libraryPageCount) return;
                this.loadLibraryPage(next);
            },
            clearLibrarySelection() {
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
                        await nextTick(() => this.playStrokeAnimation());
                    } catch (error) {
                        this.showToast(error.message);
                    }
                    return;
                }
                if ((this.libraryType === 'english' || this.libraryType === 'vocab') && this.librarySelected.imagePath) {
                    try {
                        this.libraryPanelImage = await this.ensureMediaUrl(this.librarySelected.imagePath);
                    } catch (_) {
                        this.libraryPanelImage = '';
                    }
                }
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
            libraryTitle(item) {
                if (item.title) return item.title + (item.author ? ' · ' + item.author : '');
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
                this.view = 'resources';
                this.libraryType = 'character';
                this.libraryQuery = character;
                this.libraryTag = '';
                await this.searchLibrary();
                if (this.libraryItems.length) await this.selectLibraryItem(0);
            },
            practiceStrokeCharacter() {
                const ch = (this.strokeData && this.strokeData.character) || (this.librarySelected && this.librarySelected.character);
                if (!ch) return;
                this.selectedStage = this.selectedStage || '幼小衔接';
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
