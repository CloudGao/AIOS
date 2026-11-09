package com.android.server;
import android.app.ActivityThread;
import android.content.*;
import android.provider.Settings;
import android.view.WindowManager;
public class OverlayPolicyDeviceCheck {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception {
  com.android.internal.os.ApplicationSharedMemory.setInstance(com.android.internal.os.ApplicationSharedMemory.create());
  android.os.Looper.prepareMainLooper();Context base=ActivityThread.systemMain().getSystemContext();
  // app_process is not an AMS-registered application. Acquire Settings using the
  // explicit external-provider API rather than passing an unregistered app thread.
  android.os.Binder token=new android.os.Binder();
  android.app.ContentProviderHolder holder=android.app.ActivityManager.getService()
      .getContentProviderExternal("settings",0,token,"AIOS overlay policy test");
  if(holder==null)throw new AssertionError("Settings provider unavailable");
  ContentResolver resolver=new ContentResolver(base) {
   protected IContentProvider acquireProvider(Context c,String name){
    if(!"settings".equals(name))throw new IllegalArgumentException(name);
    return holder.provider;
   }
   protected IContentProvider acquireUnstableProvider(Context c,String name){return acquireProvider(c,name);}
   public boolean releaseProvider(IContentProvider p){return true;}
   public boolean releaseUnstableProvider(IContentProvider p){return true;}
   public void unstableProviderDied(IContentProvider p){throw new AssertionError("Settings provider died");}
  };
  Context c=new ContextWrapper(base){public ContentResolver getContentResolver(){return resolver;}};
  ComponentName component=new ComponentName("os.aios.agent","os.aios.agent.ForegroundUiService");
  int uid=c.getPackageManager().getPackageUidAsUser("os.aios.agent",0);
  String key="aios_assistant_ui_control_enabled";
  int old=Settings.Secure.getIntForUser(c.getContentResolver(),key,0,0);
  try {
   Settings.Secure.putIntForUser(c.getContentResolver(),key,1,0);
   check(AiosAssistantPolicy.mayBypass(c,component,0),"signed AIOS current-user service authorized");
   android.content.pm.ServiceInfo service=c.getPackageManager().getServiceInfo(component,0);
   check(AiosAssistantPolicy.mayBypassService(c,component,service,0),"resolved service UID authorized");
   check(!AiosAssistantPolicy.mayBypassService(c,component,service,10),"different service user denied");

   check(!AiosAssistantPolicy.mayBypass(c,new ComponentName("other","other.Service"),0),"other package denied");
   check(!AiosAssistantPolicy.mayBypass(c,new ComponentName("os.aios.agent","os.aios.agent.Other"),0),"other component denied");
   Settings.Secure.putIntForUser(c.getContentResolver(),key,0,0);
   check(!AiosAssistantPolicy.mayBypass(c,component,0),"authorization disabled denies bypass");
   WindowManager.LayoutParams p=new WindowManager.LayoutParams();p.type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
   p.packageName="os.aios.agent";p.setTitle("AIOS Assistant Panel");
   check(AiosAssistantPolicy.isPanel(c,uid,p),"signed panel identified");
   check(!AiosAssistantPolicy.isPanel(c,2000,p),"shell cannot impersonate panel UID");
   p.setTitle("Other panel");check(!AiosAssistantPolicy.isPanel(c,uid,p),"other title denied");
   p.setTitle("AIOS Assistant Panel");p.type=WindowManager.LayoutParams.TYPE_APPLICATION;
   check(!AiosAssistantPolicy.isPanel(c,uid,p),"activity cannot impersonate panel");
  } finally {
   try {Settings.Secure.putIntForUser(c.getContentResolver(),key,old,0);}
   finally {android.app.ActivityManager.getService().removeContentProviderExternalAsUser("settings",token,0);}
  }
  System.out.println("PASSED "+count+" device policy checks");System.exit(0);
 }
}