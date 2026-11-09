package os.aios.runtime;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Immutable task-scoped grant. Android permission remains an additional prerequisite. */
public final class TaskAuthorization {
    public final String id,subject,taskId;public final long approvedVersion,expiresAt;private final Set<String> capabilities,resources;
    public TaskAuthorization(JSONObject value)throws Exception{
        id=value.getString("id");subject=value.getString("subject");taskId=value.optString("task_id","");approvedVersion=value.getLong("approved_version");expiresAt=value.optLong("expires_at",0);
        capabilities=set(value.getJSONArray("capabilities"));resources=set(value.optJSONArray("resources"));
    }
    private static Set<String> set(JSONArray a)throws Exception{Set<String>s=new HashSet<>();if(a!=null)for(int i=0;i<a.length();i++)s.add(a.getString(i));return s;}
    public void require(String capability,String resource,long planVersion,long now){
        if(expiresAt>0&&now>=expiresAt)throw new SecurityException("Task authorization expired");
        if(planVersion!=approvedVersion)throw new SecurityException("Plan changed; authorization must be renewed");
        if(!capabilities.contains(capability))throw new SecurityException("Capability outside task authorization: "+capability);
        if(resource!=null&&!resource.isEmpty()&&!resources.contains(resource))throw new SecurityException("Resource outside task authorization: "+resource);
    }
}

