package os.aios.agent;
import android.content.Context;
import org.json.*;

/** Ordinary-app policy; unsupported operations cannot fall through to ROM code. */
final class ApkPlatformServices extends SharedPlatformServices {
 ApkPlatformServices(Context context,os.aios.device.DeviceBackend.ApplicationLauncher launcher){super(context,launcher,DeviceContributions.create(context));LocalAdbActivation.startRecovery(context);}
 PackageServices.Backend packageBackend(String op,JSONObject args)throws Exception{return ApkPackageBackend.select(c,op,args);}
 @Override public JSONObject execute(String op,JSONObject args)throws Exception{
  if(AdbBridge.connected()){
   String epoch=AdbBridge.instance();
   if(java.util.Set.of("wifi.read","wifi.set","bluetooth.read","bluetooth.set","display.brightness.set").contains(op))return AdbBridge.call("system_operation",new JSONObject().put("instance",epoch).put("operation",op).put("arguments",args));
   if(op.equals("phone.call"))return open(new android.content.Intent(android.content.Intent.ACTION_CALL,android.net.Uri.fromParts("tel",args.getString("number"),null)),args);
   boolean absolute=new java.io.File(args.optString("name",args.optString("path",""))).isAbsolute();
   if(op.equals("files.access")||op.equals("files.list")||java.util.Set.of("files.stat","files.mkdir").contains(op)||(absolute&&java.util.Set.of("files.delete","files.rename").contains(op)))return AdbBridge.call("file_operation",new JSONObject().put("instance",epoch).put("operation",op).put("arguments",args));
   if((absolute&&java.util.Set.of("files.read","files.write").contains(op))||op.equals("files.read_bytes")){
    String path=args.getString(op.equals("files.read_bytes")?"path":"name");
    if(op.equals("files.write")){
     String text=args.getString("text");if(text.length()>16000)throw new IllegalArgumentException("text_too_large");byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
     try(android.os.ParcelFileDescriptor.AutoCloseOutputStream out=new android.os.ParcelFileDescriptor.AutoCloseOutputStream(AdbBridge.file(epoch,path,true))){out.write(bytes);out.getFD().sync();}
     return ok(new JSONObject().put("path",path).put("bytes",bytes.length)).put("backend","adb");
    }
    long offset=args.optLong("offset",0);int limit=op.equals("files.read")?65537:args.optInt("limit",65536);if(offset<0||limit<1||limit>262144)throw new IllegalArgumentException("invalid_byte_range");
    android.os.ParcelFileDescriptor descriptor=AdbBridge.file(epoch,path,false);long total=descriptor.getStatSize();
    try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
     in.getChannel().position(offset);byte[] bytes=in.readNBytes(limit);
     if(op.equals("files.read")){if(bytes.length>65536)throw new IllegalArgumentException("text_limit_use_read_bytes");return ok(new JSONObject().put("text",new String(bytes,java.nio.charset.StandardCharsets.UTF_8))).put("backend","adb");}
     return ok(new JSONObject().put("base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)).put("offset",offset).put("bytes",bytes.length).put("total_bytes",total).put("eof",offset+bytes.length>=total)).put("backend","adb");
    }
   }
  }
  return super.execute(op,args);
 }
 private JSONObject unavailable(String capability)throws Exception{
  return new JSONObject().put("state","not_supported").put("reason_code","unsupported_apk_operation").put("capability",capability).put("executed",false);
 }
 JSONObject appFunctions(String op,JSONObject args)throws Exception{return unavailable(op);}
 JSONObject radio(String op,JSONObject args)throws Exception{return unavailable(op);}
 JSONObject clock(String op,JSONObject args)throws Exception{return unavailable(op);}
 JSONObject diagnostic(String service)throws Exception{return AdbBridge.connected()?AdbBridge.call("diagnostic",new JSONObject().put("name",service)):SystemDiagnostics.read(c,service);}
 JSONObject reboot()throws Exception{return unavailable("power.reboot");}
 JSONObject recordMicrophone()throws Exception{return unavailable("microphone.record");}
 void appendHostCapabilities(JSONArray out)throws Exception{
  for(String cap:new String[]{"alarm.create","timer.create"})out.put(new JSONObject().put("capability",cap).put("available",false).put("reason_code","unsupported_apk_operation"));
 }
}
