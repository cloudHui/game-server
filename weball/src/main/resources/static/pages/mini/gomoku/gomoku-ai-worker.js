importScripts('gomoku-ai.js');

/** Worker 只传递棋盘快照和落点，避免搜索阻塞页面绘制。 */
self.onmessage = function (event) {
    var data = event.data;
    var move = data.level >= 4
        ? self.GomokuAi.pickMasterMove(data.cells, data.aiColor, data.myColor, data.budgetMs || 4800)
        : self.GomokuAi.pickMove(data.cells, data.aiColor, data.myColor, data.level);
    self.postMessage({id: data.id, move: move});
};
