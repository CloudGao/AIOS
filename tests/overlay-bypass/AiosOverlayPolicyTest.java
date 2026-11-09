package com.android.server;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.view.WindowManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class AiosOverlayPolicyTest {
 @Test public void panelIdentityMustMatchEveryField() throws Exception {
  Context c=mock(Context.class); PackageManager pm=mock(PackageManager.class);
  when(c.getPackageManager()).thenReturn(pm);
  when(pm.getPackageUidAsUser("os.aios.agent",0)).thenReturn(10123);
  when(pm.checkSignatures(10123,Process.SYSTEM_UID)).thenReturn(PackageManager.SIGNATURE_MATCH);
  WindowManager.LayoutParams p=new WindowManager.LayoutParams();
  p.type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;p.packageName="os.aios.agent";p.setTitle("AIOS Assistant Panel");
  assertTrue(AiosAssistantPolicy.isPanel(c,10123,p));
  assertFalse(AiosAssistantPolicy.isPanel(c,10124,p));
  p.setTitle("Another window");assertFalse(AiosAssistantPolicy.isPanel(c,10123,p));p.setTitle("AIOS Assistant Panel");
  p.type=WindowManager.LayoutParams.TYPE_APPLICATION;assertFalse(AiosAssistantPolicy.isPanel(c,10123,p));
  p.type=WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;p.packageName="other";assertFalse(AiosAssistantPolicy.isPanel(c,10123,p));p.packageName="os.aios.agent";
  when(pm.checkSignatures(10123,Process.SYSTEM_UID)).thenReturn(PackageManager.SIGNATURE_NO_MATCH);
  assertFalse(AiosAssistantPolicy.isPanel(c,10123,p));
 }
 @Test public void unrelatedServiceCannotBypass() {
  Context c=mock(Context.class);
  assertFalse(AiosAssistantPolicy.mayBypass(c,null,0));
  assertFalse(AiosAssistantPolicy.mayBypass(c,new android.content.ComponentName("other","other.Service"),0));
  assertFalse(AiosAssistantPolicy.mayBypass(c,new android.content.ComponentName("os.aios.agent","os.aios.agent.OtherService"),0));
  verifyNoMoreInteractions(c);
 }
}
