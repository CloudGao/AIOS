package os.aios.agent;
final class SystemDiagnostics {
 static org.json.JSONObject read(android.content.Context c,String service)throws Exception{return AdbBridge.call("diagnostic",new org.json.JSONObject().put("name",service));}
}
