(function () {
    LearningRegister({
        data: {
            libraryDynasties: [],
            libraryDynasty: ''
        },
        methods: {
            selectPoetryDynasty(dynasty) {
                this.libraryDynasty = this.libraryDynasty === dynasty ? '' : dynasty;
                this.libraryTag = '';
                this.libraryTagPage = 1;
                this.loadLibraryPage(1);
            },
            resetPoetryFilters() {
                this.libraryQuery = '';
                this.libraryDynasty = '';
                this.libraryTag = '';
                this.resetLibraryFilterPages();
                this.loadLibraryPage(1);
            }
        }
    });
})();
