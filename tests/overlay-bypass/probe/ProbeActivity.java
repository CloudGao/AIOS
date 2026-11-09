package os.aios.overlayprobe;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
public class ProbeActivity extends Activity {
 int taps,longs,downs,moves,ups,cancels,device,flags; TextView status;
 void record(){
  String value="taps="+taps+" long="+longs+" down="+downs+" move="+moves+" up="+ups+" cancel="+cancels+" device="+device+" flags="+flags;
  status.setText(value);getSharedPreferences("probe",0).edit().putString("result",value).commit();
 }
 @Override public void onCreate(Bundle state){super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff00c8c8);
  status=new TextView(this);status.setTextSize(18);root.addView(status,new LinearLayout.LayoutParams(-1,160));
  EditText field=new EditText(this);field.setHint("Keyboard check");root.addView(field,new LinearLayout.LayoutParams(-1,160));
  TextView target=new TextView(this);target.setText("UNDERLYING TARGET");target.setTextSize(22);target.setGravity(Gravity.CENTER);target.setBackgroundColor(0xff00c8c8);target.setContentDescription("Underlying target");
  target.setOnClickListener(v->{taps++;record();});target.setOnLongClickListener(v->{longs++;record();return true;});
  target.setOnTouchListener((v,e)->{device=e.getDeviceId();flags=e.getFlags();switch(e.getActionMasked()){case 0:downs++;break;case 1:ups++;break;case 2:moves++;break;case 3:cancels++;break;}if(e.getActionMasked()!=2)record();return false;});
  root.addView(target,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);record();
 }
}