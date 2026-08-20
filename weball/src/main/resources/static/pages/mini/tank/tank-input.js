(function (w) {
    'use strict';
    var KEY_BY_DIR = [38,39,40,37];
    function create(pad) {
        var keys = {};
        function clear() { keys[32]=keys[37]=keys[38]=keys[39]=keys[40]=false; }
        function down(event) {
            keys[event.keyCode]=true;
            if([32,37,38,39,40].indexOf(event.keyCode)>=0)event.preventDefault();
        }
        function up(event) { keys[event.keyCode]=false; }
        function pointerDown(event) {
            var button=event.target.closest('button');
            if(!button)return;
            event.preventDefault();
            if(button.dataset.fire){keys[32]=true;return;}
            var direction=parseInt(button.dataset.dir,10);
            clear(); keys[KEY_BY_DIR[direction]]=true;
        }
        w.addEventListener('keydown',down);w.addEventListener('keyup',up);
        pad.addEventListener('pointerdown',pointerDown);pad.addEventListener('pointerup',clear);
        pad.addEventListener('pointercancel',clear);pad.addEventListener('pointerleave',clear);
        function destroy(){clear();w.removeEventListener('keydown',down);w.removeEventListener('keyup',up);pad.removeEventListener('pointerdown',pointerDown);pad.removeEventListener('pointerup',clear);pad.removeEventListener('pointercancel',clear);pad.removeEventListener('pointerleave',clear);}
        w.addEventListener('pagehide',destroy,{once:true});
        return {keys:keys,clear:clear,destroy:destroy};
    }
    w.TankInput={create:create};
})(window);
