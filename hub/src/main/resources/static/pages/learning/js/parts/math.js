(function () {
    const {nextTick} = Vue;
    LearningRegister({
        data: {
            mathConfig: {max: 10, count: 10, operation: 'mixed'},
            mathQuestions: [],
            mathIndex: 0,
            mathAnswer: '',
            mathCorrect: 0,
            mathFeedback: null,
            mathLocked: false,
            mathStartedAt: null,
            mathFinished: false,
            printConfig: {max: 10, count: 20, wordProblems: 5, operation: 'mixed', showAnswers: false},
            printQuestions: [],
            recordList: []
        },
        computed: {
            currentMath() {
                return this.mathQuestions[this.mathIndex] || {};
            }
        },
        methods: {
            async startMath() {
                try {
                    this.mathQuestions = await this.api(`math/questions?max=${this.mathConfig.max}&count=${this.mathConfig.count}&operation=${this.mathConfig.operation}`);
                    this.mathIndex = 0;
                    this.mathAnswer = '';
                    this.mathCorrect = 0;
                    this.mathFeedback = null;
                    this.mathFinished = false;
                    this.mathLocked = false;
                    this.mathStartedAt = Date.now();
                    await nextTick(() => this.$refs.mathInput && this.$refs.mathInput.focus());
                    this.sendHeartbeat();
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            async submitMath() {
                if (this.mathLocked || this.mathAnswer === '') return;
                this.mathLocked = true;
                const q = this.currentMath, a = Number(this.mathAnswer), correct = a === q.answer;
                if (correct) {
                    this.mathCorrect++;
                    this.mathFeedback = {correct: true, text: '答对了，真棒！'};
                } else {
                    this.mathFeedback = {correct: false, text: `正确答案是 ${q.answer}`};
                    try {
                        await this.addMistake({
                            subject: '数学',
                            module: `${this.mathConfig.max}以内算术`,
                            question: q.text,
                            userAnswer: String(a),
                            correctAnswer: String(q.answer),
                            errorType: '计算错误'
                        });
                    } catch (error) {
                        this.showToast(error.message);
                    }
                }
                setTimeout(async () => {
                    if (this.mathIndex >= this.mathQuestions.length - 1) {
                        this.mathFinished = true;
                        const seconds = Math.max(1, Math.round((Date.now() - this.mathStartedAt) / 1000));
                        try {
                            await this.saveRecord('数学', `${this.mathConfig.max}以内算术`, this.mathQuestions.length, this.mathCorrect, {operation: this.mathConfig.operation}, seconds);
                            await this.loadDashboard();
                        } catch (error) {
                            this.showToast(error.message);
                        }
                    } else {
                        this.mathIndex++;
                        this.mathAnswer = '';
                        this.mathFeedback = null;
                        this.mathLocked = false;
                        await nextTick(() => this.$refs.mathInput && this.$refs.mathInput.focus());
                    }
                }, correct ? 650 : 1400);
            },
            async generatePrintable() {
                try {
                    this.printQuestions = await this.api(`math/printable?max=${this.printConfig.max}&count=${this.printConfig.count}&operation=${this.printConfig.operation}&wordProblems=${this.printConfig.wordProblems}&stage=${encodeURIComponent(this.selectedStage)}`);
                } catch (error) {
                    this.showToast(error.message);
                }
            },
            printWorksheet() {
                window.print();
            }
        }
    });
})();
