package os.aios.codex;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Pure, request-scoped wire conversion. No credentials, network or tool execution. */
final class ProviderProtocol {
    static final String RESPONSES="responses", CHAT="chat", MESSAGES="messages";
    static final class Failure extends IOException {
        final String code;
        Failure(String code){super(code);this.code=code;}
    }
    static Failure invalid(){return new Failure("protocol");}
    static JSONObject arguments(String text)throws Exception {
        try{JSONTokener tokenizer=new JSONTokener(text);Object value=tokenizer.nextValue();
            if(!(value instanceof JSONObject)||tokenizer.nextClean()!=0)throw invalid();return (JSONObject)value;
        }catch(JSONException malformed){throw invalid();}
    }
    static JSONObject obj(Object... pairs)throws JSONException {
        JSONObject o=new JSONObject();for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);return o;
    }
    static final class Tool {
        final String name,namespace,alias;final JSONObject definition;
        Tool(JSONObject d,String ns)throws Exception {
            name=d.getString("name");namespace=ns;definition=d;
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((ns+"\n"+name).getBytes(StandardCharsets.UTF_8));
            String prefix=(ns.isEmpty()?name:ns+"_"+name).replaceAll("[^A-Za-z0-9_-]","_");
            StringBuilder b=new StringBuilder(prefix.substring(0,Math.min(38,prefix.length()))).append('_');
            for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",hash[i]));alias=b.toString();
        }
    }
    final String protocol,model;
    final Map<String,Tool> byAlias=new LinkedHashMap<>(),byName=new HashMap<>();
    ProviderProtocol(String protocol,String model,JSONArray definitions)throws Exception {
        this.protocol=protocol;this.model=model;
        if(!CHAT.equals(protocol)&&!MESSAGES.equals(protocol))throw invalid();
        addTools(definitions==null?new JSONArray():definitions,"");
    }
    void addTools(JSONArray definitions,String namespace)throws Exception {
        for(int i=0;i<definitions.length();i++){
            JSONObject d=definitions.getJSONObject(i);String type=d.optString("type");
            if(type.equals("namespace")&&namespace.isEmpty()){addTools(d.getJSONArray("tools"),d.getString("name"));continue;}
            if(!type.equals("function"))throw new Failure("unsupported");
            Tool t=new Tool(d,namespace);String key=namespace+"\n"+t.name;
            if(byName.put(key,t)!=null||byAlias.put(t.alias,t)!=null)throw invalid();
        }
    }
    Tool named(JSONObject item)throws Exception {
        Tool t=byName.get(item.optString("namespace")+"\n"+item.getString("name"));
        if(t==null)throw new Failure("unsupported");return t;
    }
    String callId(String raw)throws Exception {
        // Stable safe IDs also accept historical Codex IDs containing punctuation.
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder("call_");for(int i=0;i<20;i++)b.append(String.format(Locale.ROOT,"%02x",hash[i]));return b.toString();
    }
    JSONArray content(Object value,boolean anthropic)throws Exception {
        JSONArray result=new JSONArray();
        if(value instanceof String)return result.put(obj("type","text","text",value));
        if(!(value instanceof JSONArray))throw new Failure("unsupported");
        JSONArray parts=(JSONArray)value;
        for(int i=0;i<parts.length();i++){
            JSONObject p=parts.getJSONObject(i);String type=p.optString("type");
            if(type.equals("input_text")||type.equals("output_text")||type.equals("text"))result.put(obj("type","text","text",p.getString("text")));
            else if(type.equals("input_image")){
                String url=p.getString("image_url");
                if(!anthropic){result.put(obj("type","image_url","image_url",obj("url",url)));continue;}
                if(url.startsWith("data:")){
                    int comma=url.indexOf(',');if(comma<0)throw invalid();String meta=url.substring(5,comma);
                    if(!Set.of("image/jpeg;base64","image/png;base64","image/gif;base64","image/webp;base64").contains(meta))throw new Failure("unsupported");
                    result.put(obj("type","image","source",obj("type","base64","media_type",meta.substring(0,meta.indexOf(';')),"data",url.substring(comma+1))));
                }else if(url.startsWith("https://"))result.put(obj("type","image","source",obj("type","url","url",url)));
                else throw new Failure("unsupported");
            }else throw new Failure("unsupported");
        }return result;
    }
    JSONObject request(JSONObject source)throws Exception {
        if(!model.equals(source.optString("model"))||source.has("previous_response_id")&&!source.isNull("previous_response_id"))throw invalid();
        boolean anth=MESSAGES.equals(protocol);JSONArray messages=new JSONArray(),system=new JSONArray();
        String instructions=source.optString("instructions");
        if(!instructions.isEmpty()){
            if(anth)system.put(obj("type","text","text",instructions));
            else messages.put(obj("role","system","content",instructions));
        }
        Object raw=source.opt("input");JSONArray input=raw instanceof String?new JSONArray().put(obj("role","user","content",raw)):source.getJSONArray("input");
        for(int i=0;i<input.length();i++){
            JSONObject item=input.getJSONObject(i);String type=item.optString("type","message");
            if(type.equals("message")){
                String role=item.getString("role");JSONArray parts=content(item.get("content"),anth);
                if(role.equals("system")||role.equals("developer")){
                    if(anth){for(int n=0;n<parts.length();n++){if(!parts.getJSONObject(n).getString("type").equals("text"))throw invalid();system.put(parts.get(n));}}
                    else messages.put(obj("role","system","content",parts));
                }else if(role.equals("user")||role.equals("assistant"))append(messages,role,parts,anth);
                else throw invalid();
            }else if(type.equals("function_call")){
                Tool t=named(item);String id=callId(item.getString("call_id"));String args=item.getString("arguments");JSONObject parsed=arguments(args);
                if(anth)append(messages,"assistant",new JSONArray().put(obj("type","tool_use","id",id,"name",t.alias,"input",parsed)),true);
                else {
                    JSONObject last=messages.length()==0?null:messages.getJSONObject(messages.length()-1);
                    if(last==null||!last.optString("role").equals("assistant")){last=obj("role","assistant","content",JSONObject.NULL);messages.put(last);}
                    JSONArray calls=last.optJSONArray("tool_calls");if(calls==null){calls=new JSONArray();last.put("tool_calls",calls);}
                    calls.put(obj("id",id,"type","function","function",obj("name",t.alias,"arguments",args)));
                }
            }else if(type.equals("function_call_output")){
                String id=callId(item.getString("call_id"));Object output=item.get("output");
                if(anth)append(messages,"user",new JSONArray().put(obj("type","tool_result","tool_use_id",id,"content",content(output,true))),true);
                else {
                    // Chat tool messages accept text, not image content. Keep images in a following user message.
                    JSONArray parts=content(output,false),images=new JSONArray();StringBuilder text=new StringBuilder();
                    for(int n=0;n<parts.length();n++){JSONObject p=parts.getJSONObject(n);if(p.optString("type").equals("text"))text.append(p.getString("text")).append('\n');else images.put(p);}
                    messages.put(obj("role","tool","tool_call_id",id,"content",text.toString()));
                    if(images.length()>0){
                        // Defer until all adjacent tool results have been appended.
                        JSONObject marker=obj("role","user","content",images,"_tool_images",true);messages.put(marker);
                    }
                }
            }else if(type.equals("reasoning")){
                // This adapter never requests private thinking. Do not invent or forward a foreign encrypted state.
                if(item.has("encrypted_content")&&!item.isNull("encrypted_content"))throw new Failure("unsupported");
            }else throw new Failure("unsupported");
        }
        if(!anth)messages=orderToolImages(messages);
        JSONArray tools=new JSONArray();
        for(Tool t:byAlias.values()){
            JSONObject schema=t.definition.optJSONObject("parameters");if(schema==null)schema=obj("type","object","properties",new JSONObject());
            JSONObject d=obj("name",t.alias,"description",t.definition.optString("description"));
            d.put(anth?"input_schema":"parameters",schema);
            tools.put(anth?d:obj("type","function","function",d));
        }
        JSONObject out=obj("model",model,"messages",messages,"stream",true);
        if(tools.length()>0)out.put("tools",tools);
        if(anth){
            if(system.length()>0)out.put("system",system);
            out.put("max_tokens",Math.min(16384,Math.max(1,source.optInt("max_output_tokens",8192))));
            out.put("thinking",obj("type","disabled"));
        }else {out.put("stream_options",obj("include_usage",true));out.put("enable_thinking",false);}
        Object choice=source.opt("tool_choice");
        if(choice instanceof String){
            String c=(String)choice;
            if(!Set.of("auto","none","required").contains(c))throw new Failure("unsupported");
            if(tools.length()>0)out.put("tool_choice",anth?obj("type",c.equals("required")?"any":c):c);
        }else if(choice instanceof JSONObject){
            Tool t=named((JSONObject)choice);out.put("tool_choice",anth?obj("type","tool","name",t.alias):obj("type","function","function",obj("name",t.alias)));
        }
        return out;
    }
    static void append(JSONArray messages,String role,JSONArray parts,boolean merge)throws Exception {
        JSONObject last=messages.length()==0?null:messages.getJSONObject(messages.length()-1);
        if(merge&&last!=null&&role.equals(last.optString("role"))){JSONArray old=last.getJSONArray("content");for(int i=0;i<parts.length();i++)old.put(parts.get(i));}
        else messages.put(obj("role",role,"content",parts));
    }
    static JSONArray orderToolImages(JSONArray messages)throws Exception {
        // All results of a parallel tool batch must precede the synthetic image message.
        JSONArray ordered=new JSONArray(),images=new JSONArray();
        for(int i=0;i<=messages.length();i++){
            JSONObject m=i==messages.length()?null:messages.getJSONObject(i);
            if(m!=null&&m.optBoolean("_tool_images")){JSONArray parts=m.getJSONArray("content");for(int j=0;j<parts.length();j++)images.put(parts.get(j));continue;}
            if(m==null||!m.optString("role").equals("tool")){if(images.length()>0){ordered.put(obj("role","user","content",images));images=new JSONArray();}}
            if(m!=null)ordered.put(m);
        }return ordered;
    }
    interface Sink {void emit(String type,JSONObject event)throws Exception;}
    final class Stream {
        final Sink sink;final String responseId="resp_"+UUID.randomUUID().toString().replace("-","");
        final LinkedHashMap<Integer,Part> parts=new LinkedHashMap<>();
        long inputTokens,outputTokens,cachedTokens;int chars;String stop="";boolean terminal,started,upstreamStarted;
        final class Part {final int index;final String id;final boolean tool;final StringBuilder text=new StringBuilder(),args=new StringBuilder(),name=new StringBuilder();String call="";boolean closed;
            Part(int index,boolean tool){this.index=index;this.tool=tool;id=(tool?"fc_":"msg_")+UUID.randomUUID().toString().replace("-","");}}
        Stream(Sink sink){this.sink=sink;}
        JSONObject response(String status,JSONArray output)throws Exception{return obj("id",responseId,"object","response","created_at",System.currentTimeMillis()/1000,"model",model,"status",status,"output",output);}
        void start()throws Exception{sink.emit("response.created",obj("response",response("in_progress",new JSONArray())));started=true;}
        Part part(int key,boolean tool)throws Exception {
            Part p=parts.get(key);if(p!=null){if(p.tool!=tool||p.closed)throw invalid();return p;}
            if(parts.size()>=128)throw new Failure("limit");p=new Part(parts.size(),tool);parts.put(key,p);
            // Tool items are withheld until the complete upstream response is validated.
            if(!tool){sink.emit("response.output_item.added",obj("output_index",p.index,"item",message(p,"in_progress")));sink.emit("response.content_part.added",obj("output_index",p.index,"item_id",p.id,"content_index",0,"part",obj("type","output_text","text","","annotations",new JSONArray())));}
            return p;
        }
        void text(Part p,String value)throws Exception{budget(value);p.text.append(value);if(!value.isEmpty())sink.emit("response.output_text.delta",obj("output_index",p.index,"item_id",p.id,"content_index",0,"delta",value));}
        void budget(String value)throws Exception{chars+=value.length();if(chars>4_000_000)throw new Failure("limit");}
        JSONObject message(Part p,String status)throws Exception{return obj("type","message","id",p.id,"role","assistant","status",status,"content",new JSONArray().put(obj("type","output_text","text",p.text.toString(),"annotations",new JSONArray())));}
        void chat(JSONObject e)throws Exception {
            if(terminal)throw invalid();if(e.has("error"))throw new Failure("upstream");
            JSONObject usage=e.optJSONObject("usage");if(usage!=null){inputTokens=usage.optLong("prompt_tokens");outputTokens=usage.optLong("completion_tokens");JSONObject details=usage.optJSONObject("prompt_tokens_details");cachedTokens=details==null?0:details.optLong("cached_tokens");}
            JSONArray choices=e.optJSONArray("choices");if(choices==null)throw invalid();
            for(int i=0;i<choices.length();i++){
                JSONObject c=choices.getJSONObject(i);if(c.optInt("index",0)!=0||!stop.isEmpty())throw invalid();
                JSONObject delta=c.optJSONObject("delta");if(delta!=null){
                    if(!delta.isNull("refusal")&&!delta.getString("refusal").isEmpty())throw new Failure("refused");
                    if(!delta.isNull("reasoning_content")&&!delta.getString("reasoning_content").isEmpty())throw new Failure("unsupported");
                    if(!delta.isNull("content"))text(part(-1,false),delta.getString("content"));
                    JSONArray calls=delta.optJSONArray("tool_calls");if(calls!=null)for(int n=0;n<calls.length();n++){
                        JSONObject call=calls.getJSONObject(n);int index=call.getInt("index");if(index<0||index>=128)throw invalid();Part p=part(index,true);
                        if(!call.isNull("id")){String id=call.getString("id");if(id.length()>256||!p.call.isEmpty()&&!p.call.equals(id))throw invalid();p.call=id;}
                        JSONObject f=call.optJSONObject("function");if(f!=null){String name=f.isNull("name")?"":f.getString("name"),args=f.isNull("arguments")?"":f.getString("arguments");budget(name);budget(args);p.name.append(name);p.args.append(args);}
                    }
                }
                if(!c.isNull("finish_reason"))stop=c.getString("finish_reason");
            }
        }
        void anthropic(JSONObject e)throws Exception {
            if(terminal)throw invalid();String type=e.getString("type");
            if(!upstreamStarted&&!Set.of("message_start","ping","error").contains(type))throw invalid();
            switch(type){
                case "message_start": {if(upstreamStarted)throw invalid();upstreamStarted=true;JSONObject u=e.getJSONObject("message").optJSONObject("usage");if(u!=null){cachedTokens=u.optLong("cache_read_input_tokens");inputTokens=u.optLong("input_tokens")+cachedTokens+u.optLong("cache_creation_input_tokens");}break;}
                case "content_block_start": {int index=e.getInt("index");if(index<0||index>=128||parts.containsKey(index))throw invalid();JSONObject b=e.getJSONObject("content_block");String bt=b.getString("type");
                    if(bt.equals("text"))text(part(index,false),b.optString("text"));
                    else if(bt.equals("tool_use")){Part p=part(index,true);p.call=b.getString("id");String name=b.getString("name");if(p.call.length()>256||name.length()>64)throw invalid();p.name.append(name);JSONObject initial=b.optJSONObject("input");if(initial!=null&&initial.length()>0){String a=initial.toString();budget(a);p.args.append(a);}}
                    else throw new Failure("unsupported");break;}
                case "content_block_delta": {Part p=parts.get(e.getInt("index"));if(p==null||p.closed)throw invalid();JSONObject d=e.getJSONObject("delta");String dt=d.getString("type");
                    if(dt.equals("text_delta")&&!p.tool)text(p,d.getString("text"));
                    else if(dt.equals("input_json_delta")&&p.tool){String a=d.getString("partial_json");budget(a);p.args.append(a);}
                    else throw new Failure("unsupported");break;}
                case "content_block_stop": {Part p=parts.get(e.getInt("index"));if(p==null||p.closed)throw invalid();p.closed=true;break;}
                case "message_delta": {stop=e.getJSONObject("delta").optString("stop_reason",stop);JSONObject u=e.optJSONObject("usage");if(u!=null)outputTokens=u.optLong("output_tokens");break;}
                case "message_stop": for(Part p:parts.values())if(!p.closed)throw invalid();finish();break;
                case "ping": break;
                case "error": throw new Failure("upstream");
                default: throw new Failure("unsupported");
            }
        }
        void finish()throws Exception {
            if(terminal||parts.isEmpty()||inputTokens<0||outputTokens<0||cachedTokens<0)throw invalid();boolean calls=parts.values().stream().anyMatch(p->p.tool);
            if(stop.equals("length")||stop.equals("max_tokens")||stop.equals("model_context_window_exceeded"))throw new Failure("truncated");
            if(stop.equals("content_filter")||stop.equals("refusal"))throw new Failure("refused");
            if(calls?!Set.of("tool_calls","tool_use").contains(stop):!Set.of("stop","end_turn","stop_sequence").contains(stop))throw invalid();
            JSONArray output=new JSONArray();Set<String> ids=new HashSet<>();
            for(Part p:parts.values()){
                if(!p.tool){output.put(message(p,"completed"));continue;}
                Tool t=byAlias.get(p.name.toString());if(t==null||p.call.isEmpty()||!ids.add(p.call))throw invalid();
                if(p.args.length()==0&&CHAT.equals(protocol))throw invalid();
                String args=p.args.length()==0?"{}":p.args.toString();arguments(args); // Require a complete JSON object before execution.
                JSONObject item=obj("type","function_call","id",p.id,"name",t.name,"call_id",p.call,"arguments",args,"status","completed");
                if(!t.namespace.isEmpty())item.put("namespace",t.namespace);output.put(item);
            }
            // Validate every tool before emitting any output_item.done (Codex may execute immediately).
            for(int i=0;i<output.length();i++){
                JSONObject item=output.getJSONObject(i);
                if(item.optString("type").equals("function_call"))sink.emit("response.output_item.added",obj("output_index",i,"item",item));
                sink.emit("response.output_item.done",obj("output_index",i,"item",item));
            }
            JSONObject usage=obj("input_tokens",inputTokens,"output_tokens",outputTokens,"total_tokens",inputTokens+outputTokens,"input_tokens_details",obj("cached_tokens",cachedTokens));
            sink.emit("response.completed",obj("response",response("completed",output).put("usage",usage)));terminal=true;
        }
    }
}
