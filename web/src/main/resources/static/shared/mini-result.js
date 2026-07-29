(function(w){'use strict';
  function show(o){
    close(); var box=document.createElement('div'); box.className='mini-result-backdrop';
    box.innerHTML='<section class="mini-result-card" role="dialog" aria-modal="true"><h2>恭喜你！</h2><div class="pattern"></div><div class="time"></div><div class="mini-result-actions"><button class="next">继续挑战</button><button class="secondary close">返回</button></div></section>';
    box.querySelector('.pattern').textContent=o.pattern||'完成本局'; box.querySelector('.time').textContent='太棒了！用时 '+(o.elapsed||'—');
    if(o.title) box.querySelector('h2').textContent=o.title; if(!o.onNext) box.querySelector('.next').style.display='none';
    box.querySelector('.next').onclick=function(){close();o.onNext&&o.onNext()}; box.querySelector('.close').onclick=close; document.body.appendChild(box);
  }
  function close(){var old=document.querySelector('.mini-result-backdrop');if(old)old.remove()}
  w.MiniResult={show:show,close:close};
})(window);
