package android.content;
public final class ComponentName {
 private final String pkg,name; public ComponentName(String p,String n){pkg=p;name=n;}
 public String getPackageName(){return pkg;}public String flattenToString(){return pkg+"/"+name;}
 public static ComponentName unflattenFromString(String s){int i=s.indexOf('/');return i<=0||i==s.length()-1?null:new ComponentName(s.substring(0,i),s.substring(i+1));}
 public boolean equals(Object o){return o instanceof ComponentName&&flattenToString().equals(((ComponentName)o).flattenToString());}public int hashCode(){return flattenToString().hashCode();}
}
