package os.aios.agent;
public class AssistantTextTest {
 static int count;
 static void check(boolean value){if(!value)throw new AssertionError("case "+count);count++;}
 public static void main(String[] args){
  check(AssistantText.lastSentence("第一句。最后一句。").equals("最后一句。"));
  check(AssistantText.lastSentence("Hello. Last sentence!").equals("Last sentence!"));
  check(AssistantText.lastSentence("前面的话。\n**最后一句。**").equals("最后一句。"));
  check(AssistantText.lastSentence("回答。‘好的。’").equals("‘好的。’"));
  check(AssistantText.lastSentence("数值是 3.14。结果正确。").equals("结果正确。"));
  check(AssistantText.lastSentence("没有标点").equals("没有标点"));
  check(AssistantText.lastSentence("完成。\n```java\nsecretCode();\n```").equals("完成。"));
  check(AssistantText.plain("[链接](https://example.com)").equals("链接"));
  check(AssistantText.preview("完整回复",160).equals("完整回复"));
  check(AssistantText.preview("长回复".repeat(100),40).endsWith("点击查看完整回复"));
  String content="你好😀我是小助手。".repeat(500);java.util.List<String> parts=AssistantText.chunks(content,3000);
  check(String.join("",parts).equals(content));check(parts.stream().allMatch(s->s.length()<=3000));check(parts.stream().noneMatch(s->Character.isHighSurrogate(s.charAt(s.length()-1))));
  check(AssistantText.chunks("",3000).isEmpty());
  System.out.println("PASS: "+count+" sentence/TTS text assertions");
 }
}
