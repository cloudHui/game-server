class Game {
    constructor() {
        this.canvas = document.getElementById('gameCanvas');
        this.ctx = this.canvas.getContext('2d');
        this.canvas.width = 800;
        this.canvas.height = 600;

        this.plane = {
            x: this.canvas.width / 2,
            y: this.canvas.height - 100,
            width: 80,
            height: 60,
            speed: 5,
            targetX: this.canvas.width / 2,
            isMoving: false,
            rotation: 0,
            targetLetter: null,  // 跟踪目标字母
            pendingAction: false // 控制飞机动作
        };

        // 游戏配置
        this.useUpperCase = false; // 默认使用小写字母
        this.maxGameTime = 60; // 默认游戏时长60秒
        this.remainingTime = this.maxGameTime;
        this.timerInterval = null;
        this.difficulty = 'normal'; // 默认难度为普通
        this.speedLevel = 5; // 默认速度等级为5（中等）

        this.letters = [];
        this.bullets = [];
        this.explosions = []; // 添加爆炸效果数组
        this.score = 0;
        this.gameOver = false;

        // 字母生成队列 - 用于控制字母的分散生成
        this.letterQueue = [];
        this.letterGenerationDelay = 600; // 生成字母之间的延迟 (毫秒)
        this.lastLetterGeneration = 0;    // 上次从队列生成字母的时间

        // 难度配置
        this.difficultySettings = {
            easy: {
                initialLetterSpeed: 0.5,            // 更慢的初始速度
                maxLetterSpeed: 1.2,                // 限制最大速度
                initialSpawnRate: 5000,             // 更长的生成间隔
                minSpawnRate: 3000,                 // 更长的最小生成间隔
                speedIncreaseAmount: 0.08,          // 更缓慢的难度提升
                spawnRateDecreaseAmount: 100,       // 更小的生成间隔减少量
                initialLettersPerSpawn: 1,          // 每次只生成1个字母
                maxLettersPerSpawn: 2,              // 最多只会同时生成2个字母
                maxLettersOnScreen: 6,              // 屏幕上最多只有6个字母
                speedIncreaseInterval: 20,          // 每20秒才提高一次难度
                letterGenerationDelay: 800          // 字母生成延迟更长
            },
            normal: {
                initialLetterSpeed: 0.7,            // 中等初始速度
                maxLetterSpeed: 1.8,                // 中等最大速度
                initialSpawnRate: 3500,             // 中等生成间隔
                minSpawnRate: 2000,                 // 中等最小生成间隔
                speedIncreaseAmount: 0.12,          // 中等难度提升速度
                spawnRateDecreaseAmount: 150,       // 中等生成间隔减少量
                initialLettersPerSpawn: 1,          // 初始每次生成1个字母
                maxLettersPerSpawn: 3,              // 最多同时生成3个字母
                maxLettersOnScreen: 10,             // 屏幕上最多10个字母
                speedIncreaseInterval: 15,          // 每15秒提高一次难度
                letterGenerationDelay: 600          // 中等字母生成延迟
            },
            hard: {
                initialLetterSpeed: 1.2,            // 快速初始下落
                maxLetterSpeed: 3.0,                // 高最大速度
                initialSpawnRate: 2000,             // 短生成间隔
                minSpawnRate: 800,                  // 非常短的最小生成间隔
                speedIncreaseAmount: 0.25,          // 快速难度提升
                spawnRateDecreaseAmount: 250,       // 大生成间隔减少量
                initialLettersPerSpawn: 2,          // 初始每次生成2个字母
                maxLettersPerSpawn: 5,              // 最多同时生成5个字母
                maxLettersOnScreen: 20,             // 屏幕上最多20个字母
                speedIncreaseInterval: 8,           // 每8秒就提高一次难度
                letterGenerationDelay: 400          // 字母生成延迟更短
            }
        };

        // 速度等级倍率对照表 (1-10)
        this.speedMultipliers = {
            1: 0.4,  // 极慢
            2: 0.6,
            3: 0.8,
            4: 0.9,
            5: 1.0,  // 中等（默认）
            6: 1.2,
            7: 1.4,
            8: 1.7,
            9: 2.0,
            10: 2.5  // 极快
        };

        // 难度相关参数 - 这些值会根据选择的难度进行设置
        this.initialLetterSpeed = 0.8;
        this.letterSpeed = this.initialLetterSpeed;
        this.maxLetterSpeed = 2.0;
        this.speedIncreaseInterval = 12;
        this.speedIncreaseAmount = 0.15;
        this.lastSpeedIncrease = 0;

        this.initialSpawnRate = 3000;
        this.letterSpawnRate = this.initialSpawnRate;
        this.minSpawnRate = 1800;
        this.spawnRateDecreaseAmount = 180;

        // 字母数量相关参数
        this.initialLettersPerSpawn = 2;
        this.lettersPerSpawn = this.initialLettersPerSpawn;
        this.maxLettersPerSpawn = 4;
        this.maxLettersOnScreen = 12;

        this.lastLetterSpawn = 0;
        this.planeSpeed = 0.4; // 飞机移动速度
        this.bulletSpeed = 15; // 子弹速度

        this.shootSound = document.getElementById('shootSound');
        this.hitSound = document.getElementById('hitSound');
        this.missSound = document.getElementById('missSound');

        this.keys = {};
        this.lastInput = '';
        this.readingHint = '';
        this.setupEventListeners();

        // 准备飞机图像
        this.preparePlaneImage();

        // 应用默认难度设置
        this.applyDifficultySettings('normal');
        // 应用默认速度等级
        this.applySpeedLevel(5);
        this.updateStatusBar();
    }

    /** 画布外展示当前输入与读音，避免遮挡游戏区 */
    updateStatusBar() {
        const inputEl = document.getElementById('inputShow');
        const readingEl = document.getElementById('readingShow');
        if (inputEl) inputEl.textContent = this.lastInput || '—';
        if (readingEl) readingEl.textContent = this.readingHint || '—';
    }

    // 根据选择的难度应用设置
    applyDifficultySettings(difficulty) {
        this.difficulty = difficulty;
        const settings = this.difficultySettings[difficulty];

        // 应用选择的难度设置
        this.initialLetterSpeed = settings.initialLetterSpeed;
        this.letterSpeed = this.initialLetterSpeed;
        this.maxLetterSpeed = settings.maxLetterSpeed;
        this.speedIncreaseInterval = settings.speedIncreaseInterval;
        this.speedIncreaseAmount = settings.speedIncreaseAmount;

        this.initialSpawnRate = settings.initialSpawnRate;
        this.letterSpawnRate = settings.initialSpawnRate;
        this.minSpawnRate = settings.minSpawnRate;
        this.spawnRateDecreaseAmount = settings.spawnRateDecreaseAmount;

        this.initialLettersPerSpawn = settings.initialLettersPerSpawn;
        this.lettersPerSpawn = settings.initialLettersPerSpawn;
        this.maxLettersPerSpawn = settings.maxLettersPerSpawn;
        this.maxLettersOnScreen = settings.maxLettersOnScreen;

        // 设置字母生成延迟
        this.letterGenerationDelay = settings.letterGenerationDelay;

        // 应用当前速度等级的倍率
        this.applySpeedLevel(this.speedLevel);

        console.log(`难度设置为: ${difficulty}`);
    }

    // 应用速度等级
    applySpeedLevel(level) {
        this.speedLevel = parseInt(level, 10);
        const multiplier = this.speedMultipliers[this.speedLevel];

        // 应用速度倍率到相关参数
        this.letterSpeed = this.initialLetterSpeed * multiplier;
        this.maxLetterSpeed = this.difficultySettings[this.difficulty].maxLetterSpeed * multiplier;

        // 调整生成间隔 (反比例关系 - 速度越快，生成间隔越短)
        this.letterSpawnRate = this.initialSpawnRate / multiplier;
        this.minSpawnRate = this.difficultySettings[this.difficulty].minSpawnRate / multiplier;

        // 调整飞机和子弹速度
        this.planeSpeed = 0.4 * (multiplier > 1 ? Math.sqrt(multiplier) : multiplier);
        this.bulletSpeed = 15 * (multiplier > 1 ? Math.sqrt(multiplier) : multiplier);

        console.log(`速度等级设置为: ${this.speedLevel}，倍率: ${multiplier.toFixed(1)}`);
    }

    preparePlaneImage() {
        // 获取内联SVG
        const svgElement = document.getElementById('planeSvg');
        if (svgElement) {
            // 创建SVG字符串
            const svgString = new XMLSerializer().serializeToString(svgElement);
            // 创建图像对象
            this.planeImg = new Image();
            // 创建SVG Blob
            const svgBlob = new Blob([svgString], {type: 'image/svg+xml'});
            // 创建URL
            const url = URL.createObjectURL(svgBlob);
            // 设置图像源
            this.planeImg.src = url;

            console.log('飞机图像已准备');
        } else {
            console.error('找不到飞机SVG元素');
        }
    }

    setupEventListeners() {
        // 键盘事件
        document.addEventListener('keydown', (e) => {
            this.keys[e.key] = true;
            if (e.key.length === 1 && e.key.match(/[a-zA-Z]/i)) {
                this.handleKeyPress(e.key.toLowerCase());
            }
        });

        document.addEventListener('keyup', (e) => {
            this.keys[e.key] = false;
        });

        // 游戏控制按钮
        document.getElementById('startButton').addEventListener('click', () => {
            this.start();
        });

        document.getElementById('restartButton').addEventListener('click', () => {
            this.restart();
        });

        // 大小写切换
        const caseToggle = document.getElementById('caseToggle');
        const caseStatus = document.getElementById('caseStatus');
        caseToggle.addEventListener('change', () => {
            this.useUpperCase = caseToggle.checked;
            caseStatus.textContent = this.useUpperCase ? '大写' : '小写';
        });

        // 难度选择
        const difficultySelect = document.getElementById('difficulty');
        difficultySelect.addEventListener('change', () => {
            this.applyDifficultySettings(difficultySelect.value);
        });

        // 速度等级选择
        const speedLevelSelect = document.getElementById('speedLevel');
        speedLevelSelect.addEventListener('change', () => {
            this.applySpeedLevel(speedLevelSelect.value);
        });

        // 游戏时长设置
        const gameTimeSelect = document.getElementById('gameTime');
        gameTimeSelect.addEventListener('change', () => {
            this.maxGameTime = parseInt(gameTimeSelect.value, 10);
            this.remainingTime = this.maxGameTime;
            document.getElementById('time').textContent = this.remainingTime;
        });

        // 手机虚拟字母键盘，与物理键盘走同一套匹配逻辑
        if (window.MiniFirePad) {
            window.MiniFirePad.mountLetters(document.getElementById('touchPad'), (key) => {
                this.handleKeyPress(key);
            });
        }
    }

    startTimer() {
        // 清除旧的计时器
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
        }

        // 重置时间
        this.remainingTime = this.maxGameTime;
        document.getElementById('time').textContent = this.remainingTime;

        // 重置上次加速时间
        this.lastSpeedIncrease = this.maxGameTime;

        // 设置新的计时器
        this.timerInterval = setInterval(() => {
            this.remainingTime--;
            document.getElementById('time').textContent = this.remainingTime;

            // 检查是否需要增加难度
            if ((this.lastSpeedIncrease - this.remainingTime) >= this.speedIncreaseInterval) {
                this.increaseDifficulty();
                this.lastSpeedIncrease = this.remainingTime;
            }

            if (this.remainingTime <= 0) {
                this.endGame();
            }
        }, 1000);
    }

    // 增加游戏难度
    increaseDifficulty() {
        // 增加字母下落速度，但不超过上限
        if (this.letterSpeed < this.maxLetterSpeed) {
            this.letterSpeed = Math.min(this.letterSpeed + this.speedIncreaseAmount, this.maxLetterSpeed);
            console.log(`难度增加: 字母速度 ${this.letterSpeed.toFixed(1)}`);
        }

        // 减少字母生成间隔，但不低于下限
        if (this.letterSpawnRate > this.minSpawnRate) {
            this.letterSpawnRate = Math.max(this.letterSpawnRate - this.spawnRateDecreaseAmount, this.minSpawnRate);
            console.log(`难度增加: 生成间隔 ${this.letterSpawnRate.toFixed(0)}ms`);
        }

        // 增加每次生成的字母数量，但不超过上限
        if (this.lettersPerSpawn < this.maxLettersPerSpawn) {
            this.lettersPerSpawn = Math.min(this.lettersPerSpawn + 0.5, this.maxLettersPerSpawn);
            console.log(`难度增加: 字母生成数量 ${this.lettersPerSpawn.toFixed(1)}`);
        }
    }

    endGame() {
        this.gameOver = true;

        if (this.score >= 100 && window.MiniCelebrate) {
            window.MiniCelebrate.play({tone: this.score >= 200 ? 'milestone' : 'success', title: '字母小达人！', note: `得到 ${this.score} 分，你太棒啦！`, icon: '🛩️'});
        } else if (window.MiniCelebrate) {
            window.MiniCelebrate.play({tone: 'encourage', title: '继续加油！', note: `这次得到 ${this.score} 分，再挑战一次吧！`, icon: '🌱'});
        }

        // 停止计时器
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
            this.timerInterval = null;
        }
    }

    start() {
        this.gameOver = false;
        this.score = 0;
        this.letters = [];
        this.bullets = [];
        this.explosions = []; // 清空爆炸效果
        this.letterQueue = []; // 清空字母队列
        this.lastInput = '';
        this.readingHint = '';
        this.updateStatusBar();
        document.getElementById('score').textContent = this.score;

        // 获取当前选择的难度
        const difficultySelect = document.getElementById('difficulty');
        this.applyDifficultySettings(difficultySelect.value);

        // 获取当前选择的速度等级
        const speedLevelSelect = document.getElementById('speedLevel');
        this.applySpeedLevel(speedLevelSelect.value);

        // 重置难度参数为初始值
        this.letterSpeed = this.initialLetterSpeed * this.speedMultipliers[this.speedLevel];
        this.letterSpawnRate = this.initialSpawnRate / this.speedMultipliers[this.speedLevel];
        this.lettersPerSpawn = this.initialLettersPerSpawn;

        // 开始计时
        this.startTimer();

        // 开始游戏循环
        this.gameLoop();
    }

    restart() {
        this.start();
    }

    // 处理按键输入
    handleKeyPress(letter) {
        // 如果游戏结束或飞机已经在移动中，忽略输入
        if (this.gameOver || this.plane.isMoving || this.plane.pendingAction) return;

        this.lastInput = letter;
        this.updateStatusBar();

        // 根据大小写设置查找匹配字母
        const targetLetter = this.letters.find(l => {
            if (this.useUpperCase) {
                return l.letter.toUpperCase() === letter.toUpperCase();
            } else {
                return l.letter.toLowerCase() === letter.toLowerCase();
            }
        });

        if (targetLetter) {
            // 设置飞机目标位置
            this.plane.targetX = targetLetter.x + targetLetter.width / 2 - this.plane.width / 2;
            this.plane.isMoving = true;
            this.plane.pendingAction = true;
            this.plane.targetLetter = targetLetter;
        }
    }

    /** 击中后展示并朗读英文字母名 */
    onLetterHit(letterChar) {
        const speech = window.MiniFireSpeech;
        this.readingHint = speech ? speech.letterLabel(letterChar) : String(letterChar).toUpperCase();
        this.updateStatusBar();
        if (speech) speech.speakLetter(letterChar);
    }

    // 发射子弹
    shoot(targetLetter) {
        if (this.shootSound) {
            this.shootSound.currentTime = 0;
            this.shootSound.play();
        }

        // 发射子弹
        this.bullets.push({
            x: this.plane.x + this.plane.width / 2,
            y: this.plane.y,
            letter: targetLetter.letter,
            speed: this.bulletSpeed,
            targetLetter: targetLetter
        });
    }

    // 屏幕与队列中已占用的字母（忽略大小写，避免同屏重复）
    getUsedLetters() {
        const used = {};
        this.letters.forEach(l => {
            used[l.letter.toLowerCase()] = true;
        });
        this.letterQueue.forEach(l => {
            used[l.letter.toLowerCase()] = true;
        });
        return used;
    }

    // 从尚未出现的字母中随机取一个；若已用尽则返回 null
    pickUniqueLetterChar() {
        const alphabet = 'abcdefghijklmnopqrstuvwxyz';
        const used = this.getUsedLetters();
        const pool = [];
        for (let i = 0; i < alphabet.length; i++) {
            const ch = alphabet[i];
            if (!used[ch]) pool.push(ch);
        }
        if (!pool.length) return null;
        let letter = pool[Math.floor(Math.random() * pool.length)];
        if (this.useUpperCase) letter = letter.toUpperCase();
        return letter;
    }

    // 生成一个随机且不重复的字母对象
    createRandomLetter() {
        const letter = this.pickUniqueLetterChar();
        if (!letter) return null;

        // 确保字母位置不会重叠
        const minDistance = 50;
        let x;
        let attempts = 0;
        const maxAttempts = 50;

        do {
            x = Math.random() * (this.canvas.width - 30);
            attempts++;
            if (attempts > maxAttempts) break;
            const overlaps = this.letters.some(existingLetter =>
                Math.abs(existingLetter.x - x) < minDistance
            );
            if (!overlaps) break;
        } while (true);

        return {
            x: x,
            y: -30,
            letter: letter,
            width: 30,
            height: 30
        };
    }

    // 将字母添加到生成队列
    queueLetters() {
        // 如果屏幕上的字母加上队列中的字母已经达到上限，则不再添加
        if (this.letters.length + this.letterQueue.length >= this.maxLettersOnScreen) return;

        // 随机决定这次要生成多少个字母（在1和当前设置的字母数量之间随机）
        const lettersToQueue = Math.floor(Math.random() * this.lettersPerSpawn) + 1;

        // 添加字母到队列
        for (let i = 0; i < lettersToQueue; i++) {
            // 检查是否超出上限
            if (this.letters.length + this.letterQueue.length + 1 > this.maxLettersOnScreen) break;

            // 创建不重复字母；池耗尽则停止本轮入队
            const next = this.createRandomLetter();
            if (!next) break;
            this.letterQueue.push(next);
        }
    }

    // 从队列中生成字母到屏幕
    generateLetterFromQueue() {
        if (this.letterQueue.length > 0 && this.letters.length < this.maxLettersOnScreen) {
            // 从队列取出一个字母并添加到游戏中
            const letter = this.letterQueue.shift();
            this.letters.push(letter);
            this.lastLetterGeneration = Date.now();
        }
    }

    // 创建爆炸效果
    createExplosion(x, y, letter) {
        const particleCount = 15; // 爆炸粒子数量
        const colors = ['#ff8b94', '#ffaaa5', '#ffd3b6', '#dcedc1']; // 爆炸粒子颜色

        const explosion = {
            x: x,
            y: y,
            letter: letter,
            particles: [],
            alpha: 1, // 透明度
            duration: 30, // 爆炸持续的帧数
            frame: 0 // 当前帧
        };

        // 创建爆炸粒子
        for (let i = 0; i < particleCount; i++) {
            const angle = Math.random() * Math.PI * 2; // 随机角度
            const speed = 0.5 + Math.random() * 1.5; // 随机速度
            const size = 3 + Math.random() * 5; // 随机大小
            const color = colors[Math.floor(Math.random() * colors.length)]; // 随机颜色

            explosion.particles.push({
                x: 0,
                y: 0,
                angle: angle,
                speed: speed,
                size: size,
                color: color
            });
        }

        this.explosions.push(explosion);
    }

    update() {
        if (this.gameOver) return;

        // 移动飞机到目标位置
        if (this.plane.isMoving) {
            const dx = this.plane.targetX - this.plane.x;

            if (Math.abs(dx) < 2) { // 更小的阈值，更快到达目标
                this.plane.x = this.plane.targetX;
                this.plane.isMoving = false;

                // 到达目标位置后立即执行射击
                if (this.plane.pendingAction && this.plane.targetLetter) {
                    this.shoot(this.plane.targetLetter);
                    this.plane.pendingAction = false;
                    this.plane.targetLetter = null;
                }
            } else {
                this.plane.x += dx * this.planeSpeed; // 使用更高的速度
                // 根据移动方向设置旋转角度
                this.plane.rotation = Math.sin(Date.now() * 0.01) * 0.1 + (dx > 0 ? 0.1 : -0.1);
            }
        } else {
            this.plane.rotation = Math.sin(Date.now() * 0.01) * 0.05;
        }

        const now = Date.now();

        // 检查是否应该将新的字母添加到队列
        const randomFactor = Math.random() * 500 - 250; // -250到250的随机数
        if (now - this.lastLetterSpawn > this.letterSpawnRate + randomFactor) {
            this.queueLetters();
            this.lastLetterSpawn = now;
        }

        // 从队列中逐个生成字母，保持间隔
        if (now - this.lastLetterGeneration > this.letterGenerationDelay && this.letterQueue.length > 0) {
            this.generateLetterFromQueue();
        }

        // 更新字母位置
        for (let i = this.letters.length - 1; i >= 0; i--) {
            const letter = this.letters[i];
            // 为每个字母添加微小的随机速度变化
            letter.y += this.letterSpeed * (1 + (Math.random() * 0.2 - 0.1)); // ±10%的随机速度变化
            if (letter.y > this.canvas.height) {
                this.letters.splice(i, 1);
                if (this.missSound) {
                    this.missSound.currentTime = 0;
                    this.missSound.play();
                }
            }
        }

        // 更新子弹位置
        for (let i = this.bullets.length - 1; i >= 0; i--) {
            const bullet = this.bullets[i];
            bullet.y -= bullet.speed;

            // 检查子弹是否击中目标字母
            if (bullet.targetLetter &&
                bullet.y <= bullet.targetLetter.y + bullet.targetLetter.height) {
                const letterIndex = this.letters.indexOf(bullet.targetLetter);
                if (letterIndex !== -1) {
                    // 创建爆炸效果
                    this.createExplosion(
                        bullet.targetLetter.x + bullet.targetLetter.width / 2,
                        bullet.targetLetter.y + bullet.targetLetter.height / 2,
                        bullet.targetLetter.letter
                    );

                    const hitChar = bullet.targetLetter.letter;
                    this.letters.splice(letterIndex, 1);
                    this.bullets.splice(i, 1);
                    this.score += 10;
                    document.getElementById('score').textContent = this.score;
                    this.onLetterHit(hitChar);
                    if (this.hitSound) {
                        this.hitSound.currentTime = 0;
                        this.hitSound.play();
                    }
                }
            }

            // 如果子弹超出屏幕，移除它
            if (bullet.y < 0) {
                this.bullets.splice(i, 1);
            }
        }

        // 更新爆炸效果
        for (let i = this.explosions.length - 1; i >= 0; i--) {
            const explosion = this.explosions[i];
            explosion.frame++;

            // 计算透明度
            explosion.alpha = 1 - (explosion.frame / explosion.duration);

            // 如果爆炸效果结束，移除它
            if (explosion.frame >= explosion.duration) {
                this.explosions.splice(i, 1);
            }
        }
    }

    draw() {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        // 绘制飞机
        if (this.planeImg && this.planeImg.complete) {
            this.ctx.save();
            this.ctx.translate(this.plane.x + this.plane.width / 2, this.plane.y + this.plane.height / 2);
            this.ctx.rotate(this.plane.rotation);

            this.ctx.drawImage(
                this.planeImg,
                -this.plane.width / 2,
                -this.plane.height / 2,
                this.plane.width,
                this.plane.height
            );

            this.ctx.restore();
        } else {
            // 如果SVG未加载完成，绘制一个三角形飞机
            this.ctx.save();
            this.ctx.translate(this.plane.x + this.plane.width / 2, this.plane.y + this.plane.height / 2);
            this.ctx.rotate(this.plane.rotation);

            this.ctx.fillStyle = '#FF8B94';
            this.ctx.beginPath();
            this.ctx.moveTo(0, -this.plane.height / 3);
            this.ctx.lineTo(this.plane.width / 3, this.plane.height / 3);
            this.ctx.lineTo(-this.plane.width / 3, this.plane.height / 3);
            this.ctx.closePath();
            this.ctx.fill();

            this.ctx.restore();
        }

        // 绘制字母 - 移除背景
        this.ctx.font = '24px Comic Sans MS';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        this.letters.forEach(letter => {
            // 移除背景填充
            this.ctx.fillStyle = '#ff8b94';
            this.ctx.fillText(letter.letter, letter.x + letter.width / 2, letter.y + letter.height / 2);
        });

        // 绘制子弹
        this.ctx.fillStyle = '#ff8b94';
        this.bullets.forEach(bullet => {
            this.ctx.fillText(bullet.letter, bullet.x, bullet.y);
        });

        // 绘制爆炸效果
        this.explosions.forEach(explosion => {
            explosion.particles.forEach(particle => {
                this.ctx.save();

                // 计算粒子位置
                const x = explosion.x + particle.x;
                const y = explosion.y + particle.y;

                // 更新粒子位置
                particle.x += Math.cos(particle.angle) * particle.speed;
                particle.y += Math.sin(particle.angle) * particle.speed;

                // 设置透明度
                this.ctx.globalAlpha = explosion.alpha;

                // 绘制粒子
                this.ctx.fillStyle = particle.color;
                this.ctx.beginPath();
                this.ctx.arc(x, y, particle.size, 0, Math.PI * 2);
                this.ctx.fill();

                this.ctx.restore();
            });

            // 在爆炸中心绘制字母特效
            if (explosion.frame < 10) {
                this.ctx.save();
                this.ctx.globalAlpha = explosion.alpha * 1.5;
                this.ctx.fillStyle = '#fff';
                this.ctx.font = '32px Comic Sans MS';
                this.ctx.fillText(explosion.letter, explosion.x, explosion.y);
                this.ctx.restore();
            }
        });

        if (this.gameOver) {
            this.ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
            this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
            this.ctx.fillStyle = 'white';
            this.ctx.font = '48px Comic Sans MS';
            this.ctx.fillText('游戏结束!', this.canvas.width / 2, this.canvas.height / 2 - 50);
            this.ctx.font = '36px Comic Sans MS';
            this.ctx.fillText(`最终得分: ${this.score}`, this.canvas.width / 2, this.canvas.height / 2 + 20);
        }
    }

    gameLoop() {
        this.update();
        this.draw();
        if (!this.gameOver) {
            requestAnimationFrame(() => this.gameLoop());
        }
    }
}

// 初始化游戏
window.onload = () => {
    const game = new Game();
};
