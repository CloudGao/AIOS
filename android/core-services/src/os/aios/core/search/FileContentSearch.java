package os.aios.core.search;

import org.json.*;
import os.aios.runtime.DeviceSearchClient;
import os.aios.core.CoreFeatures;

/** One candidate per bounded page; callers continue using the existing recall cursor. */
final class FileContentSearch {
    static JSONObject page(DeviceSearchClient device,CoreFeatures.Documents documents,JSONObject request,JSONObject progress,String owner)throws Exception{
        JSONArray items=new JSONArray(),skipped=new JSONArray();JSONObject candidate=progress.optJSONObject("candidate");boolean more;
        if(candidate==null){JSONObject metadata=new JSONObject().put("source","files").put("query","").put("limit",1).put("after_id",progress.optLong("after_id",0)).put("filters",request.getJSONObject("filters"));JSONObject page=device.call("query",metadata);
            if(!page.optString("state").equals("available"))return new JSONObject().put("items",items).put("has_more",false).put("state",page.optString("state"));
            JSONArray rows=page.getJSONArray("items");if(rows.length()==0)return new JSONObject().put("items",items).put("has_more",false).put("state","available");candidate=rows.getJSONObject(0);progress.put("candidate",candidate).put("metadata_more",page.optBoolean("has_more"));
        }
        more=progress.optBoolean("metadata_more");int scanned=0;long bytes=0;
        try{
            // Revalidate the row on every continuation; no stale cached path grants.
            JSONArray fresh=device.call("get",new JSONObject().put("source","files").put("id",candidate.getString("id"))).getJSONArray("items");if(fresh.length()!=1||!fresh.getJSONObject(0).getString("source_version").equals(candidate.getString("source_version")))throw new IllegalStateException("source_changed");
            JSONObject find=new JSONObject().put("query",request.getString("query")).put("index",progress.optInt("index",0)).put("offset",progress.optInt("offset",0)).put("limit",5).put("max_chars",Math.min(4000,request.optInt("max_chars",16000)));
            JSONObject found=documents.findSource(candidate.getString("uri"),candidate.getString("title"),find,owner);bytes=found.optLong("bytes_read");
            if(progress.has("version")&&!progress.getString("version").equals(found.getString("source_version")))throw new IllegalStateException("source_changed");progress.put("version",found.getString("source_version"));
            JSONArray hits=found.getJSONArray("items");if(hits.length()>0){JSONObject item=new JSONObject(candidate.toString());item.put("match_reason","document_literal").put("matches",hits).put("snippet",hits.getJSONObject(0).getString("text")).put("document_version",found.getString("source_version"));items.put(item);}
            if(found.optString("text_state").equals("needs_ocr"))skipped.put(new JSONObject().put("id",candidate.getString("id")).put("reason","needs_ocr"));
            if(found.getBoolean("has_more")){JSONObject next=found.getJSONObject("next");progress.put("index",next.getInt("index")).put("offset",next.getInt("offset"));more=true;}
            else{scanned=found.optString("text_state").equals("needs_ocr")?0:1;finish(progress,candidate);}
        }catch(Exception e){String reason=e.getMessage();if(reason==null||!reason.matches("[a-z_]{3,80}"))reason="document_unavailable";skipped.put(new JSONObject().put("id",candidate.getString("id")).put("reason",reason));finish(progress,candidate);}
        return new JSONObject().put("items",items).put("has_more",more).put("state","available").put("scanned_files",scanned).put("skipped_files",skipped).put("bytes_read",bytes).put("coverage","media_provider_candidates_only").put("matching","literal_within_unit");
    }
    private static void finish(JSONObject p,JSONObject row)throws Exception{p.put("after_id",row.getLong("id"));for(String key:new String[]{"candidate","index","offset","version","metadata_more"})p.remove(key);}
}
