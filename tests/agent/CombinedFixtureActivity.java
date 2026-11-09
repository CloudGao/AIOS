package os.aios.combined.tests;
import android.app.Activity;import android.os.Bundle;import android.widget.*;
/** Isolated acceptance surface: no camera access, network, accounts or business writes. */
public final class CombinedFixtureActivity extends Activity {
 TextView value;int count;
 public void onCreate(Bundle b){super.onCreate(b);count=getPreferences(0).getInt("count",0);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(30,60,30,30);TextView title=new TextView(this);title.setText("AIOS 合并验收页面");title.setTextSize(24);root.addView(title);value=new TextView(this);value.setTextSize(24);root.addView(value);show();Button increment=new Button(this);increment.setText("计数加一");increment.setOnClickListener(v->{count++;getPreferences(0).edit().putInt("count",count).commit();show();});root.addView(increment);Button permission=new Button(this);permission.setText("请求相机权限（不使用相机）");permission.setOnClickListener(v->requestPermissions(new String[]{"android.permission.CAMERA"},10));root.addView(permission);setContentView(root);}
 void show(){value.setText("当前计数："+count);value.setContentDescription("当前计数 "+count);}
 public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==10){TextView result=new TextView(this);result.setText(grants.length>0&&grants[0]==0?"权限结果：允许":"权限结果：未允许");((LinearLayout)value.getParent()).addView(result);}}
}
