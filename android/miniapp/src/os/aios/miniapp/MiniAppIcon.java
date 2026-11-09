package os.aios.miniapp;
import android.graphics.*;
import android.graphics.drawable.Icon;
public final class MiniAppIcon {
 public static Icon create(String id,String name){
  Bitmap b=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  int[] colors={0xff465d91,0xff256d61,0xff84526d,0xff785b27};c.drawColor(colors[Math.floorMod(id.hashCode(),colors.length)]);
  p.setColor(Color.WHITE);p.setTextSize(80);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);
  String letter=name.isEmpty()?"•":name.substring(0,name.offsetByCodePoints(0,1));c.drawText(letter,96,96-(p.ascent()+p.descent())/2,p);
  return Icon.createWithAdaptiveBitmap(b);
 }

}
