(function (w) {
    'use strict';

    function create(canvas, getCells) {
        var size = 15, pad = 24, cell = 0;
        var buffer = new CanvasBuffer(canvas);
        var sprites = {};

        function resize() {
            var width = Math.min(480, window.innerWidth - 40);
            buffer.resize(width, width);
            cell = (width - pad * 2) / (size - 1);
            cacheBoard();
            cacheStones();
            draw();
        }

        function cacheBoard() {
            buffer.cacheBackground(function (ctx) {
                var width = canvas.width;
                ctx.fillStyle = '#deb887';
                ctx.fillRect(0, 0, width, width);
                ctx.strokeStyle = '#5c4033';
                ctx.lineWidth = 1;
                for (var i = 0; i < size; i++) {
                    var point = pad + i * cell;
                    ctx.beginPath();
                    ctx.moveTo(pad, point);
                    ctx.lineTo(pad + (size - 1) * cell, point);
                    ctx.stroke();
                    ctx.beginPath();
                    ctx.moveTo(point, pad);
                    ctx.lineTo(point, pad + (size - 1) * cell);
                    ctx.stroke();
                }
                [[3, 3], [3, 11], [7, 7], [11, 3], [11, 11]].forEach(function (point) {
                    ctx.beginPath();
                    ctx.arc(pad + point[0] * cell, pad + point[1] * cell, 3, 0, Math.PI * 2);
                    ctx.fillStyle = '#5c4033';
                    ctx.fill();
                });
            });
        }

        function cacheStones() {
            var spriteSize = Math.ceil(cell);
            sprites = {};
            [1, 2].forEach(function (color) {
                sprites[color] = CanvasBuffer.createSprite(spriteSize, function (ctx, width) {
                    var center = width / 2;
                    var gradient = ctx.createRadialGradient(
                        center - 4, center - 4, 2, center, center, cell * 0.42);
                    if (color === 1) {
                        gradient.addColorStop(0, '#666');
                        gradient.addColorStop(1, '#111');
                    } else {
                        gradient.addColorStop(0, '#fff');
                        gradient.addColorStop(1, '#ddd');
                    }
                    ctx.beginPath();
                    ctx.arc(center, center, cell * 0.42, 0, Math.PI * 2);
                    ctx.fillStyle = gradient;
                    ctx.fill();
                });
            });
        }

        function draw() {
            var cells = getCells();
            buffer.render(function (ctx) {
                for (var y = 0; y < size; y++) for (var x = 0; x < size; x++) {
                    if (!cells[y] || !cells[y][x]) continue;
                    var sprite = sprites[cells[y][x]];
                    var cx = pad + x * cell, cy = pad + y * cell;
                    ctx.drawImage(sprite, cx - sprite.width / 2, cy - sprite.height / 2);
                }
            });
        }

        function eventCell(event) {
            var rect = canvas.getBoundingClientRect();
            var scale = canvas.width / rect.width;
            return {
                x: Math.round(((event.clientX - rect.left) * scale - pad) / cell),
                y: Math.round(((event.clientY - rect.top) * scale - pad) / cell)
            };
        }

        buffer.watchResize(resize);
        return {resize: resize, draw: draw, eventCell: eventCell};
    }

    w.GomokuView = {create: create};
})(window);
