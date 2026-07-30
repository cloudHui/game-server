(function () {
  LearningRegister({
    data: {
      loading:false,error:'',toast:'',toastTimer:null,token:'',
      authRestoring:true,
      authMode:'login',displayName:'',confirmPassword:'',inviteToken:'',regOptions:{openRegister:true,idleMinutes:10},
      nameInput:'',passwordInput:'',student:null,view:'home',heartbeatTimer:null,idleTimer:null,idleMs:10*60*1000,
      dashboard:{},statsData:{},showPassword:false,passwordForm:{oldPassword:'',newPassword:'',confirmPassword:''},
      selectedStage:'幼小衔接',stages:['幼小衔接','一年级','二年级','三年级','四年级','五年级','六年级'],
      subjectName:'',subjectItems:[]
    },
    computed: {
      greeting(){const h=new Date().getHours();return h<11?'早上好':h<14?'中午好':h<18?'下午好':'晚上好';},
      currentFeature(){if(this.view==='chinese')return this.chineseMode==='dictation'?'语文听写':'识字手写';if(this.view==='math')return this.mathQuestions.length&&!this.mathFinished?'算术答题':'数学区';if(this.view==='olympiad')return this.olympiadTopic?'奥数·'+this.olympiadTopic.name:'奥数专题';if(this.view==='print')return'题目打印';return'';}
    },
    methods: {
      apiUrl(path){
        const base=(typeof appUrl==='function')?appUrl('/api/learning/'):'/api/learning/';
        return base.replace(/\/$/,'/')+String(path||'').replace(/^\//,'');
      },
      async api(path,options={}){
        const headers={...(options.headers||{})};
        if(options.body&&!(options.body instanceof FormData))headers['Content-Type']='application/json';
        const response=await fetch(this.apiUrl(path),{credentials:'include',...options,headers});
        if(!response.ok){
          let message='请求失败，请稍后再试';
          try{message=(await response.json()).message||message;}catch(_){}
          if(response.status===401){this.redirectHome();throw new Error(message);}
          throw new Error(message);
        }
        if(path!=='auth/heartbeat')this.bumpIdle();
        const type=response.headers.get('content-type')||'';
        return type.includes('json')?response.json():response.text();
      },
      redirectHome(){location.href=(typeof appUrl==='function')?appUrl('/'):'/';},
      async loadRegistrationOptions(){
        try{
          const q=this.inviteToken?('?invite='+encodeURIComponent(this.inviteToken)):'';
          this.regOptions=await this.api('auth/registration'+q);
          this.idleMs=Math.max(1,Number(this.regOptions.idleMinutes||10))*60*1000;
        }catch(_){this.regOptions={openRegister:true,idleMinutes:10};}
      },
      enterStudent(){this.redirectHome();},
      registerStudent(){this.redirectHome();},
      afterLogin(){
        this.selectedStage=this.student.stage||'幼小衔接';this.view='home';
        if(this.visibleLibraryTypes&&this.visibleLibraryTypes.length&&!this.visibleLibraryTypes.some(item=>item.id===this.libraryType))this.libraryType=this.visibleLibraryTypes[0].id;
        if(this.student.mustChangePassword){this.passwordForm={oldPassword:'123456',newPassword:'',confirmPassword:''};this.showPassword=true;}else this.loadDashboard();
        clearInterval(this.heartbeatTimer);this.sendHeartbeat();this.heartbeatTimer=setInterval(()=>this.sendHeartbeat(),30000);
        this.bumpIdle();
      },
      bumpIdle(){if(!this.student)return;clearTimeout(this.idleTimer);this.idleTimer=setTimeout(()=>this.idleLogout(),this.idleMs);},
      idleLogout(){this.clearSession();this.redirectHome();},
      leaveStudent(){this.clearSession();this.redirectHome();},
      clearSession(){clearInterval(this.heartbeatTimer);clearTimeout(this.idleTimer);this.token='';this.student=null;this.authRestoring=false;this.view='home';localStorage.removeItem('learningToken');if(this.revokeMediaUrls)this.revokeMediaUrls();},
      async sendHeartbeat(){if(!this.student||this.heartbeatBusy||(window.AppQuality&&!window.AppQuality.canRequest()))return;this.heartbeatBusy=true;try{await this.api('auth/heartbeat',{method:'POST',body:JSON.stringify({page:this.pageName(),feature:this.currentFeature,device:this.deviceType()})});}catch(_){}finally{this.heartbeatBusy=false;}},
      deviceType(){const ua=navigator.userAgent;return /iPad|Tablet/i.test(ua)?'平板':/Mobile|Android|iPhone/i.test(ua)?'手机':'电脑';},
      pageName(){return {home:'首页',chinese:'语文区',math:'数学区',olympiad:'奥数思维',mistakes:'错题库',records:'学习记录',resources:'资源中心',stats:'学习统计',print:'题目打印',stages:'小学阶段',subject:this.subjectName+'区'}[this.view]||this.view;},
      hasPerm(permission){return this.student&&('ADMIN'===this.student.role||(this.student.permissions||[]).includes(permission));},
      openAdmin(){if(this.student&&this.student.mustChangePassword){this.showToast('请先修改初始密码，再进入管理后台');return;}window.location.href=(typeof appUrl==='function')?appUrl('/pages/admin/admin.html'):'/pages/admin/admin.html';},
      async changePassword(){if(this.passwordForm.newPassword!==this.passwordForm.confirmPassword){this.showToast('两次输入的新密码不一致');return;}try{await this.api('auth/password',{method:'POST',body:JSON.stringify(this.passwordForm)});this.student.mustChangePassword=false;this.showPassword=false;this.passwordForm={oldPassword:'',newPassword:'',confirmPassword:''};await this.loadDashboard();this.showToast('密码已修改');}catch(error){this.showToast(error.message);}},
      async loadDashboard(){try{this.statsData=await this.api('stats');this.dashboard=this.statsData.today||{};}catch(error){this.dashboard={};if(this.hasPerm('STATS'))this.showToast(error.message);}},
      goHome(){this.view='home';this.mathQuestions=[];this.dictationWord=null;this.loadDashboard();window.scrollTo(0,0);this.sendHeartbeat();},
      goBack(){if(this.view==='home'){location.href=(typeof appUrl==='function')?appUrl('/pages/lobby/learning.html'):'../lobby/learning.html';return;}this.goHome();},
      async openView(name){
        this.view=name;window.scrollTo(0,0);
        if(name==='chinese')await this.loadWords();
        if(name==='mistakes')await this.loadMistakes();
        if(name==='records')await this.loadRecords();
        if(name==='resources'){await this.searchLibrary();await this.loadResources();}
        if(name==='stats')await this.loadStats();
        if(name==='print'&&!this.printQuestions.length)await this.generatePrintable();
        this.sendHeartbeat();
      },
      async openSubject(subject){this.subjectName=subject;this.view='subject';try{this.subjectItems=await this.api('content?subject='+encodeURIComponent(subject));}catch(error){this.showToast(error.message);}this.sendHeartbeat();},
      chooseStage(stage){this.selectedStage=stage;this.showToast('已选择'+stage);this.openView('chinese');},
      showToast(message){this.toast=message;clearTimeout(this.toastTimer);this.toastTimer=setTimeout(()=>this.toast='',2200);},
      saveRecord(subject,module,total,correct,details={},durationSeconds=0){return this.api('records',{method:'POST',body:JSON.stringify({subject,module,stage:this.selectedStage,total,correct,durationSeconds,details})});},
      addMistake(item){return this.api('mistakes',{method:'POST',body:JSON.stringify({stage:this.selectedStage,...item})});},
      async loadMistakes(){try{const q=this.mistakeSubject?'?subject='+encodeURIComponent(this.mistakeSubject):'';this.mistakeList=await this.api('mistakes'+q);}catch(error){this.showToast(error.message);}},
      async reviewMistake(item,correct){try{await this.api(`mistakes/${item.id}/review`,{method:'POST',body:JSON.stringify({correct})});this.showToast(correct?'复习成功':'下次继续加油');await this.loadMistakes();await this.loadDashboard();}catch(error){this.showToast(error.message);}},
      statusClass(status){return status==='已掌握'?'status-mastered':status==='待复习'?'status-pending':'status-learning';},
      async loadRecords(){try{this.recordList=await this.api('records');}catch(error){this.showToast(error.message);}},
      async loadStats(){try{this.statsData=await this.api('stats');this.dashboard=this.statsData.today||{};}catch(error){this.showToast(error.message);}},
      formatDate(value){return value?new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'';},
      formatDuration(seconds){return seconds<60?seconds+'秒':seconds<3600?Math.round(seconds/60)+'分钟':(seconds/3600).toFixed(1)+'小时';}
    }
  });
})();
