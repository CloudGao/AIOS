package os.aios.agent;
import android.app.*;import android.os.*;import android.content.*;import android.net.Uri;import android.graphics.*;import java.io.*;import java.util.*;
/** Dedicated source UID for real Android URI-grant and share-sheet acceptance. */
public final class ShareFixtureActivity extends Activity {
 public void onCreate(Bundle state){super.onCreate(state);try{
  String kind=getIntent().getStringExtra("kind");if(kind==null)kind="text";
  Intent send=new Intent(Intent.ACTION_SEND).setComponent(new ComponentName("os.aios.agent","os.aios.agent.ShareToAssistantActivity")).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"AIOS acceptance shared note: buy milk and review the meeting notes tomorrow.");
  if(kind.equals("denied")){send.setType("image/png").putExtra(Intent.EXTRA_STREAM,Uri.parse("content://sms/1"));}
  else if(!kind.equals("text")){
   String name=kind.equals("video")?"probe.mp4":"probe.png";File file=new File(getCacheDir(),name);
   if(kind.equals("video")){try(InputStream in=getAssets().open("probe.mp4");OutputStream out=new FileOutputStream(file)){in.transferTo(out);}}
   else{Bitmap bitmap=Bitmap.createBitmap(480,320,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint paint=new Paint();paint.setColor(Color.BLUE);paint.setTextSize(35);canvas.drawText("AIOS shared image",32,160,paint);try(OutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
   Uri uri=Uri.parse("content://os.aios.dynamic.acceptance.files/"+name);send.setType(kind.equals("video")?"video/mp4":"image/png").putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(ClipData.newRawUri("acceptance",uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }
  startActivity(send);finish();
 }catch(Exception e){android.widget.TextView text=new android.widget.TextView(this);text.setText(e.toString());setContentView(text);}}
}
