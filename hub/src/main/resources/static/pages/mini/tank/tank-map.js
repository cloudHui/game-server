(function (w) {
    'use strict';
    function setCells(map, cells, value) {
        cells.forEach(function (p) { map[p[0]][p[1]] = value; });
    }
    function create(level, rows, cols) {
        var map = [];
        for (var row = 0; row < rows; row++) {
            map[row] = [];
            for (var col = 0; col < cols; col++) {
                var edge = row === 0 || col === 0 || row === rows-1 || col === cols-1;
                var brick = !edge && ((row+col+level)%5 === 0 || (row*3+col)%11 === 0)
                    && !(row > rows-4 && col > 4 && col < 8);
                map[row][col] = edge ? 2 : (brick ? 1 : 0);
            }
        }
        [[rows-2,6],[1,2],[1,cols-3],[1,6]].forEach(function (p) {
            map[p[0]][p[1]] = map[p[0]][p[1]-1] = map[p[0]][p[1]+1] = 0;
        });
        setCells(map, [[4,3],[4,4],[8,8],[8,9]], 3);
        setCells(map, [[3,8],[3,9],[9,3],[9,4]], 4);
        setCells(map, [[6,2],[6,3],[6,9],[6,10]], 6);
        setCells(map, [[11,5],[10,5],[10,6],[10,7],[11,7]], 1);
        // 保留一条横向和一条纵向战术主干，随机砖块不会生成无解封闭区。
        for (var c=1;c<cols-1;c++) map[2][c]=0;
        for (var r=1;r<rows-3;r++) map[r][6]=0;
        return map;
    }
    function hasRoute(map, start, goals) {
        var rows=map.length,cols=map[0].length,queue=[start],head=0,seen={};
        seen[start[0]+','+start[1]]=true;
        while(head<queue.length){var p=queue[head++];for(var i=0;i<goals.length;i++)if(p[0]===goals[i][0]&&p[1]===goals[i][1])return true;
            [[1,0],[-1,0],[0,1],[0,-1]].forEach(function(d){var nr=p[0]+d[0],nc=p[1]+d[1],key=nr+','+nc;
                if(nr>=0&&nc>=0&&nr<rows&&nc<cols&&!seen[key]&&(map[nr][nc]===0||map[nr][nc]===4||map[nr][nc]===6)){seen[key]=true;queue.push([nr,nc]);}});}
        return false;
    }
    w.TankMap = {create:create,hasRoute:hasRoute};
})(window);
