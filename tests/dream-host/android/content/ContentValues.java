package android.content;
public class ContentValues extends java.util.LinkedHashMap<String,Object>{ public String getAsString(String key){Object v=get(key);return v==null?null:v.toString();}public int size(){return super.size();}}
