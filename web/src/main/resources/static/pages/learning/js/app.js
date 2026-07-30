const { createApp } = Vue;

createApp(LearningMerge({
  data(){return {};},
  computed: {},
  methods: {},
  async mounted(){
    this.heartbeatBusy=false;
    this.onUserActivity=()=>this.bumpIdle();
    ['pointerdown','keydown','touchstart','scroll'].forEach(evt=>window.addEventListener(evt,this.onUserActivity,{passive:true}));
    const params=new URLSearchParams(location.search);
    this.inviteToken=params.get('invite')||'';
    if(this.inviteToken)this.authMode='register';
    try{
      try{
        this.student=await this.api('auth/me');
        this.token='game-session';
        this.afterLogin();
      }catch(_){this.redirectHome();return;}
      await this.loadRegistrationOptions();
    }finally{this.authRestoring=false;}
    window.addEventListener('error',()=>{if(this.student)this.api('auth/frontend-error',{method:'POST'}).catch(()=>{});});
  },
  beforeUnmount(){
    clearInterval(this.heartbeatTimer);
    clearTimeout(this.idleTimer);
    ['pointerdown','keydown','touchstart','scroll'].forEach(evt=>window.removeEventListener(evt,this.onUserActivity));
    this.revokeMediaUrls();
    if(this.englishAudio){this.englishAudio.pause();this.englishAudio=null;}
  }
})).mount('#app');
