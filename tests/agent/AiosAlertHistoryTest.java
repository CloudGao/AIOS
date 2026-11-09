package com.android.systemui.statusbar.notification.collection;

public final class AiosAlertHistoryTest {
    static int assertions;
    static void check(boolean result){assertions++;if(!result)throw new AssertionError("alert check "+assertions);}
    public static void main(String[] args){
        AiosAlertHistory history=new AiosAlertHistory();long now=1000000;
        history.arriving(0,"old",now-10);history.arriving(0,"new",now);
        check(!history.claim(0,"old",now-10,now,0,""));check(history.claim(0,"new",now,now,0,""));check(!history.claim(0,"new",now,now,0,""));
        // A thousand genuine messages may alert with no minimum interval.
        for(int i=1;i<=1000;i++){String id="chat-"+i;history.arriving(0,id,now+i);check(history.claim(0,id,now+i,now+i,0,""));}
        check(!history.claim(0,"old",now-10,now+1001,0,""));
        history.arriving(10,"profile",now);check(history.claim(10,"profile",now,now,0,""));
        check(!new AiosAlertHistory().claim(0,"stale",now-60001,now,0,""));
        check(!new AiosAlertHistory().claim(0,"seen-before-restart",now,now,now,"seen-before-restart"));
        history.arriving(0,"same-time-a",now+2000);history.arriving(0,"same-time-b",now+2000);
        check(!history.claim(0,"same-time-a",now+2000,now+2000,0,""));check(history.claim(0,"same-time-b",now+2000,now+2000,0,""));
        history.arriving(0,"clock-old",now+1000000);history.arriving(0,"clock-new",now+3000);
        check(history.claim(0,"clock-new",now+3000,now+3000,now+1000000,"clock-old"));
        System.out.println("PASS "+assertions+" heads-up freshness assertions");
    }
}
