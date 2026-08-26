(function () {
    const detailCollections = {
        word: {
            items: 'words',
            page: 'chinesePage',
            size: 'chinesePageSize',
            selected: 'selectedWord',
            title(item) {
                return item.character + ' · 识字练习';
            },
            meta(item, index, total) {
                return `${this.selectedStage} · 第 ${index + 1} / ${total} 个字`;
            }
        },
        mistake: {
            items: 'mistakeList',
            page: 'mistakePage',
            size: 'mistakePageSize',
            title: '错题详情',
            meta(item) {
                return `${item.subject} · ${item.module}`;
            }
        },
        record: {
            items: 'recordList',
            page: 'recordPage',
            size: 'recordPageSize',
            title: '学习记录详情',
            meta(item) {
                return `${item.subject} · ${item.module}`;
            }
        },
        subject: {
            items: 'enabledSubjectItems',
            page: 'subjectPage',
            size: 'subjectPageSize',
            title(item) {
                return item.title;
            },
            meta(item) {
                return `${item.stage} · ${item.type || '学习内容'}`;
            }
        }
    };

    const LearningPager = {
        props: {
            page: {type: Number, default: 1},
            pageCount: {type: Number, default: 1},
            total: {type: Number, default: 0},
            loading: {type: Boolean, default: false}
        },
        emits: ['change'],
        computed: {
            currentPage() {
                return Math.max(1, Number(this.page) || 1);
            },
            totalPages() {
                return Math.max(1, Number(this.pageCount) || 1);
            },
            pages() {
                const count = this.totalPages;
                const current = Math.min(this.currentPage, count);
                const start = Math.max(1, Math.min(current - 2, count - 4));
                const end = Math.min(count, start + 4);
                const result = [];
                for (let number = start; number <= end; number++) result.push(number);
                return result;
            }
        },
        methods: {
            change(page) {
                const next = Math.max(1, Math.min(Number(page) || 1, this.totalPages));
                if (next !== this.currentPage && !this.loading) this.$emit('change', next);
            }
        },
        template: `
            <nav v-if="total > 0" class="learning-pagination" aria-label="分页">
                <span class="pagination-total">共 {{ total }} 条</span>
                <button type="button" :disabled="loading || currentPage <= 1" @click="change(currentPage - 1)">上一页</button>
                <button v-for="number in pages" :key="number" type="button"
                        :class="{active:number===currentPage}" :disabled="loading || number===currentPage"
                        @click="change(number)">{{ number }}</button>
                <button type="button" :disabled="loading || currentPage >= totalPages" @click="change(currentPage + 1)">下一页</button>
                <span class="pagination-index">第 {{ currentPage }} / {{ totalPages }} 页</span>
            </nav>
        `
    };

    const LearningDetail = {
        props: {
            title: {type: String, default: ''},
            meta: {type: String, default: ''},
            canPrev: {type: Boolean, default: false},
            canNext: {type: Boolean, default: false},
            loading: {type: Boolean, default: false}
        },
        emits: ['close', 'prev', 'next'],
        template: `
            <div class="learning-detail-mask" @click.self="$emit('close')">
                <section class="learning-detail" role="dialog" aria-modal="true" :aria-label="title">
                    <header class="detail-header">
                        <div><p v-if="meta" class="detail-meta">{{ meta }}</p><h2>{{ title }}</h2></div>
                        <button type="button" class="detail-close" aria-label="关闭详情" @click="$emit('close')">×</button>
                    </header>
                    <div class="detail-content" :class="{loading:loading}">
                        <p v-if="loading" class="detail-loading">正在打开详情…</p>
                        <slot v-else></slot>
                    </div>
                    <footer class="detail-footer">
                        <button type="button" :disabled="!canPrev || loading" @click="$emit('prev')">上一条</button>
                        <span v-if="canPrev || canNext">详情导航</span>
                        <button type="button" :disabled="!canNext || loading" @click="$emit('next')">下一条</button>
                    </footer>
                </section>
            </div>
        `
    };

    LearningRegister({
        components: {LearningPager, LearningDetail},
        data: {
            viewStack: [],
            homePage: 1,
            statsPage: 1,
            detail: null
        },
        computed: {
            detailTitle() {
                if (!this.detail) return '';
                return this.detail.title || '学习详情';
            },
            detailMeta() {
                return this.detail && this.detail.meta ? this.detail.meta : '';
            },
            detailCanPrev() {
                return !!this.detail && Number(this.detail.index) > 0;
            },
            detailCanNext() {
                return !!this.detail && Number(this.detail.index) >= 0 && Number(this.detail.index) < Number(this.detail.total || 0) - 1;
            }
        },
        methods: {
            openDetail(kind, item, index, options) {
                this.detail = Object.assign({kind, item, index: Number(index), total: 0}, options || {});
            },
            closeDetail() {
                this.detail = null;
            },
            pageCount(items, size) {
                return Math.max(1, Math.ceil((items || []).length / Math.max(1, Number(size) || 1)));
            },
            pageItems(items, page, size) {
                const pageSize = Math.max(1, Number(size) || 1);
                const currentPage = Math.max(1, Number(page) || 1);
                return (items || []).slice((currentPage - 1) * pageSize, currentPage * pageSize);
            },
            changeCollectionPage(pageKey, page, pageCount) {
                this[pageKey] = Math.max(1, Math.min(Number(page) || 1, Number(pageCount) || 1));
            },
            pagedCollection(kind) {
                const config = detailCollections[kind];
                if (!config) return null;
                return {...config, list: this[config.items] || []};
            },
            async openPagedDetail(kind, localIndex) {
                const collection = this.pagedCollection(kind);
                if (!collection) return;
                const page = Number(this[collection.page]) || 1;
                const size = Math.max(1, Number(this[collection.size]) || 1);
                return this.openPagedDetailAt(kind, (page - 1) * size + Number(localIndex));
            },
            async openPagedDetailAt(kind, index) {
                const collection = this.pagedCollection(kind);
                const absoluteIndex = Number(index);
                if (!collection || !Number.isInteger(absoluteIndex) || absoluteIndex < 0 || absoluteIndex >= collection.list.length) return;
                const size = Math.max(1, Number(this[collection.size]) || 1);
                const item = collection.list[absoluteIndex];
                this[collection.page] = Math.floor(absoluteIndex / size) + 1;
                if (collection.selected) this[collection.selected] = item;
                const title = typeof collection.title === 'function' ? collection.title.call(this, item, absoluteIndex, collection.list.length) : collection.title;
                const meta = typeof collection.meta === 'function' ? collection.meta.call(this, item, absoluteIndex, collection.list.length) : '';
                this.openDetail(kind, item, absoluteIndex, {total: collection.list.length, title, meta});
                if (kind === 'word' && typeof this.initCanvas === 'function') await this.$nextTick(() => this.initCanvas());
            },
            openPagedDetailMove(kind, delta) {
                if (!this.detail || this.detail.kind !== kind) return;
                return this.openPagedDetailAt(kind, Number(this.detail.index) + Number(delta));
            },
            async moveDetail(delta) {
                if (!this.detail) return;
                if (this.detail.kind === 'library') return this.shiftLibraryItem(delta);
                return this.openPagedDetailMove(this.detail.kind, delta);
            }
        }
    });
})();
