/**
 * Canvas 渲染：飞机、数字、子弹、爆炸与结算遮罩。
 */
(function (w) {
    function NumberFireRender(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.planeImg = null;
    }

    NumberFireRender.prototype.preparePlane = function (svgId) {
        var svg = document.getElementById(svgId);
        if (!svg) return;
        var svgString = new XMLSerializer().serializeToString(svg);
        var img = new Image();
        img.src = URL.createObjectURL(new Blob([svgString], {type: 'image/svg+xml'}));
        this.planeImg = img;
    };

    NumberFireRender.prototype.clear = function () {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    };

    NumberFireRender.prototype.drawPlane = function (plane) {
        var ctx = this.ctx;
        ctx.save();
        ctx.translate(plane.x + plane.width / 2, plane.y + plane.height / 2);
        ctx.rotate(plane.rotation || 0);
        if (this.planeImg && this.planeImg.complete) {
            ctx.drawImage(this.planeImg, -plane.width / 2, -plane.height / 2, plane.width, plane.height);
        } else {
            ctx.fillStyle = '#4ECDC4';
            ctx.beginPath();
            ctx.moveTo(0, -plane.height / 3);
            ctx.lineTo(plane.width / 3, plane.height / 3);
            ctx.lineTo(-plane.width / 3, plane.height / 3);
            ctx.closePath();
            ctx.fill();
        }
        ctx.restore();
    };

    NumberFireRender.prototype.drawTargets = function (targets) {
        var ctx = this.ctx;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        targets.forEach(function (t) {
            var text = String(t.value);
            var fontSize = text.length >= 4 ? 22 : text.length === 3 ? 26 : 30;
            ctx.font = 'bold ' + fontSize + 'px "Comic Sans MS", "Microsoft YaHei", sans-serif';
            ctx.fillStyle = '#FF6B6B';
            ctx.fillText(text, t.x + t.width / 2, t.y + t.height / 2);
        });
    };

    NumberFireRender.prototype.drawBullets = function (bullets) {
        var ctx = this.ctx;
        ctx.fillStyle = '#4ECDC4';
        ctx.font = '20px "Comic Sans MS", sans-serif';
        ctx.textAlign = 'center';
        bullets.forEach(function (b) {
            ctx.fillText(String(b.value), b.x, b.y);
        });
    };

    NumberFireRender.prototype.drawExplosions = function (explosions) {
        var ctx = this.ctx;
        explosions.forEach(function (ex) {
            ex.particles.forEach(function (p) {
                ctx.save();
                ctx.globalAlpha = ex.alpha;
                ctx.fillStyle = p.color;
                ctx.beginPath();
                ctx.arc(ex.x + p.x, ex.y + p.y, p.size, 0, Math.PI * 2);
                ctx.fill();
                ctx.restore();
                p.x += Math.cos(p.angle) * p.speed;
                p.y += Math.sin(p.angle) * p.speed;
            });
            if (ex.frame < 12) {
                ctx.save();
                ctx.globalAlpha = ex.alpha;
                ctx.fillStyle = '#fff';
                ctx.font = '28px "Comic Sans MS", sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText(String(ex.value), ex.x, ex.y);
                ctx.restore();
            }
        });
    };

    w.NumberFireRender = NumberFireRender;
})(window);
