(function () {
  const { nextTick } = Vue;
  LearningRegister({
    data: {
      chineseMode:'learn',words:[],selectedWord:null,showGuide:true,dictationWord:null,dictationRevealed:false,canvasDrawing:false,canvasLast:null,
      mistakeList:[],mistakeSubject:''
    },
    methods: {
      async loadWords(){try{this.words=await this.api('words?stage='+encodeURIComponent(this.selectedStage));if(!this.words.length&&this.selectedStage!=='幼小衔接'){this.showToast(this.selectedStage+'暂无字库，先展示幼小衔接');this.words=await this.api('words?stage='+encodeURIComponent('幼小衔接'));}this.selectedWord=this.words[0]||null;if(this.chineseMode==='learn')await nextTick(()=>this.initCanvas());}catch(error){this.showToast(error.message);}},
      async selectWord(word){this.selectedWord=word;await nextTick(()=>this.initCanvas());},
      async setChineseMode(mode){this.chineseMode=mode;this.dictationWord=null;this.dictationRevealed=false;if(mode==='learn')await nextTick(()=>this.initCanvas());this.sendHeartbeat();},
      speak(text){if(!('speechSynthesis'in window)){this.showToast('当前浏览器不支持语音播报');return;}speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.lang='zh-CN';u.rate=.72;speechSynthesis.speak(u);},
      initCanvas(){const canvas=this.$refs.writingCanvas;if(!canvas)return;const ctx=canvas.getContext('2d');ctx.lineCap='round';ctx.lineJoin='round';ctx.strokeStyle='#29263b';ctx.lineWidth=14;const point=e=>{const r=canvas.getBoundingClientRect();return{x:(e.clientX-r.left)*canvas.width/r.width,y:(e.clientY-r.top)*canvas.height/r.height};};canvas.onpointerdown=e=>{e.preventDefault();canvas.setPointerCapture(e.pointerId);this.canvasDrawing=true;this.canvasLast=point(e);ctx.beginPath();ctx.arc(this.canvasLast.x,this.canvasLast.y,ctx.lineWidth/2,0,Math.PI*2);ctx.fillStyle=ctx.strokeStyle;ctx.fill();};canvas.onpointermove=e=>{if(!this.canvasDrawing)return;e.preventDefault();const p=point(e);ctx.beginPath();ctx.moveTo(this.canvasLast.x,this.canvasLast.y);ctx.lineTo(p.x,p.y);ctx.stroke();this.canvasLast=p;};canvas.onpointerup=canvas.onpointercancel=()=>{this.canvasDrawing=false;this.canvasLast=null;};},
      clearCanvas(){const c=this.$refs.writingCanvas;if(c)c.getContext('2d').clearRect(0,0,c.width,c.height);},
      async recordWord(correct){if(!this.selectedWord)return;try{await this.saveRecord('语文','认字',1,correct?1:0,{wordId:this.selectedWord.id,character:this.selectedWord.character});if(!correct)await this.addMistake({subject:'语文',module:'认字',question:'认读汉字：'+this.selectedWord.character,userAnswer:'不认识',correctAnswer:this.selectedWord.pinyin+'；'+this.selectedWord.words,errorType:'不认识'});this.showToast(correct?'真棒！已经记住了':'已放进复习本');const i=this.words.findIndex(w=>w.id===this.selectedWord.id);if(i<this.words.length-1)this.selectWord(this.words[i+1]);}catch(error){this.showToast(error.message);}},
      async startDictation(){if(!this.words.length)await this.loadWords();this.dictationWord=this.words[Math.floor(Math.random()*this.words.length)];this.dictationRevealed=false;await nextTick(()=>this.initCanvas());this.speak(this.dictationWord.character);},
      async finishDictation(correct){const word=this.dictationWord;try{await this.saveRecord('语文','听写',1,correct?1:0,{wordId:word.id,character:word.character});if(!correct)await this.addMistake({subject:'语文',module:'听写',question:'听写：'+word.pinyin,userAnswer:'书写错误',correctAnswer:word.character,errorType:'书写错误'});this.showToast(correct?'听写正确！':'已加入错题库');this.dictationWord=null;setTimeout(()=>this.startDictation(),300);}catch(error){this.showToast(error.message);}}
    }
  });
})();
