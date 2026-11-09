package com.android.systemui.statusbar.notification.collection;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Main-thread freshness ledger. No rate limit or minimum gap between new messages. */
public final class AiosAlertHistory {
    private final Map<Integer,Long> latestTime=new HashMap<>();
    private final Map<Integer,String> latestId=new HashMap<>();
    private final Set<String> shown=new LinkedHashSet<>();
    public void arriving(int user,String id,long time){
        if(time>=latestTime.getOrDefault(user,0L)){latestTime.put(user,time);latestId.put(user,id);}
    }
    public boolean claim(int user,String id,long time,long now,long persistedTime,String persistedId){
        if(id==null||id.isEmpty()||time<=0||now-time>60000)return false;
        // A wall-clock correction must not suppress all subsequent notifications.
        if(latestTime.getOrDefault(user,0L)>now+60000){latestTime.remove(user);latestId.remove(user);}
        if(persistedTime>now+60000){persistedTime=0;persistedId="";}
        String key=user+":"+id;
        if(shown.contains(key)||time<latestTime.getOrDefault(user,0L))return false;
        if(time==latestTime.getOrDefault(user,0L)&&!id.equals(latestId.get(user)))return false;
        if(time<persistedTime||(time==persistedTime&&id.equals(persistedId)))return false;
        if(shown.size()>=2048)shown.remove(shown.iterator().next());shown.add(key);return true;
    }
}
