package os.aios.agent;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.widget.*;import org.json.*;import java.util.*;import os.aios.core.*;import os.aios.workspace.WorkspaceStore;
/** Explicit share action with durable checkpoints and idempotent note/assistant submission. */
public final class ShareToAssistantActivity extends Activity {
 private static final Set<String> running=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private TextView status,target,send;private LinearLayout targetRow;private EditText text;private String job;private JSONObject state;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private android.content.SharedPreferences jobs(){return getSharedPreferences("share_jobs",0);}
 private void save()throws Exception{if(!jobs().edit().putString(job,state.toString()).commit())throw new java.io.IOException(getString(R.string.assistant_could_not_save_attachment_efe23));}
 private final Runnable refresh=new Runnable(){public void run(){if(isDestroyed()||isFinishing())return;try{JSONObject snapshot=new JSONObject(jobs().getString(job,"{}"));boolean busy=running.contains(job);send.setEnabled(!busy);send.setAlpha(busy?0.38f:1f);text.setEnabled(!busy);targetRow.setEnabled(!busy);String phase=snapshot.optString("phase");if(phase.equals("queued")){Toast.makeText(ShareToAssistantActivity.this,getString(R.string.share_saved),Toast.LENGTH_LONG).show();startForegroundService(new Intent(ShareToAssistantActivity.this,AssistantOrbService.class).setAction("show").putExtra("expand",true));finish();return;}if(phase.equals("failed"))status.setText(getString(R.string.share_failed,snapshot.optString("error")));}catch(Exception e){android.util.Log.w("AIOSShare","Restore",e);}ui.postDelayed(this,500);}};
 @Override public void onCreate(Bundle saved){super.onCreate(saved);try{
  if(saved!=null&&jobs().contains(saved.getString("job",""))){job=saved.getString("job");state=new JSONObject(jobs().getString(job,"{}"));}
  else{
   Intent intent=getIntent();if(!Set.of(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE).contains(intent.getAction()))throw new IllegalArgumentException(getString(R.string.share_empty));
   CharSequence raw=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);String body=raw==null?"":raw.toString();if(body.length()>100000)throw new IllegalArgumentException(getString(R.string.share_limit));
   ArrayList<Uri> sources=new ArrayList<>();if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){ArrayList<Uri> list=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM,Uri.class);if(list!=null)sources.addAll(list);}else{Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);if(uri!=null)sources.add(uri);}
   ClipData clip=intent.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++){Uri uri=clip.getItemAt(i).getUri();if(uri!=null&&!sources.contains(uri))sources.add(uri);}
   if(sources.size()>16)throw new IllegalArgumentException(getString(R.string.share_limit));JSONArray uris=new JSONArray();int sourceUid=getLaunchedFromUid();
   for(Uri uri:sources){if(!"content".equals(uri.getScheme())||sourceUid<0||checkContentUriPermissionFull(uri,-1,sourceUid,Intent.FLAG_GRANT_READ_URI_PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException(getString(R.string.assistant_could_not_read_attachment_429db));uris.put(uri.toString());}
   if(body.isBlank()&&sources.isEmpty())throw new IllegalArgumentException(getString(R.string.share_empty));
   job=UUID.randomUUID().toString();state=new JSONObject().put("body",body).put("sources",uris).put("phase","draft");save();
  }
  LinearLayout root=AiosUi.column(this);int pad=AiosUi.dp(this,16);root.addView(AiosUi.toolbar(this,getString(R.string.share_title),this::finish));
  LinearLayout card=AiosUi.card(this);root.addView(card);
  if(!state.optString("body").isBlank()){TextView preview=AiosUi.selectable(this,state.optString("body"),16,AiosTheme.text(this));preview.setMaxLines(6);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(preview);AiosUi.gap(card,6);}
  card.addView(AiosUi.secondary(this,getString(R.string.share_files,state.getJSONArray("sources").length())));
  LinearLayout form=AiosUi.column(this);form.setPadding(pad,0,pad,0);root.addView(form);
  text=AiosUi.area(this,getString(R.string.share_instruction),state.optString("instruction"),3);text.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10000)});form.addView(text);
  LinearLayout group=AiosUi.group(this);root.addView(group);
  targetRow=AiosUi.settingRow(this,"book",getString(R.string.share_target),ShareNotes.label(this),null,true,true,()->ShareNotes.choose(this,()->target.setText(ShareNotes.label(this))));group.addView(targetRow);target=ControlActivity.summaryOf(targetRow);
  status=AiosUi.secondary(this,getString(R.string.share_ready));status.setPadding(AiosUi.dp(this,32),0,AiosUi.dp(this,32),AiosUi.dp(this,8));root.addView(status);
  send=AiosUi.filledButton(this,getString(R.string.share_send),this::submit);LinearLayout actions=AiosUi.actions(this,send);actions.setPadding(pad,AiosUi.dp(this,8),pad,AiosUi.dp(this,24));root.addView(actions);
  ScrollView scroll=new ScrollView(this);scroll.addView(root);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
  AiosUi.page(this,scroll);setContentView(scroll);
 }catch(Exception e){Toast.makeText(this,getString(R.string.share_failed,String.valueOf(e.getMessage())),Toast.LENGTH_LONG).show();finish();}}
 /** uiMode is declared in configChanges so the job survives; colours are read at build time, so rebuild the page. */
 @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(!isFinishing())recreate();}
 @Override protected void onResume(){super.onResume();if(job!=null)ui.post(refresh);}
 @Override protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
 @Override protected void onSaveInstanceState(Bundle out){out.putString("job",job);if(!running.contains(job)&&text!=null)try{state=new JSONObject(jobs().getString(job,"{}"));state.put("instruction",text.getText().toString());save();}catch(Exception ignored){}super.onSaveInstanceState(out);}
 private void submit(){if(!running.add(job))return;send.setEnabled(false);try{state=new JSONObject(jobs().getString(job,"{}"));state.put("instruction",text.getText().toString()).put("target",ShareNotes.target(this)).put("destination",ShareNotes.label(this)).put("phase","preparing");save();}catch(Exception e){running.remove(job);return;}
  new Thread(()->{try{
   String pkg=state.getString("target");
   JSONArray copied=state.optJSONArray("copied");if(copied==null){copied=new JSONArray();state.put("copied",copied);save();}
   JSONArray sources=state.getJSONArray("sources");for(int i=copied.length();i<sources.length();i++){JSONObject file=ShareNotes.publishSource(this,AssistantAttachments.copy(this,Uri.parse(sources.getString(i))));copied.put(file);save();}
   String body=state.optString("body");if(!body.isEmpty()&&!state.has("original")){
    Uri uri=AssistantAttachments.create(this,false,"text/plain");try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception failure){AssistantAttachments.delete(this,uri);throw failure;}
    state.put("original",ShareNotes.publishSource(this,AssistantAttachments.describe(this,uri,getString(R.string.share_original))));save();
   }
   JSONArray attachments=new JSONArray(copied.toString());if(state.has("original"))attachments.put(state.getJSONObject("original"));
   
   String instruction=state.optString("instruction").trim();String prompt=instruction.isEmpty()?getString(R.string.share_default_instruction):instruction;
   AssistantConversation.enqueueShared(this,prompt,attachments,job);synchronized(ShareNotes.class){state=new JSONObject(jobs().getString(job,"{}"));state.put("phase","queued");save();}startForegroundService(new Intent(this,AgentService.class));
  }catch(Exception e){try{state.put("phase","failed").put("error",String.valueOf(e.getMessage()));save();}catch(Exception ignored){}}finally{running.remove(job);}},"AIOS-share").start();
 }
}
