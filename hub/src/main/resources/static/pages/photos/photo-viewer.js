(function () {
    'use strict';
    window.PhotoViewer = function (options) {
        var viewer = options.viewer, stage = options.stage, image = options.image;
        var view = { scale: 1, x: 0, y: 0 }, pointers = new Map(), gesture, objectUrl, request = 0, lastTap = 0;
        function clamp() {
            if (!image.naturalWidth) return;
            var maxX = Math.max(0, (image.clientWidth * view.scale - stage.clientWidth) / 2);
            var maxY = Math.max(0, (image.clientHeight * view.scale - stage.clientHeight) / 2);
            view.x = Math.max(-maxX, Math.min(maxX, view.x));
            view.y = Math.max(-maxY, Math.min(maxY, view.y));
        }
        function render(shouldClamp) {
            if (shouldClamp) clamp();
            image.style.transform = 'translate3d(' + view.x + 'px,' + view.y + 'px,0) scale(' + view.scale + ')';
        }
        function reset() { view = { scale: 1, x: 0, y: 0 }; render(); }
        function distance(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }
        function midpoint(a, b) { return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; }
        function begin() {
            var p = Array.from(pointers.values());
            if (p.length === 1) gesture = { type: 'drag', px: p[0].x, py: p[0].y, x: view.x, y: view.y };
            else if (p.length >= 2) { var m = midpoint(p[0], p[1]); gesture = { type: 'pinch', distance: distance(p[0], p[1]), scale: view.scale, x: view.x, y: view.y, mx: m.x, my: m.y }; }
        }
        function zoomAt(clientX, clientY) {
            var rect = stage.getBoundingClientRect(), next = view.scale > 1 ? 1 : 2.5;
            if (next === 1) return reset();
            view.x = (rect.width / 2 - (clientX - rect.left)) * (next - 1);
            view.y = (rect.height / 2 - (clientY - rect.top)) * (next - 1);
            view.scale = next; render(true);
        }
        stage.onpointerdown = function (e) { e.preventDefault(); pointers.set(e.pointerId, { x: e.clientX, y: e.clientY }); stage.setPointerCapture(e.pointerId); stage.classList.add('is-gesturing'); begin(); };
        stage.onpointermove = function (e) {
            if (!pointers.has(e.pointerId)) return;
            e.preventDefault(); pointers.set(e.pointerId, { x: e.clientX, y: e.clientY }); var p = Array.from(pointers.values());
            if (p.length === 1 && gesture && gesture.type === 'drag') { view.x = gesture.x + p[0].x - gesture.px; view.y = gesture.y + p[0].y - gesture.py; render(true); }
            else if (p.length >= 2 && gesture && gesture.type === 'pinch') { var m = midpoint(p[0], p[1]), next = Math.max(1, Math.min(5, gesture.scale * distance(p[0], p[1]) / Math.max(1, gesture.distance))), ratio = next / gesture.scale; view.scale = next; view.x = gesture.x * ratio + m.x - gesture.mx; view.y = gesture.y * ratio + m.y - gesture.my; render(true); }
        };
        function end(e) { pointers.delete(e.pointerId); if (pointers.size) begin(); else { stage.classList.remove('is-gesturing'); gesture = null; render(true); } }
        stage.onpointerup = end; stage.onpointercancel = end;
        stage.ondblclick = function (e) { e.preventDefault(); zoomAt(e.clientX, e.clientY); };
        stage.ontouchend = function (e) { if (e.changedTouches.length !== 1) return; var now = Date.now(); if (now - lastTap < 300) { var t = e.changedTouches[0]; zoomAt(t.clientX, t.clientY); lastTap = 0; } else lastTap = now; };
        options.closeButton.onclick = close;
        window.addEventListener('resize', function () { if (!viewer.hidden) render(true); });
        function close() { request++; viewer.hidden = true; document.body.style.overflow = ''; pointers.clear(); if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = null; image.removeAttribute('src'); }
        return {
            open: function (thumbnailUrl, originalUrl, headers) {
                var current = ++request; reset(); pointers.clear(); if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = null;
                viewer.hidden = false; document.body.style.overflow = 'hidden'; image.src = thumbnailUrl;
                fetch(originalUrl, { headers: headers }).then(function (r) { if (!r.ok) throw Error(); return r.blob(); }).then(function (blob) { if (current !== request || viewer.hidden) return; objectUrl = URL.createObjectURL(blob); image.src = objectUrl; }).catch(function () {});
            },
            close: close
        };
    };
})();
