(function (w) {
    'use strict';

    var ROWS = 10, COLS = 9;

    function isRed(piece) {
        return piece >= 'A' && piece <= 'Z';
    }

    function inBoard(row, col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }

    function inPalace(row, col, red) {
        return col >= 3 && col <= 5 && (red ? row >= 7 && row <= 9 : row >= 0 && row <= 2);
    }

    function countBlocks(board, fromRow, fromCol, toRow, toCol) {
        var dr = Math.sign(toRow - fromRow), dc = Math.sign(toCol - fromCol);
        var n = 0, row = fromRow + dr, col = fromCol + dc;
        while (row !== toRow || col !== toCol) {
            if (board[row][col] !== '.') n++;
            row += dr;
            col += dc;
        }
        return n;
    }

    function faceToFace(board) {
        var redRow = -1, redCol = -1, blackRow = -1, blackCol = -1;
        for (var row = 0; row < ROWS; row++) for (var col = 0; col < COLS; col++) {
            if (board[row][col] === 'K') {
                redRow = row;
                redCol = col;
            }
            if (board[row][col] === 'k') {
                blackRow = row;
                blackCol = col;
            }
        }
        if (redRow < 0 || blackRow < 0 || redCol !== blackCol) return false;
        return countBlocks(board, redRow, redCol, blackRow, blackCol) === 0;
    }

    function isLegal(board, fromRow, fromCol, toRow, toCol, allowKingTarget) {
        if (!inBoard(fromRow, fromCol) || !inBoard(toRow, toCol)) return false;
        var piece = board[fromRow][fromCol], target = board[toRow][toCol];
        if (piece === '.') return false;
        if (target !== '.' && isRed(target) === isRed(piece)) return false;
        if (!allowKingTarget && target.toLowerCase() === 'k') return false;
        var dr = toRow - fromRow, dc = toCol - fromCol, type = piece.toLowerCase();
        if (type === 'k') return inPalace(toRow, toCol, isRed(piece)) && Math.abs(dr) + Math.abs(dc) === 1;
        if (type === 'a') return inPalace(toRow, toCol, isRed(piece)) && Math.abs(dr) === 1 && Math.abs(dc) === 1;
        if (type === 'b') return Math.abs(dr) === 2 && Math.abs(dc) === 2
            && board[fromRow + dr / 2][fromCol + dc / 2] === '.' && (isRed(piece) ? toRow >= 5 : toRow <= 4);
        if (type === 'n') {
            if (!((Math.abs(dr) === 2 && Math.abs(dc) === 1)
                || (Math.abs(dr) === 1 && Math.abs(dc) === 2))) return false;
            var blockRow = Math.abs(dr) === 2 ? fromRow + dr / 2 : fromRow;
            var blockCol = Math.abs(dc) === 2 ? fromCol + dc / 2 : fromCol;
            return board[blockRow][blockCol] === '.';
        }
        if (type === 'r') return (dr === 0 || dc === 0)
            && countBlocks(board, fromRow, fromCol, toRow, toCol) === 0;
        if (type === 'c') {
            if (dr !== 0 && dc !== 0) return false;
            var blocks = countBlocks(board, fromRow, fromCol, toRow, toCol);
            return target === '.' ? blocks === 0 : blocks === 1;
        }
        if (type === 'p') {
            if (isRed(piece)) {
                if (fromRow >= 5) return dr === -1 && dc === 0;
                return (dr === -1 && dc === 0) || (dr === 0 && Math.abs(dc) === 1);
            }
            if (fromRow <= 4) return dr === 1 && dc === 0;
            return (dr === 1 && dc === 0) || (dr === 0 && Math.abs(dc) === 1);
        }
        return false;
    }

    function legalFrom(board, row, col) {
        var list = [];
        for (var toRow = 0; toRow < ROWS; toRow++) for (var toCol = 0; toCol < COLS; toCol++) {
            if (isMoveLegal(board, row, col, toRow, toCol)) list.push([toRow, toCol]);
        }
        return list;
    }

    function isInCheck(board, red) {
        var king = red ? 'K' : 'k', kingRow = -1, kingCol = -1;
        for (var row = 0; row < ROWS; row++) for (var col = 0; col < COLS; col++) {
            if (board[row][col] === king) {
                kingRow = row;
                kingCol = col;
            }
        }
        if (kingRow < 0) return true;
        for (var fromRow = 0; fromRow < ROWS; fromRow++) for (var fromCol = 0; fromCol < COLS; fromCol++) {
            var attacker = board[fromRow][fromCol];
            if (attacker !== '.' && isRed(attacker) !== red
                && isLegal(board, fromRow, fromCol, kingRow, kingCol, true)) return true;
        }
        return false;
    }

    function isMoveLegal(board, fromRow, fromCol, toRow, toCol) {
        if (!isLegal(board, fromRow, fromCol, toRow, toCol, false)) return false;
        var piece = board[fromRow][fromCol], captured = board[toRow][toCol];
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = '.';
        var invalid = faceToFace(board) || isInCheck(board, isRed(piece));
        board[fromRow][fromCol] = piece;
        board[toRow][toCol] = captured;
        return !invalid;
    }

    function isCheckmate(board, red) {
        if (!isInCheck(board, red)) return false;
        for (var row = 0; row < ROWS; row++) for (var col = 0; col < COLS; col++) {
            if (board[row][col] !== '.' && isRed(board[row][col]) === red
                && legalFrom(board, row, col).length) return false;
        }
        return true;
    }

    w.ChessRules = {
        isRed: isRed,
        inBoard: inBoard,
        countBlocks: countBlocks,
        faceToFace: faceToFace,
        isInCheck: isInCheck,
        isLegal: isLegal,
        isMoveLegal: isMoveLegal,
        legalFrom: legalFrom,
        isCheckmate: isCheckmate
    };
})(typeof self !== 'undefined' ? self : window);
