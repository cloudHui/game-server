(function () {
    var user = MiniGames.requireLogin();
    if (!user) return;
    document.getElementById('userDisplay').textContent = user.nickname;
    document.getElementById('backLink').href = appUrl('/pages/mini/index.html');

    var ROWS = 10, COLS = 9;
    var NAMES = {
        K: '帅', A: '仕', B: '相', N: '马', R: '车', C: '炮', P: '兵',
        k: '将', a: '士', b: '象', n: '马', r: '车', c: '炮', p: '卒'
    };
    var canvas = document.getElementById('board');
    var board = [];
    var redTurn = true;
    var finished = false;
    var mode = null;
    var selected = null;
    var iAmRed = true;
    var sock = null;
    var aiLevel = 2; // 默认标准档，兼顾思考速度和对抗性
    var view = ChessView.create(canvas, function () {
        return board;
    }, function () {
        return selected;
    }, NAMES);
    var aiWorker = null;
    var aiJob = 0;
    var aiStartedAt = 0;

    function initBoard() {
        board = [];
        for (var r = 0; r < ROWS; r++) {
            board[r] = [];
            for (var c = 0; c < COLS; c++) board[r][c] = '.';
        }
        var black = 'rnbakabnr', red = 'RNBAKABNR';
        for (var c = 0; c < COLS; c++) {
            board[0][c] = black[c];
            board[9][c] = red[c];
        }
        board[2][1] = 'c';
        board[2][7] = 'c';
        board[7][1] = 'C';
        board[7][7] = 'C';
        for (var c = 0; c < COLS; c += 2) {
            board[3][c] = 'p';
            board[6][c] = 'P';
        }
        redTurn = true;
        finished = false;
        selected = null;
    }

    function fromString(s) {
        board = [];
        for (var r = 0; r < ROWS; r++) {
            board[r] = [];
            for (var c = 0; c < COLS; c++) board[r][c] = s[r * COLS + c] || '.';
        }
    }

    // 页面与 AI Worker 共用同一份棋规实现。
    function isRed(p) {
        return ChessRules.isRed(p);
    }

    function inBoard(r, c) {
        return ChessRules.inBoard(r, c);
    }

    function tryMove(fr, fc, tr, tc) {
        if (!ChessRules.isMoveLegal(board, fr, fc, tr, tc)) return false;
        var p = board[fr][fc];
        board[tr][tc] = p;
        board[fr][fc] = '.';
        redTurn = !redTurn;
        finished = ChessRules.isCheckmate(board, redTurn);
        return true;
    }

    function aiMove() {
        if (finished || mode !== 'ai' || redTurn) return;
        setStatus(aiLevel >= 4 ? '大师 AI 深度思考中（最多约 8 秒）…' : '电脑思考中…');
        var job = ++aiJob;
        aiStartedAt = Date.now();
        if (aiWorker) {
            aiWorker.postMessage({id: job, board: board, level: aiLevel, budgetMs: aiLevel >= 4 ? 7800 : 0});
        } else {
            finishAiMove(job, ChessAi.pickMove(board, Math.min(aiLevel, 3)));
        }
    }

    function finishAiMove(job, move) {
        if (job !== aiJob || !move || finished || mode !== 'ai' || redTurn) return;
        if (tryMove(move[0], move[1], move[2], move[3])) {
            draw();
            if (finished) setStatus('将死，电脑获胜');
            else setStatus(ChessRules.isInCheck(board, true) ? '将军，请走解将棋步' : '轮到你（红方）');
            if (finished) stopAiWorker();
        }
    }

    /** 每局重建 Worker；切换模式时直接取消尚未完成的旧搜索。 */
    function restartAiWorker() {
        stopAiWorker();
        if (!window.Worker) return;
        try {
            aiWorker = new Worker('chess-ai-worker.js');
            aiWorker.onmessage = function (event) {
                var result = event.data;
                var wait = aiLevel >= 4 ? Math.max(0, 2000 - (Date.now() - aiStartedAt)) : 0;
                setTimeout(function () {
                    finishAiMove(result.id, result.move);
                }, wait);
            };
            aiWorker.onerror = function () {
                var job = aiJob;
                aiWorker.terminate();
                aiWorker = null;
                if (!finished && mode === 'ai' && !redTurn) {
                    finishAiMove(job, ChessAi.pickMove(board, Math.min(aiLevel, 3)));
                }
            };
        } catch (e) {
            aiWorker = null;
        }
    }

    function stopAiWorker() {
        aiJob++;
        if (aiWorker) aiWorker.terminate();
        aiWorker = null;
    }

    function draw() {
        view.draw();
    }

    function setStatus(t) {
        document.getElementById('status').textContent = t;
    }

    function startLocal() {
        closeSock();
        stopAiWorker();
        mode = 'ai';
        iAmRed = true;
        aiLevel = parseInt(document.getElementById('difficulty').value, 10) || 2;
        initBoard();
        restartAiWorker();
        draw();
        document.getElementById('difficultyBox').style.display = 'block';
        document.getElementById('resignBtn').disabled = false;
        document.getElementById('cancelBtn').style.display = 'none';
        setStatus('你执红，请走棋');
    }

    function startOnline() {
        stopAiWorker();
        initBoard();
        draw();
        mode = 'online';
        finished = true;
        document.getElementById('resignBtn').disabled = true;
        document.getElementById('cancelBtn').style.display = 'inline-block';
        document.getElementById('difficultyBox').style.display = 'block';
        setStatus('连接中…');
        closeSock();
        sock = new MiniGames.MiniSocket(onSockEvent);
        sock.connect(user.sessionId).then(function () {
            setStatus('匹配中…');
            return sock.send('match', {game: 'chess'});
        }).then(function (msg) {
            if (msg.data && msg.data.status === 'queued') setStatus('排队等待对手…');
        }).catch(function (e) {
            setStatus(e.message || '失败');
        });
    }

    function onSockEvent(msg) {
        if (!msg) return;
        if (msg.action === 'matched' && msg.data) {
            iAmRed = msg.data.side === 'red';
            fromString(msg.data.board);
            redTurn = !!msg.data.redTurn;
            finished = false;
            selected = null;
            document.getElementById('resignBtn').disabled = false;
            document.getElementById('cancelBtn').style.display = 'none';
            setStatus('对阵 ' + (msg.data.opponent || '对手') + ' · 你执' + (iAmRed ? '红' : '黑')
                + ((redTurn === iAmRed) ? ' · 轮到你' : ' · 等待对手'));
            draw();
        } else if (msg.action === 'move' && msg.data) {
            fromString(msg.data.board);
            redTurn = !!msg.data.redTurn;
            finished = !!msg.data.finished;
            selected = null;
            draw();
            if (!finished) setStatus(redTurn === iAmRed ? '轮到你' : '等待对手');
        } else if (msg.action === 'gameOver' && msg.data) {
            finished = true;
            document.getElementById('resignBtn').disabled = true;
            if (msg.data.board) fromString(msg.data.board);
            draw();
            var win = msg.data.winner;
            var mine = iAmRed ? 'red' : 'black';
            setStatus((win === mine ? '你赢了' : (win ? '你输了' : '和棋')) + (msg.data.reason ? '（' + msg.data.reason + '）' : ''));
            if (win === mine) MiniCelebrate.play({tone: 'milestone', title: '将军获胜！', note: '这一步走得真漂亮！'});
        }
    }

    function cancelOnline() {
        if (sock) sock.send('cancelMatch', {}).catch(function () {
        });
        closeSock();
        mode = null;
        setStatus('已取消匹配');
        document.getElementById('cancelBtn').style.display = 'none';
    }

    function resignGame() {
        if (finished) return;
        if (mode === 'online' && sock) {
            sock.send('resign', {}).catch(function () {
            });
            return;
        }
        finished = true;
        stopAiWorker();
        document.getElementById('resignBtn').disabled = true;
        setStatus('你认输，电脑获胜');
    }

    function resetToMenu() {
        closeSock();
        stopAiWorker();
        mode = null;
        initBoard();
        draw();
        setStatus('请选择模式');
        document.getElementById('resignBtn').disabled = true;
        document.getElementById('cancelBtn').style.display = 'none';
        document.getElementById('difficultyBox').style.display = 'none';
    }

    function closeSock() {
        if (sock) {
            try {
                sock.send('leave', {});
            } catch (e) {
            }
            sock.close();
            sock = null;
        }
    }

    canvas.addEventListener('click', function (e) {
        if (finished || !mode) return;
        var point = view.eventCell(e);
        var c = point.col, r = point.row;
        if (!inBoard(r, c)) return;

        var myTurn = mode === 'online' ? (redTurn === iAmRed) : redTurn;
        if (!myTurn) return;

        if (!selected) {
            var p = board[r][c];
            if (p === '.' || isRed(p) !== (mode === 'online' ? iAmRed : true)) return;
            selected = [r, c];
            draw();
            return;
        }
        if (selected[0] === r && selected[1] === c) {
            selected = null;
            draw();
            return;
        }

        if (mode === 'online') {
            var fr = selected[0], fc = selected[1];
            selected = null;
            if (!ChessRules.isMoveLegal(board, fr, fc, r, c)) {
                setStatus(ChessRules.isInCheck(board, iAmRed) ? '正在被将军，只能走解将棋步' : '走法无效');
                draw();
                return;
            }
            sock.send('move', {fr: fr, fc: fc, tr: r, tc: c}).catch(function (err) {
                setStatus(err.message || '走法无效');
                draw();
            });
            return;
        }
        if (tryMove(selected[0], selected[1], r, c)) {
            selected = null;
            draw();
            if (finished) {
                setStatus('将死，你赢了');
                MiniCelebrate.play({tone: 'milestone', title: '将军获胜！', note: '你太棒啦！'});
            }
            else {
                setStatus(ChessRules.isInCheck(board, false) ? '将军，电脑正在解将…' : '电脑思考中…');
                setTimeout(aiMove, 180);
            }
        } else {
            if (ChessRules.isInCheck(board, true)) setStatus('正在被将军，只能走解将棋步');
            var p = board[r][c];
            if (p !== '.' && isRed(p)) selected = [r, c];
            else selected = null;
            draw();
        }
    });

    initBoard();
    view.layout();
    window.addEventListener('beforeunload', function () {
        stopAiWorker();
        closeSock();
    });
    // Inline handlers run on window; the implementations live in this IIFE.
    window.startLocal = startLocal;
    window.startOnline = startOnline;
    window.cancelOnline = cancelOnline;
    window.resignGame = resignGame;
    window.resetToMenu = resetToMenu;
})();
