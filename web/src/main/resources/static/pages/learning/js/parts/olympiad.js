(function () {
  const { nextTick } = Vue;
  const TOPICS = [
    {id:'pattern',icon:'🔢',name:'找规律',desc:'观察数列变化，发现隐藏规则',method:'观察相邻项的差或交替规律'},
    {id:'sum-diff',icon:'⚖️',name:'和差问题',desc:'根据两个数量的和与差求结果',method:'大数=(和+差)÷2，小数=(和-差)÷2'},
    {id:'sum-times',icon:'📏',name:'和倍差倍',desc:'借助份数关系解决数量问题',method:'先求一份，再按倍数还原'},
    {id:'chicken-rabbit',icon:'🐇',name:'鸡兔同笼',desc:'使用假设法比较数量差异',method:'先全部假设成腿数较少的一类'}
  ];

  function random(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  function patternQuestion(level, index) {
    const start=random(1,12),step=random(2,level+4);
    if(index%2===0){
      const seq=Array.from({length:4},(_,i)=>start+i*step);
      return question(`观察数列：${seq.join('，')}，____。下一个数是多少？`,
        start+4*step,['比较相邻两个数相差多少。',`每次增加 ${step}。`],
        `相邻两项都增加 ${step}，所以 ${seq[3]}+${step}=${start+4*step}。`,'相邻差');
    }
    const first=random(1,6),seq=[first];
    for(let i=1;i<4;i++)seq.push(seq[i-1]+i);
    return question(`观察数列：${seq.join('，')}，____。下一个数是多少？`,
      seq[3]+4,['每次增加的数也可能在变化。','依次增加1、2、3，下一次增加4。'],
      `增加量依次是1、2、3、4，所以答案是 ${seq[3]}+4=${seq[3]+4}。`,'递增规律');
  }

  function sumDiffQuestion(level) {
    const small=random(4,12+level*3),diff=random(2,5+level)*2;
    const large=small+diff,sum=small+large;
    return question(`小明和小华共有 ${sum} 张卡片，小明比小华多 ${diff} 张。小明有多少张？`,
      large,['先把多出的部分拿掉，两个人就一样多。',`小华有（${sum}-${diff}）÷2 张。`],
      `小明有（和+差）÷2=（${sum}+${diff}）÷2=${large} 张。`,'和差公式','张');
  }

  function sumTimesQuestion(level, index) {
    const times=random(2,Math.min(4,level+2)),one=random(3,8+level*2);
    if(index%2===0){
      const total=one*(times+1);
      return question(`故事书和科技书共有 ${total} 本，故事书是科技书的 ${times} 倍。科技书有多少本？`,
        one,[`把科技书看作1份，故事书就是${times}份。`,`总数一共是 ${times+1} 份。`],
        `科技书是1份，共有1+${times}=${times+1}份，所以 ${total}÷${times+1}=${one} 本。`,'和倍问题','本');
    }
    const diff=one*(times-1);
    return question(`哥哥的邮票是弟弟的 ${times} 倍，哥哥比弟弟多 ${diff} 张。弟弟有多少张？`,
      one,[`哥哥比弟弟多 ${times-1} 份。`,`用相差的数量除以相差的份数。`],
      `两人相差 ${times}-1=${times-1} 份，所以弟弟有 ${diff}÷${times-1}=${one} 张。`,'差倍问题','张');
  }

  function chickenRabbitQuestion(level) {
    const rabbits=random(2,4+level),chickens=random(3,7+level);
    const total=rabbits+chickens,legs=rabbits*4+chickens*2;
    return question(`笼中有鸡和兔共 ${total} 只，共有 ${legs} 条腿。兔有多少只？`,
      rabbits,['假设笼中全部是鸡，先算一共有多少条腿。',`全部是鸡时有 ${total*2} 条腿，比较实际多出的腿数。`],
      `假设全是鸡，有 ${total}×2=${total*2} 条腿；实际多 ${legs-total*2} 条，每只兔多2条腿，所以兔有（${legs}-${total*2}）÷2=${rabbits} 只。`,'假设法','只');
  }

  function question(prompt, answer, hints, explanation, method, unit) {
    return {prompt,answer,hints,explanation,method,unit:unit||'',variant:false};
  }

  function buildQuestions(topic, level) {
    const factories={
      pattern:(i)=>patternQuestion(level,i),
      'sum-diff':()=>sumDiffQuestion(level),
      'sum-times':(i)=>sumTimesQuestion(level,i),
      'chicken-rabbit':()=>chickenRabbitQuestion(level)
    };
    return Array.from({length:5},(_,i)=>{
      const item=factories[topic](i);
      item.variant=i>=3;
      return item;
    });
  }

  LearningRegister({
    data: {
      olympiadTopics:TOPICS,olympiadTopic:null,olympiadLevel:1,olympiadQuestions:[],
      olympiadIndex:0,olympiadAnswer:'',olympiadCorrect:0,olympiadHintIndex:0,
      olympiadHintsUsed:0,olympiadResult:null,olympiadStartedAt:null,
      olympiadSaving:false,olympiadFinished:false
    },
    computed: {
      currentOlympiad(){return this.olympiadQuestions[this.olympiadIndex]||{};},
      olympiadProgress(){return this.olympiadQuestions.length?Math.round((this.olympiadIndex+1)/this.olympiadQuestions.length*100):0;}
    },
    methods: {
      selectOlympiadTopic(topic){this.olympiadTopic=topic;this.olympiadQuestions=[];this.olympiadResult=null;this.olympiadFinished=false;window.scrollTo(0,0);},
      async startOlympiad(){
        this.olympiadQuestions=buildQuestions(this.olympiadTopic.id,this.olympiadLevel);
        this.olympiadIndex=0;this.olympiadAnswer='';this.olympiadCorrect=0;
        this.olympiadHintIndex=0;this.olympiadHintsUsed=0;this.olympiadResult=null;
        this.olympiadFinished=false;this.olympiadStartedAt=Date.now();
        await nextTick(()=>this.$refs.olympiadInput&&this.$refs.olympiadInput.focus());
      },
      showOlympiadHint(){
        if(this.olympiadResult)return;
        const count=(this.currentOlympiad.hints||[]).length;
        if(this.olympiadHintIndex<count){this.olympiadHintIndex++;this.olympiadHintsUsed++;}
      },
      async submitOlympiad(){
        if(this.olympiadResult||this.olympiadAnswer==='')return;
        const q=this.currentOlympiad,answer=Number(this.olympiadAnswer),correct=answer===q.answer;
        if(correct)this.olympiadCorrect++;
        this.olympiadResult={correct,text:correct?'答对了！你找到了正确方法。':`答案是 ${q.answer}${q.unit}`};
        if(!correct)await this.saveOlympiadMistake(q,answer);
      },
      async saveOlympiadMistake(q, answer){
        try{
          await this.addMistake({subject:'数学',module:'奥数·'+this.olympiadTopic.name,
            question:q.prompt,userAnswer:String(answer),correctAnswer:String(q.answer)+q.unit,errorType:'方法或计算错误'});
        }catch(error){this.showToast(error.message);}
      },
      async nextOlympiad(){
        if(this.olympiadIndex>=this.olympiadQuestions.length-1){await this.finishOlympiad();return;}
        this.olympiadIndex++;this.olympiadAnswer='';this.olympiadHintIndex=0;this.olympiadResult=null;
        await nextTick(()=>this.$refs.olympiadInput&&this.$refs.olympiadInput.focus());
      },
      async finishOlympiad(){
        if(this.olympiadSaving)return;
        this.olympiadSaving=true;
        const seconds=Math.max(1,Math.round((Date.now()-this.olympiadStartedAt)/1000));
        try{
          await this.saveRecord('数学','奥数·'+this.olympiadTopic.name,5,this.olympiadCorrect,
            {topic:this.olympiadTopic.id,level:this.olympiadLevel,hintsUsed:this.olympiadHintsUsed},seconds);
          await this.loadDashboard();this.olympiadQuestions=[];this.olympiadFinished=true;this.showToast('本次训练已保存');
        }catch(error){this.showToast(error.message);}
        finally{this.olympiadSaving=false;}
      },
      leaveOlympiad(){this.olympiadTopic=null;this.olympiadQuestions=[];this.olympiadResult=null;this.olympiadFinished=false;}
    }
  });
})();
