package os.aios.tests;
import android.app.*;import android.content.*;
/** Local-only notification fixture; performs no communication or user-data operations. */
public final class NotificationFixtureReceiver extends BroadcastReceiver {
 static android.media.session.MediaSession media;
 public void onReceive(Context c,Intent i){
  NotificationManager manager=c.getSystemService(NotificationManager.class);
  if("clear".equals(i.getStringExtra("op"))){manager.cancelAll();if(media!=null){media.release();media=null;}return;}
  if("action".equals(i.getStringExtra("op"))){c.getSharedPreferences("acceptance",0).edit().putBoolean("action_clicked",true).apply();return;}
  String channel="acceptance";NotificationChannel ch=new NotificationChannel(channel,"Acceptance",NotificationManager.IMPORTANCE_HIGH);ch.setSound(null,null);ch.enableVibration(false);manager.createNotificationChannel(ch);
  PendingIntent action=PendingIntent.getBroadcast(c,1,new Intent(c,NotificationFixtureReceiver.class).putExtra("op","action"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  int id=i.getIntExtra("id",1);Notification.Builder b=new Notification.Builder(c,channel).setVisibility(Notification.VISIBILITY_PUBLIC).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(i.getStringExtra("title")==null?"SECOND_APP_FIXTURE":i.getStringExtra("title")).setContentText("Local acceptance notification").setOngoing(i.getBooleanExtra("ongoing",false)).addAction(new Notification.Action.Builder(null,"Test action",action).build());
  String kind=i.getStringExtra("kind");
  if("chat".equals(kind)){
   int seq=i.getIntExtra("seq",1);Person self=new Person.Builder().setName("QA self").build(),sender=new Person.Builder().setName("QA sender").build();
   long time=System.currentTimeMillis();Notification.MessagingStyle style=new Notification.MessagingStyle(self).setConversationTitle("AIOS_QA_CHAT");
   style.addHistoricMessage(new Notification.MessagingStyle.Message("OLD_QA_"+seq,time-60000,sender));
   style.addMessage(new Notification.MessagingStyle.Message("NEW_QA_"+seq,time,sender));b.setStyle(style).setCategory(Notification.CATEGORY_MESSAGE);
  }else if("alarm".equals(kind))b.setCategory(Notification.CATEGORY_ALARM).setGroup("qa-alarm");
  else if("call".equals(kind))b.setCategory(Notification.CATEGORY_CALL).setGroup("qa-call");
  else if("navigation".equals(kind))b.setCategory(Notification.CATEGORY_NAVIGATION).setGroup("qa-navigation");
  else if("media".equals(kind)){
   if(media==null)media=new android.media.session.MediaSession(c,"QA_MEDIA");media.setActive(true);
   media.setMetadata(new android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"QA_MEDIA").putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,"Local fixture").build());
   media.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING,0,1).setActions(android.media.session.PlaybackState.ACTION_PAUSE).build());
   b.setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(0)).setCategory(Notification.CATEGORY_TRANSPORT).setGroup("qa-media");
  }
  manager.notify(id,b.build());
 }
}
