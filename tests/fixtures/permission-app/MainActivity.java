package os.aios.permissionfixture;
public class MainActivity extends android.app.Activity {
 android.widget.TextView result;
 public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView text=new android.widget.TextView(this);result=text;text.setText("AIOS 权限交接测试");setContentView(text);
 if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},1);
 }
 public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);result.setText(grants.length>0 && grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED?"权限结果：已允许":"权限结果：已拒绝");}
}
