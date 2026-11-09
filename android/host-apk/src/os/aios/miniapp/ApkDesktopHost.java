package os.aios.miniapp;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Icon;
import org.json.*;
public final class ApkDesktopHost implements DesktopIntegration.Host {
 public String request(Context c,JSONObject app)throws Exception{
  ShortcutManager manager=c.getSystemService(ShortcutManager.class);
  if(manager==null||!manager.isRequestPinShortcutSupported())return "unsupported";
  String id=app.getJSONObject("manifest").getString("id"),name=os.aios.core.DisplayText.clean(app.getJSONObject("manifest").getString("name"));
  String key="mini:"+id+":"+app.getString("generation");
  for(ShortcutInfo existing:manager.getPinnedShortcuts())if(key.equals(existing.getId()))return "pinned";
  ShortcutInfo shortcut=new ShortcutInfo.Builder(c,key).setShortLabel(name).setLongLabel(name).setIcon(MiniAppIcon.create(id,name))
   .setActivity(new ComponentName(c,os.aios.agent.ApkHomeActivity.class))
   .setIntent(new Intent(c,ApkMiniAppEntryActivity.class).setAction(Intent.ACTION_VIEW).setData(DesktopIntegration.uri(app))).build();
  return manager.requestPinShortcut(shortcut,null)?"requested":"unsupported";
 }
}
