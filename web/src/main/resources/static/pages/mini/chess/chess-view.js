(function (w) {
    'use strict';
    function create(canvas, getBoard, getSelected, names) {
        var rows=10, cols=9, px, py, cw, ch, sprites={}, buffer=new CanvasBuffer(canvas);
        function layout(){
            var width=Math.min(450,window.innerWidth-36), height=width*10/9;
            buffer.resize(width,height); px=width*.08; py=height*.06;
            cw=(width-px*2)/(cols-1); ch=(height-py*2)/(rows-1);
            cacheBoard(); cachePieces(); draw();
        }
        function line(ctx,x1,y1,x2,y2){
            ctx.beginPath(); ctx.moveTo(x1,y1); ctx.lineTo(x2,y2); ctx.stroke();
        }
        function cacheBoard(){
            buffer.cacheBackground(function(ctx){
                ctx.fillStyle='#f3d19c'; ctx.fillRect(0,0,canvas.width,canvas.height);
                ctx.strokeStyle='#5c4033'; ctx.lineWidth=1.5;
                for(var r=0;r<rows;r++) line(ctx,px,py+r*ch,px+(cols-1)*cw,py+r*ch);
                for(var c=0;c<cols;c++){
                    line(ctx,px+c*cw,py,px+c*cw,py+4*ch);
                    line(ctx,px+c*cw,py+5*ch,px+c*cw,py+9*ch);
                }
                palace(ctx,0); palace(ctx,7);
                ctx.fillStyle='#8b6914'; ctx.font='12px sans-serif'; ctx.textAlign='center';
                ctx.fillText('楚 河',px+2*cw,py+4.55*ch);
                ctx.fillText('汉 界',px+6*cw,py+4.55*ch);
            });
        }
        function palace(ctx,row){
            line(ctx,px+3*cw,py+row*ch,px+5*cw,py+(row+2)*ch);
            line(ctx,px+5*cw,py+row*ch,px+3*cw,py+(row+2)*ch);
        }
        function cachePieces(){
            var radius=Math.min(cw,ch)*.4, size=Math.ceil(radius*2+4);
            sprites={};
            Object.keys(names).forEach(function(piece){
                sprites[piece]=CanvasBuffer.createSprite(size,function(ctx,width){
                    var center=width/2, red=ChessRules.isRed(piece);
                    ctx.beginPath(); ctx.arc(center,center,radius,0,Math.PI*2);
                    ctx.fillStyle=red?'#fff5e6':'#2a2a2a'; ctx.fill();
                    ctx.strokeStyle=red?'#c41e2a':'#111'; ctx.lineWidth=2; ctx.stroke();
                    ctx.fillStyle=red?'#c41e2a':'#f0f0f0';
                    ctx.font='bold '+Math.floor(radius*1.1)+'px "Songti SC","SimSun",serif';
                    ctx.textAlign='center'; ctx.textBaseline='middle';
                    ctx.fillText(names[piece],center,center+1);
                });
            });
        }
        function center(row,col){ return [px+col*cw,py+row*ch]; }
        function draw(){
            var board=getBoard(), selected=getSelected();
            buffer.render(function(ctx){
                if(selected) drawSelection(ctx,board,selected);
                for(var r=0;r<rows;r++) for(var c=0;c<cols;c++){
                    var piece=board[r][c]; if(piece==='.') continue;
                    var point=center(r,c), sprite=sprites[piece];
                    ctx.drawImage(sprite,point[0]-sprite.width/2,point[1]-sprite.height/2);
                }
            });
        }
        function drawSelection(ctx,board,selected){
            var point=center(selected[0],selected[1]);
            ctx.beginPath(); ctx.arc(point[0],point[1],Math.min(cw,ch)*.42,0,Math.PI*2);
            ctx.strokeStyle='#c9a227'; ctx.lineWidth=3; ctx.stroke();
            ChessRules.legalFrom(board,selected[0],selected[1]).forEach(function(target){
                var targetPoint=center(target[0],target[1]);
                ctx.beginPath(); ctx.arc(targetPoint[0],targetPoint[1],5,0,Math.PI*2);
                ctx.fillStyle='rgba(46,125,50,.55)'; ctx.fill();
            });
        }
        function eventCell(event){
            var rect=canvas.getBoundingClientRect();
            return {
                col:Math.round(((event.clientX-rect.left)*canvas.width/rect.width-px)/cw),
                row:Math.round(((event.clientY-rect.top)*canvas.height/rect.height-py)/ch)
            };
        }
        buffer.watchResize(layout);
        return {layout:layout,draw:draw,eventCell:eventCell};
    }
    w.ChessView={create:create};
})(window);
