package os.aios.agent;
import android.app.*;import android.content.*;import android.os.*;import android.service.notification.*;import org.json.*;
public final class NotificationRepairAcceptance extends Instrumentation {
 Bundle arguments; Context c; int count;JSONArray failures=new JSONArray(),cases=new JSONArray();String channel="notification_repair_20261106",tag="AIOS_REPAIR_20261106";int next=9700;
 interface Work{void run()throws Exception;}
 void check(boolean ok,String detail){count++;if(!ok)throw new AssertionError(detail);}
 void test(String name,Work work){long started=SystemClock.elapsedRealtime();try{work.run();}catch(Throwable e){failures.put(name+": "+e.toString());}Bundle b=new Bundle();b.putString("case",name);b.putLong("duration_ms",SystemClock.elapsedRealtime()-started);b.putString("failures",failures.toString());sendStatus(0,b);}
 public void onCreate(Bundle b){super.onCreate(b);arguments=b;start();}
 JSONObject notification(String title,String text)throws Exception{return new JSONObject().put("package","com.autonavi.minimap").put("channel","COMMON_CHANNEL_ID").put("title",title).put("text",text);}
 JSONObject rule(String id,String title,String action,int importance)throws Exception{return new JSONObject().put("id",tag+id).put("action",action).put("importance",importance).put("when",new JSONObject().put("all",new JSONArray().put(new JSONObject().put("field","channel").put("op","equals").put("value",channel)).put(new JSONObject().put("field","title").put("op","equals").put("value",title))));}
 StatusBarNotification post(String title,String text)throws Exception{return postOn(channel,title,text);}
 StatusBarNotification postOn(String selectedChannel,String title,String text)throws Exception{
  int id=next++;long posted=System.currentTimeMillis();c.getSystemService(NotificationManager.class).notify(tag,id,new Notification.Builder(c,selectedChannel).setWhen(posted).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).build());
  long end=SystemClock.elapsedRealtime()+55000;
  while(SystemClock.elapsedRealtime()<end){if(PhoneNotifications.instance!=null)for(StatusBarNotification n:PhoneNotifications.instance.getActiveNotifications())if(n.getId()==id&&n.getNotification().when==posted&&tag.equals(n.getTag())&&!n.getNotification().extras.getString("android.aios.identity","").isEmpty())return n;Thread.sleep(150);}
  throw new AssertionError("notification not governed: "+title);
 }
 void verify(StatusBarNotification n,boolean filtered,int importance)throws Exception{
  Bundle e=n.getNotification().extras;check(e.getBoolean("android.aios.filtered")==filtered,"filtered "+n.getId());
  if(importance>=0)check(e.getInt("android.aios.importance")==importance,"importance "+n.getId());
  NotificationListenerService.Ranking ranking=new NotificationListenerService.Ranking();check(PhoneNotifications.instance.getCurrentRanking().getRanking(n.getKey(),ranking),"ranking exists");
  JSONObject row=new JSONObject().put("title",String.valueOf(e.getCharSequence("android.title"))).put("filtered",filtered).put("importance",e.getInt("android.aios.importance")).put("native_importance",ranking.getImportance()).put("old",e.getBoolean("android.aios.old")).put("key",n.getKey());cases.put(row);
  if(!filtered&&importance==2)check(ranking.getImportance()>=NotificationManager.IMPORTANCE_HIGH,"LOW source promoted to HIGH");
  if(!filtered&&importance==1)check(ranking.getImportance()==NotificationManager.IMPORTANCE_DEFAULT,"normal promoted to DEFAULT");
  if(!filtered&&importance==0)check(ranking.getImportance()<=NotificationManager.IMPORTANCE_LOW,"explicit low respected");
  if(filtered){
   JSONObject data=PhoneNotifications.notification(n);String event=os.aios.runtime.CoreContracts.stableId("event","notification.posted",data.getString("source_id"),data.getString("source_version"));
   try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(c.getDatabasePath("device_event_outbox.db").toString(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor rows=db.rawQuery("SELECT id FROM receipts WHERE id=? UNION ALL SELECT id FROM pending WHERE id=?",new String[]{event,event})){check(rows.getCount()==0,"filtered notification isolated from automation");}
   try(NotificationLedger l=new NotificationLedger(c)){boolean present=false;long until=SystemClock.elapsedRealtime()+3000;do{JSONArray trash=l.list(true,100,0);for(int i=0;i<trash.length();i++)if(trash.getJSONObject(i).getString("identity").equals(e.getString("android.aios.identity")))present=true;if(!present)Thread.sleep(100);}while(!present&&SystemClock.elapsedRealtime()<until);check(present,"trash applied");}}
 }
 public void onStart(){c=getTargetContext();JSONArray original=null;Bundle out=new Bundle();android.content.SharedPreferences prefs=c.getSharedPreferences("notification_preferences",0);boolean hasFilter=prefs.contains("model_filter"),filter=prefs.getBoolean("model_filter",true),smart=prefs.getBoolean("smart_importance",false);
 try{
  if(arguments.getBoolean("cleanup",false)||"true".equals(arguments.getString("cleanup"))){
   NotificationManager manager=c.getSystemService(NotificationManager.class);
   if(PhoneNotifications.instance!=null)for(StatusBarNotification n:PhoneNotifications.instance.getActiveNotifications())if(tag.equals(n.getTag())&&n.getPackageName().equals(c.getPackageName()))manager.cancel(tag,n.getId());
   manager.deleteNotificationChannel(channel);manager.deleteNotificationChannel(channel+"_user");
   try(NotificationLedger l=new NotificationLedger(c)){l.getWritableDatabase().delete("notices","package=? AND (json_extract(body,'$.channel')=? OR json_extract(body,'$.channel')=?)",new String[]{c.getPackageName(),channel,channel+"_user"});}
   out.putString("cleanup","test notifications, channels and ledger removed");return;
  }
  if("true".equals(arguments.getString("configure_cloud"))){
   JSONObject binding=new JSONObject().put("model","cloud:openai:gpt-6-luna").put("fallback","none");
   SharedModelCatalog.save(c,"notification.filter",binding);SharedModelCatalog.save(c,"notification.importance",binding);
   out.putString("configured",binding.toString());return;
  }
  JSONObject selected=SharedModelCatalog.resolve(c,"notification.filter");check(selected.getString("model").equals("cloud:openai:gpt-6-luna")&&selected.getString("fallback").equals("none"),"test must use explicitly selected cloud classifier");cases.put(new JSONObject().put("model_binding",selected));
  NotificationChannel ch=new NotificationChannel(channel,"Notification repair tests",NotificationManager.IMPORTANCE_LOW);ch.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,Notification.AUDIO_ATTRIBUTES_DEFAULT);ch.enableVibration(true);c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
  if("true".equals(arguments.getString("isolated"))){test("isolated arrived alert",()->verify(post("司机已到达","司机正在上车点等候，请尽快上车。"),false,2));return;}
  prefs.edit().putBoolean("model_filter",true).putBoolean("smart_importance",true).commit();
  try(NotificationLedger l=new NotificationLedger(c)){JSONObject snap=l.rules();original=snap.getJSONArray("items");JSONArray rules=new JSONArray().put(rule("block","REPAIR explicit block","filter",0)).put(rule("allow","REPAIR explicit allow","allow",0)).put(rule("urgent","REPAIR explicit urgent","allow",2)).put(rule("model","REPAIR model rule","model",1).put("instruction","Filter unsolicited promotional discounts and coupons. Keep actual orders.")).put(rule("failallow","REPAIR failure allow","model",1).put("model","invalid").put("on_error","allow")).put(rule("failfilter","REPAIR failure filter","model",1).put("model","invalid").put("on_error","filter"));for(int i=0;i<original.length();i++)rules.put(original.get(i));l.saveRules(rules,snap.getLong("revision"));}
  test("explicit block",()->verify(post("REPAIR explicit block","司机已到达，请前往上车点"),true,0));
  test("explicit allow no model override",()->verify(post("REPAIR explicit allow","限时领取优惠券"),false,0));
  test("explicit urgent",()->verify(post("REPAIR explicit urgent","Local test only"),false,2));
  test("explicit model rule",()->verify(post("REPAIR model rule","领取打车优惠券，限时五折优惠"),true,0));
  test("model failure allow",()->verify(post("REPAIR failure allow","Local test only"),false,1));
  test("model failure filter",()->verify(post("REPAIR failure filter","Local test only"),true,1));
  test("driver arrived LOW promotion",()->verify(post("司机已到达","司机正在上车点等候，请尽快上车。"),false,2));
  test("driver arriving LOW promotion",()->verify(post("司机即将到达提醒","司机将在2分钟内到达，请提前前往上车点乘车。"),false,2));
  test("user-locked silent channel is respected",()->{
   NotificationManager manager=c.getSystemService(NotificationManager.class);String id=channel+"_user";
   manager.createNotificationChannel(new NotificationChannel(id,"Notification user-silence test",NotificationManager.IMPORTANCE_HIGH));
   NotificationChannel locked=manager.getNotificationChannel(id);locked.setImportance(NotificationManager.IMPORTANCE_LOW);
   getUiAutomation().adoptShellPermissionIdentity();
   try{Object service=NotificationManager.class.getDeclaredMethod("getService").invoke(null);Class.forName("android.app.INotificationManager").getMethod("updateNotificationChannelForPackage",String.class,int.class,NotificationChannel.class).invoke(service,c.getPackageName(),android.os.Process.myUid(),locked);}
   finally{getUiAutomation().dropShellPermissionIdentity();}
   StatusBarNotification n=postOn(id,"司机已到达","司机正在上车点等候，请尽快上车。");
   NotificationListenerService.Ranking rank=new NotificationListenerService.Ranking();check(PhoneNotifications.instance.getCurrentRanking().getRanking(n.getKey(),rank),"user channel ranking");check(rank.getImportance()==NotificationManager.IMPORTANCE_LOW,"explicit user silence retained");
   cases.put(new JSONObject().put("user_silent",true).put("native_importance",rank.getImportance()));
  });
  test("default model advertising",()->verify(post("打车优惠限时领取","周末打车领券，最高减50元，立即领取优惠券"),true,0));
  test("default model normal bill",()->verify(post("本次行程已结束","本次行程共37.4元，点击确认账单"),false,1));
  test("default model engagement spam",()->verify(post("邀请打卡","邀请您为地点打卡，参加活动赢取奖励"),true,0));
  test("protected payment",()->verify(post("支付成功","您已支付37.40元，收款方：高德打车"),false,1));
  test("protected parcel",()->verify(post("包裹已到驿站","请凭取件码26-30-58领取您的快递。"),false,1));
  test("protected OTP",()->verify(post("登录验证码","您的登录验证码为123456，5分钟内有效，请勿泄露给他人。"),false,2));
  test("cloud unknown personal",()->verify(post("好友消息","我已经到楼下了，你方便下来吗？"),false,-1));
  test("default cloud failure keeps notification",()->{
   JSONObject binding=SharedModelCatalog.binding(c,"notification.filter");
   try{SharedModelCatalog.save(c,"notification.filter",new JSONObject().put("model","cloud:unavailable:test").put("fallback","none"));
    StatusBarNotification n=post("Uncertain personal update","Can we talk later about the proposal?");check(!n.getNotification().extras.getBoolean("android.aios.filtered"),"cloud failure must allow");
   }finally{SharedModelCatalog.save(c,"notification.filter",binding);}
  });
  for(String[] sample:new String[][]{{"司机已到达","请尽快前往上车点乘车","allow"},{"司机即将到达提醒","司机将在2分钟内到达，请前往上车点","allow"},{"打车优惠限时领取","周末打车领券，立即领取优惠券","filter"},{"本次行程已结束","本次行程共37.4元，点击确认账单","allow"},{"邀请打卡","邀请您为地点打卡，参加活动赢取奖励","filter"},{"好友消息","我已经到楼下了，你方便下来吗？","allow"},{"到账通知","您收到一笔转账，金额128.00元。","allow"},{"登录验证码","您的登录验证码为123456，5分钟内有效，请勿泄露给他人。","allow"},{"安全提醒","您的账号刚刚在新设备登录，如非本人操作请检查账号安全。","allow"},{"最后30分钟","今晚大促即将结束，立即抢购，满300减100！","filter"},{"支付成功","您已支付37.40元，收款方：高德打车","allow"},{"红包到账提醒","88元优惠红包待领取，购物满300可用，点击领取","filter"},{"额度到账","您有50000元借款额度待领取，年利率低至3.6%，立即申请。","filter"},{"包裹已到驿站","请凭取件码26-30-58领取您的快递。","allow"},{"Your driver has arrived","Meet your driver at the pickup point","allow"},{"Payment received","Your account received $128.00","allow"},{"Verification code","Your login code is 654321. Do not share it.","allow"},{"Limited-time offer","Claim your coupon now to save 30%","filter"}})test("raw model "+sample[0],()->{JSONObject d=SharedTextInference.judge(c,"inherit","",notification(sample[0],sample[1]));cases.put(new JSONObject().put("raw_model",sample[0]).put("result",d));check(d.getString("decision").equals(sample[2]),"raw model expected "+sample[2]+", got "+d);});
 }catch(Throwable e){failures.put(android.util.Log.getStackTraceString(e));}
 finally{
  try{if(original!=null)try(NotificationLedger l=new NotificationLedger(c)){JSONObject now=l.rules();JSONArray kept=new JSONArray();for(int i=0;i<now.getJSONArray("items").length();i++){JSONObject rule=now.getJSONArray("items").getJSONObject(i);if(!rule.getString("id").startsWith(tag))kept.put(rule);}l.saveRules(kept,now.getLong("revision"));}}catch(Exception e){failures.put("restore rules: "+e);}
  android.content.SharedPreferences.Editor restore=prefs.edit().putBoolean("smart_importance",smart);if(hasFilter)restore.putBoolean("model_filter",filter);else restore.remove("model_filter");restore.commit();
  out.putInt("checks",count);out.putString("failures",failures.toString());out.putString("cases",cases.toString());finish(failures.length()==0?0:1,out);
 }
 }
}
