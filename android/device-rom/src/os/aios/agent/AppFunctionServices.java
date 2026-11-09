package os.aios.agent;
import android.app.appfunctions.*;
import android.app.appsearch.GenericDocument;
import android.content.Context;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** ROM AppFunction API, executed under the existing Agent identity. */
final class AppFunctionServices {
 static JSONObject execute(Context context,String op,JSONObject params)throws Exception {
  AppFunctionManager manager=context.getSystemService(AppFunctionManager.class);
  if(manager==null)return new JSONObject().put("state","not_supported").put("reason","App Functions unavailable");
  try{
   if(op.equals("apps.functions.discover")){
    CompletableFuture<List<AppFunctionMetadata>> result=new CompletableFuture<>();
    manager.searchAppFunctions(new AppFunctionSearchSpec.Builder().build(),Runnable::run,new OutcomeReceiver<List<AppFunctionMetadata>,Exception>(){
     public void onResult(List<AppFunctionMetadata> value){result.complete(value);}
     public void onError(Exception error){result.completeExceptionally(error);}
    });
    JSONArray functions=new JSONArray();List<AppFunctionMetadata> found=result.get(30,TimeUnit.SECONDS);
    for(AppFunctionMetadata item:found)functions.put(new JSONObject().put("package",item.getName().getPackageName()).put("function",item.getName().getFunctionIdentifier()).put("metadata",json(item.getMetadataDocument(),0)));
    return new JSONObject().put("state","succeeded").put("data",functions);
   }
   String pkg=params.getString("package"),function=params.getString("function");
   if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||function.isEmpty()||function.length()>500)throw new IllegalArgumentException("Invalid AppFunction target");
   JSONObject values=params.getJSONObject("parameters");if(values.toString().length()>64000)throw new IllegalArgumentException("AppFunction parameters too large");
   ExecuteAppFunctionRequest request=new ExecuteAppFunctionRequest.Builder(pkg,function).setParameters(document(values,0)).build();
   CompletableFuture<ExecuteAppFunctionResponse> result=new CompletableFuture<>();CancellationSignal signal=new CancellationSignal();
   manager.executeAppFunction(request,Runnable::run,signal,new OutcomeReceiver<ExecuteAppFunctionResponse,AppFunctionException>(){
    public void onResult(ExecuteAppFunctionResponse value){result.complete(value);}
    public void onError(AppFunctionException error){result.completeExceptionally(error);}
   });
   try{return new JSONObject().put("state","succeeded").put("data",json(result.get(30,TimeUnit.SECONDS).getResultDocument(),0));}
   catch(TimeoutException timeout){signal.cancel();return new JSONObject().put("state","outcome_unknown").put("reason","App Function timed out; cancellation requested, do not replay without checking target state");}
   catch(InterruptedException interrupted){signal.cancel();throw interrupted;}
  }catch(SecurityException denied){return new JSONObject().put("state","permission_required").put("reason","App Function access not granted");}
   catch(ExecutionException failure){Throwable cause=failure.getCause();
    if(cause instanceof SecurityException)return new JSONObject().put("state","permission_required");
    if(cause instanceof AppFunctionException){AppFunctionException error=(AppFunctionException)cause;int code=error.getErrorCode();
     String state=code==AppFunctionException.ERROR_DENIED?"permission_required":code==AppFunctionException.ERROR_FUNCTION_NOT_FOUND?"not_supported":code==AppFunctionException.ERROR_INVALID_ARGUMENT||code==AppFunctionException.ERROR_DISABLED||code==AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED?"failed":"outcome_unknown";
     return new JSONObject().put("state",state).put("error_code",code).put("reason",error.getErrorMessage());
    }
    throw failure;
   }
 }
 static GenericDocument document(JSONObject object,int depth)throws Exception {
  if(depth>16)throw new IllegalArgumentException("AppFunction parameters too deeply nested");
  GenericDocument.Builder<?> b=new GenericDocument.Builder<>(object.optString("namespace",""),object.optString("id",""),object.optString("schemaType",""));
  for(Iterator<String> it=object.keys();it.hasNext();){String key=it.next();Object raw=object.get(key);
   if(raw==JSONObject.NULL)throw new IllegalArgumentException("Null AppFunction property: "+key);
   JSONArray a=raw instanceof JSONArray?(JSONArray)raw:new JSONArray().put(raw);if(a.length()==0)continue;Object first=a.get(0);int n=a.length();
   if(first instanceof String){String[] v=new String[n];for(int i=0;i<n;i++){if(!(a.get(i) instanceof String))throw new IllegalArgumentException("Mixed property types");v[i]=(String)a.get(i);}b.setPropertyString(key,v);}
   else if(first instanceof Boolean){boolean[] v=new boolean[n];for(int i=0;i<n;i++){if(!(a.get(i) instanceof Boolean))throw new IllegalArgumentException("Mixed property types");v[i]=(Boolean)a.get(i);}b.setPropertyBoolean(key,v);}
   else if(first instanceof Integer||first instanceof Long){long[] v=new long[n];for(int i=0;i<n;i++){Object value=a.get(i);if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Mixed property types");v[i]=((Number)value).longValue();}b.setPropertyLong(key,v);}
   else if(first instanceof Number){double[] v=new double[n];for(int i=0;i<n;i++){if(!(a.get(i) instanceof Number))throw new IllegalArgumentException("Mixed property types");v[i]=((Number)a.get(i)).doubleValue();}b.setPropertyDouble(key,v);}
   else if(first instanceof JSONObject){GenericDocument[] v=new GenericDocument[n];for(int i=0;i<n;i++)v[i]=document(a.getJSONObject(i),depth+1);b.setPropertyDocument(key,v);}
   else throw new IllegalArgumentException("Unsupported AppFunction property type");
  }return b.build();
 }
 static JSONObject json(GenericDocument document,int depth)throws Exception {
  if(depth>16)throw new IllegalArgumentException("AppFunction result too deeply nested");JSONObject out=new JSONObject();
  for(String key:document.getPropertyNames()){Object value=document.getProperty(key);JSONArray a=new JSONArray();
   if(value!=null)for(int i=0;i<java.lang.reflect.Array.getLength(value);i++){Object item=java.lang.reflect.Array.get(value,i);a.put(item instanceof GenericDocument?json((GenericDocument)item,depth+1):item instanceof byte[]?android.util.Base64.encodeToString((byte[])item,android.util.Base64.NO_WRAP):item);}
   out.put(key,a);
  }return out;
 }
}
