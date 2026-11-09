package os.aios.agent;
import android.app.Instrumentation;import android.os.*;import android.content.Intent;import android.view.accessibility.AccessibilityNodeInfo;import java.util.*;
public class SkillUiAcceptance extends Instrumentation {
 int checks; StringBuilder log=new StringBuilder();
 public void onCreate(Bundle b){super.onCreate(b);start();}
 List<AccessibilityNodeInfo> nodes(){List<AccessibilityNodeInfo> out=new ArrayList<>();walk(getUiAutomation().getRootInActiveWindow(),out);return out;}
 void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out){if(n==null)return;out.add(n);for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),out);}
 AccessibilityNodeInfo find(String text)throws Exception{for(int k=0;k<30;k++){for(AccessibilityNodeInfo n:nodes())if(text.contentEquals(n.getText()==null?"":n.getText())||text.contentEquals(n.getContentDescription()==null?"":n.getContentDescription()))return n;Thread.sleep(200);}throw new AssertionError("Missing UI: "+text);}
 void click(String text)throws Exception{AccessibilityNodeInfo n=find(text);while(n!=null&&!n.isClickable())n=n.getParent();if(n==null||!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("click "+text);Thread.sleep(350);}
 void fill(String... values)throws Exception{List<AccessibilityNodeInfo> edits=new ArrayList<>();for(AccessibilityNodeInfo n:nodes())if("android.widget.EditText".contentEquals(n.getClassName()))edits.add(n);if(edits.size()!=values.length)throw new AssertionError("fields "+edits.size());for(int i=0;i<values.length;i++){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,values[i]);if(!edits.get(i).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b))throw new AssertionError("set text");}Thread.sleep(200);}
 void check(String value)throws Exception{find(value);checks++;log.append("PASS ").append(value).append("\n");}
 public void onStart(){Bundle result=new Bundle();try{
 getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("workspace_tab","skills"));Thread.sleep(800);
 click("＋ 添加技能");fill("验收临时技能", "验收中文完整输入", "读取电量后核对返回值；不修改设置。");click("保存");
 fill("验收临时技能");click("搜索");check("验收临时技能");click("查看方法");click("编辑");
 fill("验收临时技能已编辑", "验收修改", "仅查询电量，不执行其他操作。");click("保存");fill("验收临时技能已编辑");click("搜索");check("验收临时技能已编辑");
 click("查看方法");click("管理");click("停用");check("手动添加 · 已停用");
 click("查看方法");click("管理");click("启用");check("手动添加 · 已启用");
 click("查看方法");click("管理");click("删除");click("删除");
 result.putString("stream","PASS: "+checks+" UI assertions; create/search/edit/disable/enable/delete completed\n"+log);finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+log+android.util.Log.getStackTraceString(e));finish(0,result);}}
}