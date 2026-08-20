(function () {
    var user = MiniGames.requireLogin();
    if (!user) return;
    document.getElementById('userDisplay').textContent = user.nickname;
    document.getElementById('backLink').href = appUrl('/pages/mini/index.html');

    var SIZE = 15;
    var canvas = document.getElementById('board');
    var cells = [];
    var view = GomokuView.create(canvas, function () {
        return cells;
    });
    var turn = 1;
    var finished = false;
    var mode = null; // local | ai | online
    var myColor = 1;
    var aiColor = 2;
    var aiLevel = 2; // 1 入门，2 标准，3 挑战；默认推荐标准
    var sock = null;
    var onlineSide = null;
    var aiWorker = null;
    var aiJob = 0;
    var aiStartedAt = 0;

    function emptyBoard() {
        cells = [];
        for (var y = 0; y < SIZE; y++) {
            cells[y] = [];
            for (var x = 0; x < SIZE; x++) cells[y][x] = 0;
        }
        turn = 1;
        finished = false;
    }

    function setStatus(t) {
        document.getElementById('status').textContent = t;
    }

    function draw() {
        view.draw();
    }

    function countDir(x, y, dx, dy, color) {
        var n = 0, cx = x + dx, cy = y + dy;
        while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE && cells[cy][cx] === color) {
            n++;
            cx += dx;
            cy += dy;
        }
        return n;
    }

    function checkWin(x, y, color) {
        return countDir(x, y, 1, 0, color) + countDir(x, y, -1, 0, color) >= 4
            || countDir(x, y, 0, 1, color) + countDir(x, y, 0, -1, color) >= 4
            || countDir(x, y, 1, 1, color) + countDir(x, y, -1, -1, color) >= 4
            || countDir(x, y, 1, -1, color) + countDir(x, y, -1, 1, color) >= 4;
    }

    function placeLocal(x, y) {
        if (finished || cells[y][x]) return false;
        cells[y][x] = turn;
        if (checkWin(x, y, turn)) {
            finished = true;
            setStatus((turn === 1 ? '黑棋' : '白棋') + '获胜');
            if (mode === 'local' || turn === myColor) {
                MiniCelebrate.play({tone: mode === 'ai' ? 'milestone' : 'success', title: '五子连珠！', note: '你太棒啦！'});
            }
            document.getElementById('resignBtn').disabled = true;
        } else {
            var full = true;
            for (var i = 0; i < SIZE && full; i++)
                for (var j = 0; j < SIZE; j++) if (!cells[i][j]) {
                    full = false;
                    break;
                }
            if (full) {
                finished = true;
                setStatus('和棋');
            } else {
                turn = turn === 1 ? 2 : 1;
                if (mode === 'ai') setStatus(turn === myColor ? '轮到你'
                    : (aiLevel >= 4 ? '大师 AI 深度思考中（最多约 5 秒）…' : '电脑思考中…'));
                else setStatus(turn === 1 ? '黑棋落子' : '白棋落子');
            }
        }
        draw();
        return true;
    }

    function aiMove() {
        if (finished || mode !== 'ai' || turn !== aiColor) return;
        var job = ++aiJob;
        aiStartedAt = Date.now();
        if (aiWorker) {
            aiWorker.postMessage({
                id: job,
                cells: cells,
                aiColor: aiColor,
                myColor: myColor,
                level: aiLevel,
                budgetMs: aiLevel >= 4 ? 4800 : 0
            });
        } else {
            finishAiMove(job, GomokuAi.pickMove(cells, aiColor, myColor, Math.min(aiLevel, 3)));
        }
    }

    function finishAiMove(job, move) {
        if (job !== aiJob || !move || finished || mode !== 'ai' || turn !== aiColor) return;
        placeLocal(move.x, move.y);
        if (finished) stopAiWorker();
    }

    /** 每局使用独立 Worker；重开时终止旧搜索，杜绝过期结果落子。 */
    function restartAiWorker() {
        stopAiWorker();
        if (!window.Worker) return;
        try {
            aiWorker = new Worker('gomoku-ai-worker.js');
            aiWorker.onmessage = function (event) {
                var result = event.data;
                var wait = aiLevel >= 4 ? Math.max(0, 1000 - (Date.now() - aiStartedAt)) : 0;
                setTimeout(function () {
                    finishAiMove(result.id, result.move);
                }, wait);
            };
            aiWorker.onerror = function () {
                var job = aiJob;
                aiWorker.terminate();
                aiWorker = null;
                if (!finished && mode === 'ai' && turn === aiColor) {
                    finishAiMove(job, GomokuAi.pickMove(cells, aiColor, myColor, Math.min(aiLevel, 3)));
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

    function startLocal(vsAi) {
        closeSock();
        stopAiWorker();
        mode = vsAi ? 'ai' : 'local';
        if (vsAi) {
            aiLevel = parseInt(document.getElementById('difficulty').value, 10) || 2;
            document.getElementById('difficultyBox').style.display = 'block';
        } else {
            document.getElementById('difficultyBox').style.display = 'none';
        }
        myColor = 1;
        aiColor = 2;
        onlineSide = null;
        emptyBoard();
        if (vsAi) restartAiWorker();
        draw();
        document.getElementById('resignBtn').disabled = false;
        document.getElementById('cancelBtn').style.display = 'none';
        setStatus(vsAi ? '你执黑，请落子' : '黑棋先手');
    }

    function startOnline() {
        stopAiWorker();
        emptyBoard();
        draw();
        mode = 'online';
        finished = true;
        document.getElementById('resignBtn').disabled = true;
        document.getElementById('cancelBtn').style.display = 'inline-block';
        setStatus('连接中…');
        closeSock();
        sock = new MiniGames.MiniSocket(onSockEvent);
        sock.connect(user.sessionId).then(function () {
            setStatus('匹配中…');
            return sock.send('match', {game: 'gomoku'});
        }).then(function (msg) {
            if (msg.data && msg.data.status === 'queued') setStatus('排队等待对手…');
        }).catch(function (e) {
            setStatus(e.message || '匹配失败');
        });
    }

    function onSockEvent(msg) {
        if (!msg) return;
        if (msg.action === 'matched' && msg.data) {
            onlineSide = msg.data.side;
            myColor = onlineSide === 'black' ? 1 : 2;
            cells = msg.data.board || cells;
            turn = msg.data.turn || 1;
            finished = false;
            document.getElementById('resignBtn').disabled = false;
            document.getElementById('cancelBtn').style.display = 'none';
            setStatus('对阵 ' + (msg.data.opponent || '对手') + ' · 你执' + (myColor === 1 ? '黑' : '白')
                + (turn === myColor ? ' · 轮到你' : ' · 等待对手'));
            draw();
        } else if (msg.action === 'move' && msg.data) {
            cells[msg.data.y][msg.data.x] = msg.data.color;
            turn = msg.data.turn;
            finished = !!msg.data.finished;
            draw();
            if (!finished) setStatus(turn === myColor ? '轮到你' : '等待对手');
        } else if (msg.action === 'gameOver' && msg.data) {
            finished = true;
            document.getElementById('resignBtn').disabled = true;
            var w = msg.data.winner;
            var text = w === 0 ? '和棋' : (w === myColor ? '你赢了' : '你输了');
            setStatus(text + (msg.data.reason ? '（' + msg.data.reason + '）' : ''));
            if (w === myColor) MiniCelebrate.play({tone: 'milestone', title: '你赢啦！', note: '五子连珠，好厉害呀！'});
        } else if (msg.action === 'closed' && mode === 'online' && !finished) {
            setStatus('连接断开');
        }
    }

    function cancelOnline() {
        if (sock) sock.send('cancelMatch', {}).catch(function () {
        });
        closeSock();
        setStatus('已取消匹配');
        document.getElementById('cancelBtn').style.display = 'none';
        mode = null;
    }

    function resignGame() {
        if (finished) return;
        if (mode === 'online' && sock) {
            sock.send('resign', {}).catch(function () {
            });
            return;
        }
        finished = true;
        if (mode === 'ai') stopAiWorker();
        document.getElementById('resignBtn').disabled = true;
        if (mode === 'ai') setStatus('你认输，电脑获胜');
        else setStatus((turn === 1 ? '黑棋' : '白棋') + '认输，对方获胜');
    }

    function resetToMenu() {
        closeSock();
        stopAiWorker();
        mode = null;
        emptyBoard();
        draw();
        setStatus('请选择模式');
        document.getElementById('resignBtn').disabled = true;
        document.getElementById('cancelBtn').style.display = 'none';
        document.getElementById('difficultyBox').style.display = 'block';
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
        var x = point.x, y = point.y;
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) return;

        if (mode === 'online') {
            if (turn !== myColor) return;
            sock.send('move', {x: x, y: y}).catch(function (err) {
                setStatus(err.message || '落子失败');
            });
            return;
        }
        if (mode === 'ai' && turn !== myColor) return;
        if (placeLocal(x, y) && mode === 'ai' && !finished) {
            setTimeout(aiMove, 220);
        }
    });

    emptyBoard();
    view.resize();
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
