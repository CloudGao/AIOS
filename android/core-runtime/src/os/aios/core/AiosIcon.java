package os.aios.core;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Monochrome 24dp Material-style stroke symbols; no emoji/font-dependent rendering. */
public final class AiosIcon extends Drawable {
    private final String kind;private int color;private final int size;private final Paint p=new Paint(3);
    public AiosIcon(Context c,String kind){this(c,kind,AiosTheme.text(c));}
    public AiosIcon(Context c,String kind,int color){this.kind=kind;this.color=color;size=Math.round(24*c.getResources().getDisplayMetrics().density);setBounds(0,0,size,size);}
    public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
    public void setTint(int tint){color=tint;invalidateSelf();}
    public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        switch(kind){
            case "search":c.drawCircle(10,10,6,p);c.drawLine(15,15,21,21,p);break;
            case "add":c.drawLine(12,5,12,19,p);c.drawLine(5,12,19,12,p);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.6f,p);break;
            case "done":c.drawCircle(12,12,9,p);c.drawLine(7,12,10,15,p);c.drawLine(10,15,17,8,p);break;
            case "check":c.drawLine(5,12,10,17,p);c.drawLine(10,17,19,7,p);break;
            case "running":c.drawArc(3,3,21,21,-80,280,false,p);break;
            case "pending":c.drawCircle(12,12,9,p);c.drawLine(12,6,12,12,p);c.drawLine(12,12,16,14,p);break;
            case "pause":c.drawLine(9,6,9,18,p);c.drawLine(15,6,15,18,p);break;
            case "stop":p.setStyle(Paint.Style.FILL);c.drawRoundRect(6,6,18,18,2,2,p);break;
            case "play":Path tri=new Path();tri.moveTo(8,5);tri.lineTo(19,12);tri.lineTo(8,19);tri.close();c.drawPath(tri,p);break;
            case "keyboard":c.drawRoundRect(2,5,22,19,3,3,p);for(int y=9;y<=12;y+=3)for(int x=6;x<=18;x+=4)c.drawPoint(x,y,p);c.drawLine(7,16,17,16,p);break;
            case "mic":c.drawRoundRect(9,2,15,15,3,3,p);c.drawArc(6,6,18,19,0,180,false,p);c.drawLine(12,19,12,22,p);break;
            case "send":c.drawLine(12,20,12,4,p);c.drawLine(6,10,12,4,p);c.drawLine(12,4,18,10,p);break;
            case "collapse":c.drawLine(6,9,12,15,p);c.drawLine(12,15,18,9,p);break;
            case "expand":c.drawLine(6,15,12,9,p);c.drawLine(12,9,18,15,p);break;
            case "chevron":c.drawLine(9,6,15,12,p);c.drawLine(15,12,9,18,p);break;
            case "back":c.drawLine(19,12,5,12,p);c.drawLine(11,6,5,12,p);c.drawLine(5,12,11,18,p);break;
            case "close":c.drawLine(6,6,18,18,p);c.drawLine(6,18,18,6,p);break;
            case "edit":c.drawLine(4,20,8,19,p);c.drawLine(8,19,19,8,p);c.drawLine(19,8,16,5,p);c.drawLine(16,5,5,16,p);c.drawLine(5,16,4,20,p);break;
            case "delete":c.drawLine(5,7,19,7,p);c.drawLine(9,7,9,4,p);c.drawLine(9,4,15,4,p);c.drawLine(15,4,15,7,p);c.drawRoundRect(7,7,17,20,1.5f,1.5f,p);break;
            case "refresh":c.drawArc(4,4,20,20,-60,300,false,p);c.drawLine(20,4,20,9,p);c.drawLine(20,9,15,9,p);break;
            case "history":c.drawArc(4,4,20,20,-45,315,false,p);c.drawLine(4,4,4,10,p);c.drawLine(4,10,10,10,p);c.drawLine(12,8,12,12,p);c.drawLine(12,12,15,14,p);break;
            case "share":c.drawLine(12,16,12,3,p);c.drawLine(8,7,12,3,p);c.drawLine(12,3,16,7,p);c.drawLine(5,11,5,20,p);c.drawLine(5,20,19,20,p);c.drawLine(19,20,19,11,p);break;
            case "model":c.drawRoundRect(4,7,20,20,3,3,p);c.drawCircle(9,13,1.4f,p);c.drawCircle(15,13,1.4f,p);c.drawLine(12,7,12,3,p);c.drawCircle(12,3,.6f,p);c.drawLine(8,17,16,17,p);break;
            case "account":c.drawCircle(12,8,4,p);c.drawArc(4,13,20,29,180,180,false,p);break;
            case "tune":for(int y=6;y<=18;y+=6)c.drawLine(4,y,20,y,p);p.setStyle(Paint.Style.FILL);c.drawCircle(9,6,2,p);c.drawCircle(15,12,2,p);c.drawCircle(8,18,2,p);break;
            case "moon":Path m=new Path();m.moveTo(14,3);m.cubicTo(8,4,5,9,6,14);m.cubicTo(7,19,13,22,18,19);m.cubicTo(13,19,9,14,10,9);m.cubicTo(10.5f,6.5f,12,4.5f,14,3);m.close();c.drawPath(m,p);break;
            case "book":c.drawRoundRect(4,3,20,21,2,2,p);c.drawLine(8,3,8,21,p);c.drawLine(12,8,16,8,p);c.drawLine(12,12,16,12,p);break;
            case "memory":c.drawRoundRect(5,5,19,19,2,2,p);c.drawRoundRect(9,9,15,15,1,1,p);for(int i=8;i<=16;i+=4){c.drawLine(i,2,i,5,p);c.drawLine(i,19,i,22,p);c.drawLine(2,i,5,i,p);c.drawLine(19,i,22,i,p);}break;
            case "assistant":c.drawCircle(12,12,8,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,3,p);break;
            case "device":c.drawRoundRect(6,2,18,22,2.5f,2.5f,p);c.drawLine(10,19,14,19,p);break;
            case "shield":Path s=new Path();s.moveTo(12,3);s.lineTo(19,6);s.lineTo(19,12);s.cubicTo(19,16.5f,16,20,12,21);s.cubicTo(8,20,5,16.5f,5,12);s.lineTo(5,6);s.close();c.drawPath(s,p);c.drawLine(9,12,11,14,p);c.drawLine(11,14,15,9,p);break;
            case "touch":c.drawLine(10,12,10,5,p);c.drawArc(8,3,12,7,180,180,false,p);c.drawLine(12,5,12,13,p);c.drawLine(10,12,10,20,p);c.drawRoundRect(6,11,18,21,4,4,p);break;
            case "screen":c.drawRoundRect(3,4,21,17,2,2,p);c.drawLine(8,21,16,21,p);c.drawLine(12,17,12,21,p);break;
            case "filter":c.drawLine(3,6,21,6,p);c.drawLine(6,12,18,12,p);c.drawLine(10,18,14,18,p);break;
            case "pin":c.drawLine(12,15,12,21,p);c.drawLine(7,15,17,15,p);c.drawLine(9,15,9.5f,9,p);c.drawLine(15,15,14.5f,9,p);c.drawLine(8,9,16,9,p);c.drawLine(9,3,15,3,p);c.drawLine(9.5f,3,9.5f,9,p);c.drawLine(14.5f,3,14.5f,9,p);break;
            case "block":c.drawCircle(12,12,8.5f,p);c.drawLine(6,6,18,18,p);break;
            case "notification":case "bell":c.drawLine(6,17,18,17,p);c.drawLine(6,17,7,10,p);c.drawArc(7,4,17,16,180,180,false,p);c.drawLine(17,10,18,17,p);c.drawLine(10,20,14,20,p);break;
            case "clock":c.drawCircle(12,12,9,p);c.drawLine(12,7,12,12,p);c.drawLine(12,12,16,14,p);break;
            case "bolt":Path b=new Path();b.moveTo(13,2);b.lineTo(5,14);b.lineTo(11,14);b.lineTo(10,22);b.lineTo(19,10);b.lineTo(13,10);b.close();c.drawPath(b,p);break;
            case "tasks":c.drawLine(4,6,6,8,p);c.drawLine(6,8,9,4,p);c.drawLine(12,6,20,6,p);c.drawLine(4,13,6,15,p);c.drawLine(6,15,9,11,p);c.drawLine(12,13,20,13,p);c.drawLine(12,19,20,19,p);c.drawCircle(6,19,1,p);break;
            case "chat":Path ch=new Path();ch.moveTo(4,4);ch.lineTo(20,4);ch.lineTo(20,16);ch.lineTo(9,16);ch.lineTo(4,21);ch.close();c.drawPath(ch,p);break;
            case "automation":c.drawCircle(12,12,3,p);for(int i=0;i<8;i++){double a=Math.toRadians(i*45);c.drawLine((float)(12+Math.cos(a)*6.5),(float)(12+Math.sin(a)*6.5),(float)(12+Math.cos(a)*9.5),(float)(12+Math.sin(a)*9.5),p);}c.drawCircle(12,12,6.5f,p);break;
            case "skills":Path sp=new Path();sp.moveTo(12,2);for(int i=1;i<10;i++){double a=Math.toRadians(-90+i*36);float r=i%2==0?10:4.5f;sp.lineTo((float)(12+Math.cos(a)*r),(float)(12+Math.sin(a)*r));}sp.close();c.drawPath(sp,p);break;
            case "settings":c.drawCircle(12,12,3,p);Path g=new Path();for(int i=0;i<16;i++){double a=Math.toRadians(i*22.5);float r=i%2==0?9.5f:7.5f;float x=(float)(12+Math.cos(a)*r),y=(float)(12+Math.sin(a)*r);if(i==0)g.moveTo(x,y);else g.lineTo(x,y);}g.close();c.drawPath(g,p);break;
            case "camera":c.drawRoundRect(3,6,21,20,3,3,p);c.drawCircle(12,13,4,p);c.drawLine(8,6,9,3,p);c.drawLine(9,3,15,3,p);c.drawLine(15,3,16,6,p);break;
            case "image":c.drawRoundRect(3,5,21,19,2,2,p);c.drawCircle(8.5f,10,1.5f,p);c.drawLine(4,18,10,12,p);c.drawLine(10,12,14,16,p);c.drawLine(14,16,17,13,p);c.drawLine(17,13,20,16,p);break;
            case "folder":c.drawLine(3,7,3,19,p);c.drawLine(3,19,21,19,p);c.drawLine(21,19,21,9,p);c.drawLine(21,9,12,9,p);c.drawLine(12,9,10,6,p);c.drawLine(10,6,3,6,p);c.drawLine(3,6,3,7,p);break;
            case "apps":for(int x=5;x<=15;x+=10)for(int y=5;y<=15;y+=10)c.drawRoundRect(x,y,x+4.5f,y+4.5f,1,1,p);break;
            case "web":c.drawCircle(12,12,9,p);c.drawLine(3,12,21,12,p);c.drawOval(8,3,16,21,p);break;
            case "info":c.drawCircle(12,12,9,p);c.drawLine(12,11,12,16,p);c.drawPoint(12,8,p);break;
            case "warning":Path w=new Path();w.moveTo(12,3);w.lineTo(22,20);w.lineTo(2,20);w.close();c.drawPath(w,p);c.drawLine(12,9,12,14,p);c.drawPoint(12,17,p);break;
            case "muted":case "volume":Path shape=new Path();shape.moveTo(3,9);shape.lineTo(7,9);shape.lineTo(12,5);shape.lineTo(12,19);shape.lineTo(7,15);shape.lineTo(3,15);shape.close();c.drawPath(shape,p);if(kind.equals("muted")){c.drawLine(16,9,21,15,p);c.drawLine(16,15,21,9,p);}else c.drawArc(9,5,21,19,-55,110,false,p);break;
            default:c.drawCircle(12,12,8,p);
        }c.restoreToCount(save);
    }
    public void setAlpha(int alpha){p.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
