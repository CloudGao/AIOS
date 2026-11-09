package os.aios.agent;
import java.util.*;
final class AssistantText {
 static String plain(String text){return text.replaceAll("(?s)```.*?```"," ").replaceAll("!?\\[([^\\]]+)\\]\\([^)]*\\)","$1").replaceAll("(?m)^\\s{0,3}(?:#{1,6}\\s+|[-*+]\\s+|>\\s*)","").replaceAll("[*_`]","").trim();}
 static String lastSentence(String text){String value=plain(text);if(value.isEmpty())return "回复已保存在 AIOS 会话中。";String[] parts=value.split("(?<=[。！？!?])\\s*|(?<=\\.)\\s+|\\n+");String suffix="";for(int i=parts.length-1;i>=0;i--){String part=parts[i].trim();if(part.isEmpty())continue;if(part.matches("[”’\"')）]+")){suffix=part+suffix;continue;}return part+suffix;}return value;}
 static String preview(String text,int limit){String value=plain(text);if(value.isEmpty())return "回复已保存在 AIOS 会话中。";if(value.length()<=limit)return value;int end=Math.max(1,limit-12);if(Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end).trim()+"…\n点击查看完整回复";}
 static List<String> chunks(String text,int limit){if(limit<2)throw new IllegalArgumentException("Chunk limit too small");String value=plain(text);List<String> out=new ArrayList<>();while(!value.isEmpty()){int end=Math.min(limit,value.length());if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1)))end--;out.add(value.substring(0,end));value=value.substring(end);}return out;}
}
