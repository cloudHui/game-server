(function (w) {
    'use strict';

    function material(piece) {
        return {k:10000,r:500,c:300,n:250,b:150,a:150,p:60}[piece.toLowerCase()] || 0;
    }

    function evaluate(board) {
        var score = 0;
        for (var row = 0; row < 10; row++) for (var col = 0; col < 9; col++) {
            var piece = board[row][col];
            if (piece !== '.') score += w.ChessRules.isRed(piece) ? material(piece) : -material(piece);
        }
        return score;
    }

    function generateMoves(board, forRed) {
        var moves = [];
        for (var row = 0; row < 10; row++) for (var col = 0; col < 9; col++) {
            if (board[row][col] === '.' || w.ChessRules.isRed(board[row][col]) !== forRed) continue;
            w.ChessRules.legalFrom(board,row,col).forEach(function (target) {
                moves.push([row,col,target[0],target[1]]);
            });
        }
        return moves;
    }

    function orderedMoves(board, forRed, limit) {
        var moves = generateMoves(board, forRed);
        moves.sort(function (a, b) {
            var aPiece = board[a[2]][a[3]], bPiece = board[b[2]][b[3]];
            var aValue = aPiece === '.' ? 0 : material(aPiece) + (w.ChessRules.isRed(aPiece) ? 0 : 20);
            var bValue = bPiece === '.' ? 0 : material(bPiece) + (w.ChessRules.isRed(bPiece) ? 0 : 20);
            return bValue - aValue;
        });
        return moves.slice(0, limit);
    }

    var TIMEOUT = { timeout: true };

    function search(board, level, depth, forRed, alpha, beta, deadline) {
        if (deadline && Date.now() >= deadline) throw TIMEOUT;
        if (!depth) return evaluate(board);
        var limit = level === 1 ? 12 : (level === 2 ? 24 : (level === 3 ? 40 : 52));
        var moves = orderedMoves(board, forRed, limit);
        if (!moves.length) return forRed ? -1000000 : 1000000;
        var best = forRed ? -1e15 : 1e15;
        for (var i = 0; i < moves.length; i++) {
            var move = moves[i], piece = board[move[0]][move[1]], captured = board[move[2]][move[3]];
            board[move[2]][move[3]] = piece; board[move[0]][move[1]] = '.';
            var value;
            try {
                value = search(board, level, depth - 1, !forRed, alpha, beta, deadline);
            } finally {
                board[move[0]][move[1]] = piece; board[move[2]][move[3]] = captured;
            }
            if (forRed) { best = Math.max(best, value); alpha = Math.max(alpha, best); }
            else { best = Math.min(best, value); beta = Math.min(beta, best); }
            if (beta <= alpha) break;
        }
        return best;
    }

    /** 保持原有搜索深度和候选数，计算黑方电脑的最佳走法。 */
    function pickMove(board, level) {
        if (level >= 4) return pickMasterMove(board, 7800);
        var depth = level === 1 ? 1 : (level === 2 ? 2 : 3);
        return pickAtDepth(board, level, depth, 0, null);
    }

    function pickAtDepth(board, level, depth, deadline, previous) {
        var limit = level === 1 ? 12 : (level === 2 ? 24 : 40);
        if (level >= 4) limit = 52;
        var moves = orderedMoves(board, false, limit), best = 1e15, pick = previous;
        for (var i = 0; i < moves.length; i++) {
            if (deadline && Date.now() >= deadline) throw TIMEOUT;
            var move = moves[i], piece = board[move[0]][move[1]], captured = board[move[2]][move[3]];
            board[move[2]][move[3]] = piece; board[move[0]][move[1]] = '.';
            var score;
            try {
                score = search(board, level, depth - 1, true, -1e15, 1e15, deadline);
            } finally {
                board[move[0]][move[1]] = piece; board[move[2]][move[3]] = captured;
            }
            if (score < best) { best = score; pick = move; }
        }
        return pick;
    }

    function pickMasterMove(board, budgetMs) {
        var deadline = Date.now() + Math.max(200, budgetMs || 7800);
        var best = orderedMoves(board, false, 1)[0] || null;
        for (var depth = 1; depth <= 7; depth++) {
            try {
                best = pickAtDepth(board, 4, depth, deadline, best);
            } catch (e) {
                if (e !== TIMEOUT) throw e;
                break;
            }
        }
        return best;
    }

    w.ChessAi = { pickMove: pickMove, pickMasterMove: pickMasterMove };
})(typeof self !== 'undefined' ? self : window);
