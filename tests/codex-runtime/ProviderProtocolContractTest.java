package os.aios.codex;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
import java.io.StringReader;
import java.util.*;
import static os.aios.codex.ProviderProtocol.obj;

/** Prepared for the unified acceptance batch. Fake credentials; no network or device tools. */
public final class ProviderProtocolContractTest extends Instrumentation {
    int assertions;
    interface Check {void run()throws Exception;}
    void expect(boolean value)throws Exception{if(!value)throw new Exception("Assertion "+assertions);assertions++;}
    void reject(String code,Check check)throws Exception{
        try{check.run();throw new Exception("Expected rejection "+assertions);}catch(ProviderProtocol.Failure failure){expect(code.equals(failure.code));}
    }
    JSONArray definitions()throws Exception{return new JSONArray().put(obj("type","function","name","phone.action","description","Query phone information","parameters",obj("type","object","properties",obj("name",obj("type","string")))));}
    JSONObject request(JSONArray history)throws Exception{return obj("model","fixture","instructions","System instruction","stream",true,"tools",definitions(),"input",history);}
    JSONObject user(String text)throws Exception{return obj("role","user","content",new JSONArray().put(obj("type","input_text","text",text)));}
    JSONObject delta(JSONObject value,Object finish)throws Exception{return obj("choices",new JSONArray().put(obj("index",0,"delta",value,"finish_reason",finish)));}
    JSONObject toolDelta(String id,String name,String args)throws Exception{return obj("tool_calls",new JSONArray().put(obj("index",0,"id",id,"function",obj("name",name,"arguments",args))));}
    static final class Events implements ProviderProtocol.Sink {
        final List<JSONObject> events=new ArrayList<>();
        public void emit(String type,JSONObject value)throws Exception{events.add(new JSONObject(value.toString()).put("type",type));}
        long count(String type){return events.stream().filter(e->type.equals(e.optString("type"))).count();}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();try{checks();result.putString("result","PASS "+assertions+" protocol assertions");finish(-1,result);}catch(Exception e){result.putString("result","FAIL "+assertions+" "+e.getClass().getSimpleName());finish(0,result);}}
    void checks()throws Exception {
        ProviderProtocol chat=new ProviderProtocol("chat","fixture",definitions()),claude=new ProviderProtocol("messages","fixture",definitions());
        JSONArray history=new JSONArray().put(user("你好 مرحبا"));
        JSONObject c=chat.request(request(history)),a=claude.request(request(history));
        expect(c.getJSONArray("messages").getJSONObject(0).getString("role").equals("system"));
        expect(a.getJSONArray("system").getJSONObject(0).getString("text").equals("System instruction"));
        expect(a.getJSONArray("messages").toString().contains("你好 مرحبا"));
        expect(a.getInt("max_tokens")==8192);expect(!c.getBoolean("enable_thinking"));
        String alias=chat.byAlias.keySet().iterator().next();expect(alias.length()<=64&&alias.matches("[A-Za-z0-9_-]+"));
        history.put(obj("type","function_call","call_id","call:original","name","phone.action","arguments","{\"name\":\"battery\"}"));
        history.put(obj("type","function_call_output","call_id","call:original","output","42%"));
        c=chat.request(request(history));a=claude.request(request(history));
        JSONArray cm=c.getJSONArray("messages"),am=a.getJSONArray("messages");
        expect(cm.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id").equals(cm.getJSONObject(3).getString("tool_call_id")));
        expect(am.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("id").equals(am.getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("tool_use_id")));
        expect(am.getJSONObject(1).getJSONArray("content").getJSONObject(0).getJSONObject("input").getString("name").equals("battery"));
        JSONObject image=obj("type","input_image","image_url","data:image/png;base64,AA==");
        JSONArray images=new JSONArray().put(obj("role","user","content",new JSONArray().put(image)));
        expect(claude.request(request(images)).getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getJSONObject("source").getString("media_type").equals("image/png"));
        // Parallel tool results with images must remain contiguous before a synthetic user image message.
        JSONArray order=new JSONArray().put(obj("role","tool","tool_call_id","a","content","a")).put(obj("role","user","_tool_images",true,"content",new JSONArray().put(image))).put(obj("role","tool","tool_call_id","b","content","b")).put(obj("role","user","_tool_images",true,"content",new JSONArray().put(image))).put(obj("role","tool","tool_call_id","c","content","c"));
        JSONArray ordered=ProviderProtocol.orderToolImages(order);expect(ordered.length()==4);expect(ordered.getJSONObject(2).getString("role").equals("tool"));expect(ordered.getJSONObject(3).getJSONArray("content").length()==2);
        reject("unsupported",()->chat.request(request(new JSONArray().put(obj("type","compaction","encrypted_content","opaque")))));
        reject("unsupported",()->chat.request(request(new JSONArray().put(obj("type","reasoning","encrypted_content","opaque")))));
        reject("unsupported",()->new ProviderProtocol("chat","fixture",new JSONArray().put(obj("type","web_search"))));
        reject("protocol",()->chat.request(request(new JSONArray()).put("model","wrong-model")));
        Events events=new Events();ProviderProtocol.Stream stream=chat.new Stream(events);stream.start();
        stream.chat(delta(obj("content","hello","refusal",JSONObject.NULL,"reasoning_content",JSONObject.NULL),JSONObject.NULL));
        stream.chat(delta(toolDelta("call_1",alias,"{\"name\":"),JSONObject.NULL));
        expect(events.count("response.output_item.done")==0);
        stream.chat(delta(toolDelta("call_1","","\"battery\"}"),"tool_calls"));
        stream.chat(obj("choices",new JSONArray(),"usage",obj("prompt_tokens",12,"completion_tokens",4)));
        stream.finish();expect(stream.terminal);expect(events.count("response.completed")==1);expect(events.count("response.output_item.done")==2);
        JSONObject completed=events.events.get(events.events.size()-1).getJSONObject("response");
        expect(completed.getJSONObject("usage").getLong("total_tokens")==16);
        expect(completed.getJSONArray("output").getJSONObject(1).getString("name").equals("phone.action"));
        expect(completed.getJSONArray("output").getJSONObject(1).getString("arguments").equals("{\"name\":\"battery\"}"));
        // A truncated response must not publish a partially accumulated tool call.
        Events bad=new Events();ProviderProtocol.Stream truncated=chat.new Stream(bad);truncated.start();truncated.chat(delta(toolDelta("call_2",alias,"{\"name\":"),"length"));
        reject("truncated",truncated::finish);expect(bad.count("response.output_item.done")==0&&bad.count("response.completed")==0);
        ProviderProtocol.Stream absent=chat.new Stream(new Events());reject("protocol",absent::finish);
        ProviderProtocol.Stream unknown=chat.new Stream(new Events());unknown.chat(delta(toolDelta("call_3","not-a-tool","{}"),"tool_calls"));reject("protocol",unknown::finish);
        Events anthropic=new Events();ProviderProtocol.Stream as=claude.new Stream(anthropic);as.start();
        as.anthropic(obj("type","message_start","message",obj("usage",obj("input_tokens",10,"cache_read_input_tokens",3))));
        as.anthropic(obj("type","content_block_start","index",0,"content_block",obj("type","text","text","")));
        as.anthropic(obj("type","content_block_delta","index",0,"delta",obj("type","text_delta","text","你好")));
        as.anthropic(obj("type","content_block_stop","index",0));
        as.anthropic(obj("type","content_block_start","index",1,"content_block",obj("type","tool_use","id","toolu_1","name",alias,"input",new JSONObject())));
        as.anthropic(obj("type","content_block_delta","index",1,"delta",obj("type","input_json_delta","partial_json","{}")));
        as.anthropic(obj("type","content_block_stop","index",1));
        as.anthropic(obj("type","message_delta","delta",obj("stop_reason","tool_use"),"usage",obj("output_tokens",5)));
        expect(anthropic.count("response.output_item.done")==0);
        as.anthropic(obj("type","message_stop"));expect(as.terminal);expect(anthropic.count("response.completed")==1);
        JSONObject ar=anthropic.events.get(anthropic.events.size()-1).getJSONObject("response");expect(ar.getJSONObject("usage").getLong("input_tokens")==13);
        // Full SSE event framing, Unicode, CRLF, comments and clean/truncated EOF.
        expect(ProviderBridge.event(new StringReader("event: message\r\ndata: {\"text\":\"中文\"}\r\n\r\n")).contains("中文"));
        expect(ProviderBridge.event(new StringReader(": ping\n\n")).isEmpty());
        expect(ProviderBridge.event(new StringReader("data: first\ndata: second\n\n")).equals("first\nsecond"));
        expect(ProviderBridge.event(new StringReader(""))==null);
        reject("disconnected",()->ProviderBridge.event(new StringReader("data: {}\n")));
        // Namespaced tools round-trip without losing their namespace.
        JSONArray ns=new JSONArray().put(obj("type","namespace","name","device","tools",definitions()));
        ProviderProtocol namespaced=new ProviderProtocol("chat","fixture",ns);Events nsEvents=new Events();ProviderProtocol.Stream nsStream=namespaced.new Stream(nsEvents);
        nsStream.chat(delta(toolDelta("ns_call",namespaced.byAlias.keySet().iterator().next(),"{}"),"tool_calls"));nsStream.finish();
        JSONObject nsResponse=nsEvents.events.get(nsEvents.events.size()-1).getJSONObject("response");expect(nsResponse.getJSONArray("output").getJSONObject(0).getString("namespace").equals("device"));
    }
}
