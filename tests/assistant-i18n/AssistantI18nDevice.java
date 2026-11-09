package os.aios.tests;
import android.content.*;
import android.content.res.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import dalvik.system.PathClassLoader;

/** Read-only resource/policy tests; locale action is an explicit, reversible device test setting. */
public final class AssistantI18nDevice {
 static Context system()throws Exception {
  Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
  return (Context)at.getMethod("getSystemContext").invoke(thread);
 }
 static int count;
 static void check(boolean b,String description){if(!b)throw new AssertionError(description);count++;}
 public static void main(String[] args)throws Exception {
  Looper.prepareMainLooper();Context system=system();
  if(args.length>0&&args[0].equals("locale")){
   Object manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
   Class<?> api=Class.forName("android.app.IActivityManager");Configuration config=(Configuration)api.getMethod("getConfiguration").invoke(manager);
   System.out.println("PREVIOUS_LOCALES="+config.getLocales().toLanguageTags());
   if(args.length>1){config.setLocales(LocaleList.forLanguageTags(args[1]));Configuration.class.getField("userSetLocale").setBoolean(config,true);api.getMethod("updatePersistentConfiguration",Configuration.class).invoke(manager,config);System.out.println("SET_LOCALES="+args[1]);}
   return;
  }
  Context agent=system.createPackageContext("os.aios.agent",Context.CONTEXT_IGNORE_SECURITY);
  String[][] samples={{"en-US","Assistant","New conversation"},{"zh-CN","小助手","新对话"},{"zh-SG","小助手","新对话"},{"zh-TW","小助手","新對話"},{"zh-HK","小助手","新對話"},{"zh-Hant","小助手","新對話"},{"fr-FR","Assistant","New conversation"},{"ar-EG","Assistant","New conversation"},{"ja-JP","Assistant","New conversation"},{"es-ES","Assistant","New conversation"}};
  for(String[] sample:samples){Configuration cfg=new Configuration(agent.getResources().getConfiguration());cfg.setLocales(LocaleList.forLanguageTags(sample[0]));Context localized=agent.createConfigurationContext(cfg);Resources res=localized.getResources();
   check(res.getString(res.getIdentifier("assistant_assistant_d78a1","string","os.aios.agent")).equals(sample[1]),sample[0]+" title");
   check(res.getString(res.getIdentifier("assistant_new_conversation_1b7ab","string","os.aios.agent")).equals(sample[2]),sample[0]+" menu");
   String format=res.getString(res.getIdentifier("assistant_started_updated","string","os.aios.agent"),"START","UPDATE","DURATION");check(format.contains("START")&&format.contains("UPDATE")&&format.contains("DURATION"),sample[0]+" format");
   check(cfg.getLayoutDirection()==(sample[0].startsWith("ar")?1:0),sample[0]+" direction");
  }
  ClassLoader agentLoader=new PathClassLoader(agent.getApplicationInfo().sourceDir,ClassLoader.getSystemClassLoader());
  Class<?> presentation=Class.forName("os.aios.agent.AssistantLocale",true,agentLoader);Method progress=presentation.getDeclaredMethod("progress",Context.class,org.json.JSONObject.class);progress.setAccessible(true);Method notice=presentation.getDeclaredMethod("notice",Context.class,org.json.JSONObject.class);notice.setAccessible(true);
  for(String tag:new String[]{"en-US","zh-CN","zh-TW"}){
   Configuration cfg=new Configuration(agent.getResources().getConfiguration());cfg.setLocales(LocaleList.forLanguageTags(tag));Context localized=agent.createConfigurationContext(cfg);
   org.json.JSONObject state=new org.json.JSONObject().put("progress_kind","retry").put("progress_args",new org.json.JSONArray().put("2").put("5"));String rendered=(String)progress.invoke(null,localized,state);
   check(rendered.contains("2/5"),tag+" retry placeholders");check(tag.startsWith("en")?rendered.startsWith("Connection"):rendered.contains("模型"),tag+" progress localization");
   state=new org.json.JSONObject().put("progress_kind","").put("progress","USER_MODEL_TEXT");check(progress.invoke(null,localized,state).equals("USER_MODEL_TEXT"),tag+" user/model text untouched");
   state=new org.json.JSONObject().put("notice_resource","assistant_speech_language_unsupported").put("notice","OLD_LANGUAGE");String renderedNotice=(String)notice.invoke(null,localized,state);check(!renderedNotice.contains("OLD_LANGUAGE"),tag+" persisted notice relocalized");
  }
  Context runtime=system.createPackageContext("os.aios.codex",Context.CONTEXT_IGNORE_SECURITY);
  ClassLoader loader=new PathClassLoader(runtime.getApplicationInfo().sourceDir,ClassLoader.getSystemClassLoader());
  Class<?> policy=Class.forName("os.aios.codex.CommunicationLanguage",true,loader);Method method=policy.getDeclaredMethod("instructions",Locale.class);method.setAccessible(true);
  for(String tag:new String[]{"en-US","zh-Hans-CN","zh-Hant-TW","ar-EG","fr-FR","ja-JP","ko-KR","es-MX","pt-BR","de-DE","ru-RU","hi-IN"}){
   String rule=(String)method.invoke(null,Locale.forLanguageTag(tag));check(rule.contains(tag),tag+" primary system locale");check(rule.contains("explicit user request")&&rule.contains("takes precedence"),tag+" override");check(rule.contains("old conversation turns")&&rule.contains("does not override"),tag+" existing thread");
  }
  System.out.println("PASS: "+count+" Android resource and installed runtime policy assertions");
 }
}
