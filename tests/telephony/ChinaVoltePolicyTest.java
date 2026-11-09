package com.android.phone;
public final class ChinaVoltePolicyTest {
 public static void main(String[] args) {
  int count=0;
  for(String p:new String[]{"46000","46002","46004","46007","46008","46013","46001","46006","46009","46003","46011","46012"}) {
   if(!ChinaVoltePolicy.applies("frankel",p))throw new AssertionError(p);count++;
   if(ChinaVoltePolicy.applies("other",p))throw new AssertionError("device scope "+p);count++;
  }
  for(String p:new String[]{null,"","460","46015","45400","310260","460110"}) {
   if(ChinaVoltePolicy.applies("frankel",p))throw new AssertionError("PLMN scope "+p);count++;
  }
  System.out.println("PASS "+count+" device/SIM matching assertions");
 }
}
