package os.aios.agent;

/** Wire-name compatibility only. Never invent or renew an observation credential. */
final class UiObservation {
 static String supplied(String id,String token,String legacy){
  String selected="";
  for(String value:new String[]{id,token,legacy}){
   if(value==null||value.isEmpty())continue;
   if(!selected.isEmpty()&&!selected.equals(value))return "";
   selected=value;
  }
  return selected;
 }
 private UiObservation(){}
}
