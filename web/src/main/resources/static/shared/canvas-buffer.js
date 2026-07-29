(function (w) {
    'use strict';

    function CanvasBuffer(canvas) {
        this.canvas = canvas;
        this.visibleContext = canvas.getContext('2d', { alpha: false });
        this.frame = document.createElement('canvas');
        this.frameContext = this.frame.getContext('2d', { alpha: false });
        this.background = document.createElement('canvas');
        this.backgroundContext = this.background.getContext('2d', { alpha: false });
        this.resize(canvas.width, canvas.height);
    }

    /** 三层画布保持同尺寸；背景层和帧层始终不直接显示。 */
    CanvasBuffer.prototype.resize = function (width, height) {
        this.canvas.width = width;
        this.canvas.height = height;
        this.frame.width = width;
        this.frame.height = height;
        this.background.width = width;
        this.background.height = height;
    };

    CanvasBuffer.prototype.cacheBackground = function (paint) {
        paint(this.backgroundContext);
    };

    /** 离屏合成完整帧后一次贴到可见画布，避免暴露清屏过程。 */
    CanvasBuffer.prototype.render = function (paint) {
        this.frameContext.drawImage(this.background, 0, 0);
        paint(this.frameContext);
        this.visibleContext.drawImage(this.frame, 0, 0);
    };

    /** 同一动画帧内只响应一次 resize，避免移动端连续触发布局重绘。 */
    CanvasBuffer.prototype.watchResize = function (resize) {
        var pending = false;
        window.addEventListener('resize', function () {
            if (pending) return;
            pending = true;
            window.requestAnimationFrame(function () {
                pending = false;
                resize();
            });
        });
    };

    /** 创建带透明通道的预渲染图块，供棋子等重复图形复用。 */
    CanvasBuffer.createSprite = function (size, paint) {
        var sprite = document.createElement('canvas');
        sprite.width = size;
        sprite.height = size;
        paint(sprite.getContext('2d'), size);
        return sprite;
    };

    w.CanvasBuffer = CanvasBuffer;
})(window);
