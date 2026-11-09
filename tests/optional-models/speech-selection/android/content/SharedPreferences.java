package android.content;
public final class SharedPreferences {private final java.util.Map<String,String> values=new java.util.HashMap<>();public String getString(String k,String d){return values.getOrDefault(k,d);}public Editor edit(){return new Editor();}public final class Editor{public Editor putString(String k,String v){values.put(k,v);return this;}public void apply(){}}}
