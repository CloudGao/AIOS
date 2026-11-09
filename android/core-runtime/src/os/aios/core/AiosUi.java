package os.aios.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/**
 * Programmatic Material 3 / LineageOS-Settings style widgets shared by every AIOS surface.
 * Everything reads AiosTheme at build time so Activities follow the system day/night mode by
 * being recreated, and long-lived overlays rebuild in onConfigurationChanged.
 *
 * Tokens: page padding 16dp, grouped list outer 28dp / inner 4dp with 2dp gaps, rows ≥72dp,
 * fields/FAB 16dp, chips 8dp, icon buttons 44–48dp, title 28sp, row 16sp, secondary 14sp, meta 12sp.
 */
public final class AiosUi {
    private AiosUi() {}
    public static final int MATCH=ViewGroup.LayoutParams.MATCH_PARENT,WRAP=ViewGroup.LayoutParams.WRAP_CONTENT;
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    public static LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,WRAP,1);}

    // ---- Drawables -------------------------------------------------------------------------
    public static GradientDrawable shape(Context c,int color,float tl,float tr,float br,float bl){
        GradientDrawable d=new GradientDrawable();d.setColor(color);float k=c.getResources().getDisplayMetrics().density;
        d.setCornerRadii(new float[]{tl*k,tl*k,tr*k,tr*k,br*k,br*k,bl*k,bl*k});return d;
    }
    public static GradientDrawable shape(Context c,int color,float radius){return AiosTheme.shape(c,color,radius);}
    public static GradientDrawable outlined(Context c,int fill,int stroke,float radius){GradientDrawable d=shape(c,fill,radius);d.setStroke(dp(c,1),stroke);return d;}
    public static RippleDrawable ripple(Context c,Drawable content){return new RippleDrawable(ColorStateList.valueOf(AiosTheme.ripple(c)),content,content==null?shape(c,0xff000000,0):null);}
    public static RippleDrawable ripple(Context c,Drawable content,float radius){return new RippleDrawable(ColorStateList.valueOf(AiosTheme.ripple(c)),content,shape(c,0xff000000,radius));}
    /** Row position inside a grouped list: 28dp on the outer edge, 4dp towards neighbours. */
    public static GradientDrawable groupShape(Context c,int color,boolean first,boolean last){
        float t=first?28:4,b=last?28:4;return shape(c,color,t,t,b,b);
    }
    public static void clickable(View v,Runnable action){
        v.setClickable(true);v.setFocusable(true);v.setOnClickListener(w->action.run());
    }

    // ---- Layout primitives -----------------------------------------------------------------
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static View space(Context c,int size){View v=new View(c);v.setLayoutParams(lp(MATCH,dp(c,size)));return v;}
    public static void gap(LinearLayout parent,int size){parent.addView(space(parent.getContext(),size));}
    public static View divider(Context c){View v=new View(c);v.setBackgroundColor(AiosTheme.outline(c));v.setLayoutParams(lp(MATCH,Math.max(1,dp(c,1))));return v;}

    // ---- Text -------------------------------------------------------------------------------
    public static TextView text(Context c,CharSequence value,float sp,int color){
        TextView t=os.aios.core.DisplayText.bind(new TextView(c));t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setLineSpacing(0,1.15f);return t;
    }
    public static TextView title(Context c,CharSequence value){TextView t=text(c,value,28,AiosTheme.text(c));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    public static TextView heading(Context c,CharSequence value){TextView t=text(c,value,22,AiosTheme.text(c));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    public static TextView body(Context c,CharSequence value){return text(c,value,16,AiosTheme.text(c));}
    public static TextView secondary(Context c,CharSequence value){return text(c,value,14,AiosTheme.muted(c));}
    public static TextView meta(Context c,CharSequence value){return text(c,value,12,AiosTheme.muted(c));}
    /** Section label above a group, accent coloured like Settings category headers. */
    public static TextView label(Context c,CharSequence value){
        TextView t=text(c,value,14,AiosTheme.accent(c));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setPadding(dp(c,16),dp(c,20),dp(c,16),dp(c,8));return t;
    }
    public static TextView selectable(Context c,CharSequence value,float sp,int color){TextView t=text(c,value,sp,color);t.setTextIsSelectable(true);return t;}
    public static TextView oneLine(TextView t){t.setMaxLines(1);t.setEllipsize(TextUtils.TruncateAt.END);return t;}
    public static TextView lines(TextView t,int n){t.setMaxLines(n);t.setEllipsize(TextUtils.TruncateAt.END);return t;}

    // ---- App bar ---------------------------------------------------------------------------
    /** Large-title app bar: optional back, title, trailing icon actions. */
    public static LinearLayout appBar(Context c,CharSequence title,Runnable back,View... actions){
        LinearLayout bar=column(c);bar.setPadding(dp(c,4),dp(c,8),dp(c,4),dp(c,8));
        LinearLayout top=row(c);top.setMinimumHeight(dp(c,56));
        if(back!=null)top.addView(iconButton(c,"back",c.getString(c.getResources().getIdentifier("aios_nav_back","string",c.getPackageName())),back));else top.addView(space(c,0),lp(dp(c,12),1));
        top.addView(new View(c),weight());
        for(View a:actions)if(a!=null)top.addView(a);
        bar.addView(top);
        TextView t=title(c,title);t.setPadding(dp(c,12),dp(c,back==null?0:4),dp(c,12),dp(c,8));bar.addView(t);
        return bar;
    }
    /** Compact app bar: back + single-line title + actions, for detail pages. */
    public static LinearLayout toolbar(Context c,CharSequence title,Runnable back,View... actions){
        LinearLayout top=row(c);top.setMinimumHeight(dp(c,64));top.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));
        if(back!=null)top.addView(iconButton(c,"back",c.getString(c.getResources().getIdentifier("aios_nav_back","string",c.getPackageName())),back));
        TextView t=heading(c,title);t.setPadding(dp(c,back==null?12:4),0,dp(c,8),0);oneLine(t);top.addView(t,weight());
        for(View a:actions)if(a!=null)top.addView(a);
        return top;
    }

    // ---- Icons & icon buttons --------------------------------------------------------------
    public static ImageView icon(Context c,String kind,int color){ImageView v=new ImageView(c);v.setImageDrawable(new AiosIcon(c,kind,color));return v;}
    public static ImageView iconButton(Context c,String kind,String description,Runnable action){return iconButton(c,kind,description,AiosTheme.muted(c),action);}
    public static ImageView iconButton(Context c,String kind,String description,int color,Runnable action){
        ImageView v=icon(c,kind,color);v.setContentDescription(os.aios.core.DisplayText.clean(description));int s=dp(c,48);
        v.setLayoutParams(lp(s,s));v.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
        v.setBackground(ripple(c,null,24));clickable(v,action);return v;
    }
    /** Filled tonal icon button (secondary_container) used on composer fields and sheet headers. */
    public static ImageView tonalIconButton(Context c,String kind,String description,Runnable action){
        ImageView v=icon(c,kind,AiosTheme.onTonal(c));v.setContentDescription(os.aios.core.DisplayText.clean(description));int s=dp(c,44);
        v.setLayoutParams(lp(s,s));v.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));
        v.setBackground(ripple(c,shape(c,AiosTheme.tonal(c),22)));clickable(v,action);return v;
    }
    public static ImageView filledIconButton(Context c,String kind,String description,int fill,int tint,Runnable action){
        ImageView v=icon(c,kind,tint);v.setContentDescription(os.aios.core.DisplayText.clean(description));int s=dp(c,44);
        v.setLayoutParams(lp(s,s));v.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));
        v.setBackground(ripple(c,shape(c,fill,22)));clickable(v,action);return v;
    }
    /** 8dp status dot: accent=running, warn=paused, outline=stopped/done. */
    public static View dot(Context c,int color){View v=new View(c);v.setBackground(shape(c,color,4));v.setLayoutParams(lp(dp(c,8),dp(c,8)));return v;}
    public static int statusColor(Context c,String status){
        switch(status==null?"":status){
            case "doing":case "running":case "preparing":case "pausing":case "cancelling":case "reconciling":return AiosTheme.accent(c);
            case "paused":case "waiting_user":case "waiting_resource":case "queued":case "outcome_unknown":return AiosTheme.warn(c);
            case "failed":return AiosTheme.error(c);
            default:return AiosTheme.outlineStrong(c);
        }
    }

    // ---- Buttons ---------------------------------------------------------------------------
    private static TextView baseButton(Context c,CharSequence label,int textColor,Drawable bg,Runnable action){
        TextView b=text(c,label,14,textColor);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,40));b.setMinWidth(dp(c,64));b.setPadding(dp(c,24),0,dp(c,24),0);
        b.setBackground(bg);oneLine(b);if(action!=null)clickable(b,action);return b;
    }
    /** Filled (primary) button for the single most important action. */
    public static TextView filledButton(Context c,CharSequence label,Runnable action){return baseButton(c,label,AiosTheme.onAccent(c),ripple(c,shape(c,AiosTheme.accent(c),20)),action);}
    /** Tonal button (secondary_container) for secondary actions. */
    public static TextView tonalButton(Context c,CharSequence label,Runnable action){return baseButton(c,label,AiosTheme.onTonal(c),ripple(c,shape(c,AiosTheme.tonal(c),20)),action);}
    /** Outlined button. */
    public static TextView outlinedButton(Context c,CharSequence label,Runnable action){return baseButton(c,label,AiosTheme.accent(c),ripple(c,outlined(c,0,AiosTheme.outlineStrong(c),20)),action);}
    /** Text button (no container). */
    public static TextView textButton(Context c,CharSequence label,Runnable action){TextView b=baseButton(c,label,AiosTheme.accent(c),ripple(c,null,20),action);b.setPadding(dp(c,12),0,dp(c,12),0);return b;}
    /** Destructive button on error_container. */
    public static TextView dangerButton(Context c,CharSequence label,Runnable action){return baseButton(c,label,AiosTheme.onErrorContainer(c),ripple(c,shape(c,AiosTheme.errorContainer(c),20)),action);}
    /** Extended FAB (primary_container, 16dp) with leading icon. */
    public static LinearLayout fab(Context c,String iconKind,CharSequence label,Runnable action){
        LinearLayout f=row(c);f.setPadding(dp(c,16),0,dp(c,20),0);f.setMinimumHeight(dp(c,56));
        f.setBackground(ripple(c,shape(c,AiosTheme.selected(c),16)));f.setElevation(dp(c,3));
        f.addView(icon(c,iconKind,AiosTheme.onSelected(c)));
        TextView t=text(c,label,14,AiosTheme.onSelected(c));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setPadding(dp(c,12),0,0,0);f.addView(t);
        clickable(f,action);return f;
    }
    /** Right-aligned button row with 8dp gaps; nulls skipped. */
    public static LinearLayout actions(Context c,View... buttons){
        LinearLayout r=row(c);r.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);r.setPadding(0,dp(c,8),0,0);
        for(View b:buttons)if(b!=null){LinearLayout.LayoutParams p=lp(WRAP,WRAP);p.leftMargin=dp(c,8);r.addView(b,p);}
        return r;
    }

    // ---- Chips -----------------------------------------------------------------------------
    /** Filter chip. Selected = secondary_container with check, otherwise outlined. */
    public static TextView chip(Context c,CharSequence label,boolean selected,Runnable action){
        TextView t=text(c,label,14,selected?AiosTheme.onTonal(c):AiosTheme.muted(c));
        t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setGravity(Gravity.CENTER);t.setMinHeight(dp(c,32));
        t.setPadding(dp(c,selected?8:16),0,dp(c,16),0);
        t.setBackground(ripple(c,selected?shape(c,AiosTheme.tonal(c),8):outlined(c,0,AiosTheme.outlineStrong(c),8)));
        if(selected){AiosIcon check=new AiosIcon(c,"check",AiosTheme.onTonal(c));int s=dp(c,18);check.setBounds(0,0,s,s);t.setCompoundDrawables(check,null,null,null);t.setCompoundDrawablePadding(dp(c,6));}
        if(action!=null)clickable(t,action);return t;
    }
    /** Assist chip with leading icon, non-selectable look (e.g. “虚拟屏 1 · 后台”). */
    public static TextView assistChip(Context c,String iconKind,CharSequence label,Runnable action){
        TextView t=text(c,label,14,AiosTheme.text(c));t.setGravity(Gravity.CENTER);t.setMinHeight(dp(c,32));t.setPadding(dp(c,8),0,dp(c,16),0);
        t.setBackground(ripple(c,outlined(c,0,AiosTheme.outlineStrong(c),8)));
        if(iconKind!=null){AiosIcon i=new AiosIcon(c,iconKind,AiosTheme.accent(c));int s=dp(c,18);i.setBounds(0,0,s,s);t.setCompoundDrawables(i,null,null,null);t.setCompoundDrawablePadding(dp(c,6));}
        if(action!=null)clickable(t,action);return t;
    }
    /** Horizontally scrolling chip strip with 8dp gaps and 16dp page padding. */
    public static HorizontalScrollView chipStrip(Context c,View... chips){
        HorizontalScrollView s=new HorizontalScrollView(c);s.setHorizontalScrollBarEnabled(false);s.setClipToPadding(false);
        LinearLayout r=row(c);r.setPadding(dp(c,16),dp(c,4),dp(c,16),dp(c,4));
        for(View v:chips)if(v!=null){LinearLayout.LayoutParams p=lp(WRAP,WRAP);p.rightMargin=dp(c,8);r.addView(v,p);}
        s.addView(r);return s;
    }
    /** Small tinted status tag (e.g. 运行中) using the dot colour on its container. */
    public static TextView tag(Context c,CharSequence label,int color){
        TextView t=text(c,label,12,color);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setPadding(dp(c,8),dp(c,2),dp(c,8),dp(c,2));t.setBackground(shape(c,(color&0x00ffffff)|0x24000000,6));return t;
    }
    public static TextView badge(Context c,int count){
        TextView t=text(c,String.valueOf(count),12,AiosTheme.onAccent(c));t.setGravity(Gravity.CENTER);t.setMinWidth(dp(c,20));t.setMinHeight(dp(c,20));
        t.setPadding(dp(c,6),0,dp(c,6),0);t.setBackground(shape(c,AiosTheme.accent(c),10));return t;
    }

    // ---- Switch ----------------------------------------------------------------------------
    /** Material 3 switch drawn with the system palette (52×32 track, 16/24 thumb). */
    public static Switch m3switch(Context c,boolean checked,java.util.function.Consumer<Boolean> change){
        Switch s=os.aios.core.DisplayText.bind(new Switch(c));s.setShowText(false);s.setText(null);
        s.setSwitchMinWidth(dp(c,52));s.setMinHeight(dp(c,32));s.setThumbTextPadding(0);
        int on=AiosTheme.accent(c),onThumb=AiosTheme.onAccent(c),off=AiosTheme.surfaceHigh(c),offThumb=AiosTheme.outlineStrong(c),outline=AiosTheme.outlineStrong(c);
        android.graphics.drawable.StateListDrawable track=new android.graphics.drawable.StateListDrawable();
        GradientDrawable tOn=shape(c,on,16);tOn.setSize(dp(c,52),dp(c,32));
        GradientDrawable tOff=outlined(c,off,outline,16);tOff.setSize(dp(c,52),dp(c,32));
        track.addState(new int[]{android.R.attr.state_checked},tOn);track.addState(new int[]{},tOff);
        android.graphics.drawable.StateListDrawable thumb=new android.graphics.drawable.StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked},thumbDrawable(c,onThumb,24));
        thumb.addState(new int[]{},thumbDrawable(c,offThumb,16));
        s.setTrackDrawable(track);s.setThumbDrawable(thumb);s.setSplitTrack(false);
        s.setChecked(checked);if(change!=null)s.setOnCheckedChangeListener((b,v)->{if(change!=null)change.accept(v);});return s;
    }
    private static Drawable thumbDrawable(Context c,int color,int diameter){
        GradientDrawable dotShape=shape(c,color,diameter/2f);dotShape.setSize(dp(c,diameter),dp(c,diameter));
        LayerDrawable l=new LayerDrawable(new Drawable[]{dotShape});int inset=dp(c,(32-diameter)/2f);l.setLayerInset(0,inset,inset,inset,inset);
        l.setLayerSize(0,dp(c,diameter),dp(c,diameter));return l;
    }

    // ---- Grouped list rows -----------------------------------------------------------------
    /**
     * Settings-style row: optional leading icon, title + one-line summary, trailing widget
     * (chevron, switch, badge). Background is grouped (28/4dp) and ripples.
     */
    public static LinearLayout settingRow(Context c,String iconKind,CharSequence title,CharSequence summary,View trailing,boolean first,boolean last,Runnable action){
        LinearLayout r=row(c);r.setMinimumHeight(dp(c,72));r.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        r.setBackground(action==null?groupShape(c,AiosTheme.surface(c),first,last):ripple(c,groupShape(c,AiosTheme.surface(c),first,last)));
        LinearLayout.LayoutParams p=lp(MATCH,WRAP);p.bottomMargin=last?0:dp(c,2);r.setLayoutParams(p);
        if(iconKind!=null){ImageView i=icon(c,iconKind,AiosTheme.accent(c));LinearLayout.LayoutParams ip=lp(dp(c,24),dp(c,24));ip.rightMargin=dp(c,16);r.addView(i,ip);}
        LinearLayout texts=column(c);texts.addView(lines(body(c,title),2));
        if(summary!=null&&summary.length()>0){TextView s=oneLine(secondary(c,summary));s.setPadding(0,dp(c,2),0,0);texts.addView(s);}
        r.addView(texts,weight());
        if(trailing!=null){LinearLayout.LayoutParams tp=lp(WRAP,WRAP);tp.leftMargin=dp(c,12);r.addView(trailing,tp);}
        else if(action!=null){LinearLayout.LayoutParams tp=lp(dp(c,24),dp(c,24));tp.leftMargin=dp(c,12);r.addView(icon(c,"chevron",AiosTheme.muted(c)),tp);}
        if(action!=null)clickable(r,action);
        return r;
    }
    /** Status row for lists: coloured dot, title, meta line, optional trailing. */
    public static LinearLayout statusRow(Context c,int dotColor,CharSequence title,CharSequence meta,View trailing,boolean first,boolean last,Runnable action){
        LinearLayout r=row(c);r.setMinimumHeight(dp(c,72));r.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));
        r.setBackground(ripple(c,groupShape(c,AiosTheme.surface(c),first,last)));
        LinearLayout.LayoutParams p=lp(MATCH,WRAP);p.bottomMargin=last?0:dp(c,2);r.setLayoutParams(p);
        View d=dot(c,dotColor);LinearLayout.LayoutParams dpm=lp(dp(c,8),dp(c,8));dpm.rightMargin=dp(c,16);r.addView(d,dpm);
        LinearLayout texts=column(c);texts.addView(lines(body(c,title),2));
        if(meta!=null&&meta.length()>0){TextView s=oneLine(secondary(c,meta));s.setPadding(0,dp(c,2),0,0);texts.addView(s);}
        r.addView(texts,weight());
        if(trailing!=null){LinearLayout.LayoutParams tp=lp(WRAP,WRAP);tp.leftMargin=dp(c,12);r.addView(trailing,tp);}
        if(action!=null)clickable(r,action);return r;
    }
    /** Container for a group: 16dp page padding, 12dp gap below. */
    public static LinearLayout group(Context c){LinearLayout g=column(c);LinearLayout.LayoutParams p=lp(MATCH,WRAP);p.setMargins(dp(c,16),0,dp(c,16),dp(c,12));g.setLayoutParams(p);return g;}
    /** Single 28dp card (surface) with 20dp padding. */
    public static LinearLayout card(Context c){LinearLayout g=column(c);g.setPadding(dp(c,20),dp(c,18),dp(c,20),dp(c,18));g.setBackground(shape(c,AiosTheme.surface(c),28));LinearLayout.LayoutParams p=lp(MATCH,WRAP);p.setMargins(dp(c,16),0,dp(c,16),dp(c,12));g.setLayoutParams(p);return g;}
    /** Informational banner (tertiary_container, 16dp) with icon, text, optional action. */
    public static LinearLayout banner(Context c,String iconKind,CharSequence message,CharSequence actionLabel,Runnable action){
        LinearLayout b=row(c);b.setPadding(dp(c,16),dp(c,12),dp(c,8),dp(c,12));b.setBackground(shape(c,AiosTheme.tertiary(c),16));
        LinearLayout.LayoutParams p=lp(MATCH,WRAP);p.setMargins(dp(c,16),0,dp(c,16),dp(c,12));b.setLayoutParams(p);
        if(iconKind!=null){LinearLayout.LayoutParams ip=lp(dp(c,24),dp(c,24));ip.rightMargin=dp(c,12);b.addView(icon(c,iconKind,AiosTheme.onTertiary(c)),ip);}
        b.addView(lines(text(c,message,14,AiosTheme.onTertiary(c)),3),weight());
        if(actionLabel!=null){TextView t=text(c,actionLabel,14,AiosTheme.onTertiary(c));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setMinHeight(dp(c,40));t.setGravity(Gravity.CENTER);t.setPadding(dp(c,12),0,dp(c,12),0);t.setBackground(ripple(c,null,20));if(action!=null)clickable(t,action);b.addView(t);}
        else if(action!=null)clickable(b,action);
        return b;
    }
    /** Error banner on error_container. */
    public static LinearLayout errorBanner(Context c,CharSequence message){
        LinearLayout b=banner(c,"warning",message,null,null);b.setBackground(shape(c,AiosTheme.errorContainer(c),16));
        for(int i=0;i<b.getChildCount();i++){View v=b.getChildAt(i);if(v instanceof TextView)((TextView)v).setTextColor(AiosTheme.onErrorContainer(c));else if(v instanceof ImageView)((ImageView)v).setImageDrawable(new AiosIcon(c,"warning",AiosTheme.onErrorContainer(c)));}
        return b;
    }
    /** Centered empty state. */
    public static LinearLayout empty(Context c,String iconKind,CharSequence title,CharSequence hint){
        LinearLayout e=column(c);e.setGravity(Gravity.CENTER_HORIZONTAL);e.setPadding(dp(c,32),dp(c,56),dp(c,32),dp(c,32));
        ImageView i=icon(c,iconKind,AiosTheme.outlineStrong(c));i.setLayoutParams(lp(dp(c,48),dp(c,48)));e.addView(i);gap(e,16);
        TextView t=body(c,title);t.setGravity(Gravity.CENTER);e.addView(t);
        if(hint!=null){gap(e,6);TextView h=secondary(c,hint);h.setGravity(Gravity.CENTER);e.addView(h);}
        return e;
    }

    // ---- Fields ----------------------------------------------------------------------------
    /** Filled text field (surface_container_high, 16dp) with hint; 56dp min height. */
    public static EditText field(Context c,CharSequence hint,CharSequence value){
        EditText e=os.aios.core.DisplayText.editable(new EditText(c));e.setHint(hint);if(value!=null)e.setText(value);e.setTextSize(16);
        e.setTextColor(AiosTheme.text(c));e.setHintTextColor(AiosTheme.muted(c));e.setBackground(shape(c,AiosTheme.surfaceHigh(c),16));
        e.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));e.setMinHeight(dp(c,56));
        try{e.setTextCursorDrawable(shape(c,AiosTheme.accent(c),1));}catch(Throwable ignored){}
        LinearLayout.LayoutParams p=lp(MATCH,WRAP);p.bottomMargin=dp(c,12);e.setLayoutParams(p);return e;
    }
    /** Multiline field for long text. */
    public static EditText area(Context c,CharSequence hint,CharSequence value,int minLines){
        EditText e=field(c,hint,value);e.setMinLines(minLines);e.setGravity(Gravity.TOP|Gravity.START);e.setSingleLine(false);os.aios.core.DisplayText.editable(e);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);os.aios.core.DisplayText.editable(e);return e;
    }
    /** Field label shown above a field (12sp accent). */
    public static TextView fieldLabel(Context c,CharSequence value){TextView t=text(c,value,12,AiosTheme.muted(c));t.setPadding(dp(c,16),0,dp(c,16),dp(c,6));return t;}
    /** Choice row set replacing Spinner: horizontally scrolled single-select chips. */
    public static final class Choice{
        public final HorizontalScrollView view;private final LinearLayout row;private final String[] options;public int index;private final java.util.function.IntConsumer onChange;
        public Choice(Context c,String[] options,int selected,java.util.function.IntConsumer onChange){this.options=options;this.index=selected;this.onChange=onChange;view=new HorizontalScrollView(c);view.setHorizontalScrollBarEnabled(false);row=row(c);row.setPadding(0,dp(c,4),0,dp(c,12));view.addView(row);render();}
        private void render(){Context c=view.getContext();row.removeAllViews();for(int i=0;i<options.length;i++){final int at=i;LinearLayout.LayoutParams p=lp(WRAP,WRAP);p.rightMargin=dp(c,8);row.addView(chip(c,options[i],i==index,()->{index=at;render();if(onChange!=null)onChange.accept(at);}),p);}}
        public int getSelectedItemPosition(){return index;}
        public void setSelection(int i){index=i;render();}
    }
    /** Checkbox-style setting as a switch row in forms. */
    public static LinearLayout switchRow(Context c,CharSequence title,CharSequence summary,boolean checked,java.util.function.Consumer<Boolean> change){
        Switch s=m3switch(c,checked,change);LinearLayout r=settingRow(c,null,title,summary,s,true,true,null);
        clickable(r,()->s.toggle());return r;
    }
    public static boolean isChecked(View row){Switch s=find(row,Switch.class);return s!=null&&s.isChecked();}
    @SuppressWarnings("unchecked") public static <T extends View> T find(View v,Class<T> type){if(type.isInstance(v))return (T)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){T t=find(g.getChildAt(i),type);if(t!=null)return t;}}return null;}

    // ---- Bubbles ---------------------------------------------------------------------------
    /** User message bubble (primary_container, 20dp, 4dp near sender). */
    public static TextView userBubble(Context c,CharSequence message){
        TextView t=selectable(c,message,16,AiosTheme.onSelected(c));t.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        t.setBackground(shape(c,AiosTheme.selected(c),20,4,20,20));t.setMaxWidth(dp(c,300));return t;
    }
    /** Assistant text: no decorative card, plain on_surface. */
    public static TextView assistantText(Context c,CharSequence message){TextView t=selectable(c,message,16,AiosTheme.text(c));MarkdownText.bind(t,message);t.setPadding(dp(c,4),dp(c,8),dp(c,16),dp(c,8));return t;}
    /** Tool receipt line: small icon, 14sp text, optional running state. */
    public static LinearLayout receipt(Context c,String iconKind,CharSequence text,boolean running){
        LinearLayout r=row(c);r.setPadding(dp(c,4),dp(c,6),dp(c,4),dp(c,6));
        if(running){ProgressBar p=new ProgressBar(c);p.setIndeterminate(true);p.setIndeterminateTintList(ColorStateList.valueOf(AiosTheme.accent(c)));LinearLayout.LayoutParams pp=lp(dp(c,18),dp(c,18));pp.rightMargin=dp(c,12);r.addView(p,pp);}
        else{LinearLayout.LayoutParams ip=lp(dp(c,18),dp(c,18));ip.rightMargin=dp(c,12);r.addView(icon(c,iconKind,AiosTheme.muted(c)),ip);}
        r.addView(lines(secondary(c,text),2),weight());return r;
    }

    // ---- Progress / dialogs ----------------------------------------------------------------
    public static ProgressBar progress(Context c){ProgressBar p=new ProgressBar(c);p.setIndeterminate(true);p.setIndeterminateTintList(ColorStateList.valueOf(AiosTheme.accent(c)));return p;}
    /** Dialog builder with the shared day/night dialog theme. */
    public static AlertDialog.Builder dialog(Context c){
        int theme=c.getResources().getIdentifier("AiosDialogTheme","style",c.getPackageName());
        if(theme==0)theme=c.getResources().getIdentifier("AiosDialogTheme","style","os.aios.core");
        return theme==0?new AlertDialog.Builder(c):new AlertDialog.Builder(c,theme);
    }
    /** Wrap form content for a dialog: 24dp horizontal padding inside a ScrollView. */
    public static ScrollView dialogContent(Context c,View content){ScrollView s=new ScrollView(c);s.setPadding(dp(c,24),dp(c,8),dp(c,24),0);s.addView(content);return s;}
    public static void message(Context c,CharSequence title,CharSequence body){dialog(c).setTitle(os.aios.core.DisplayText.clean(title)).setMessage(os.aios.core.DisplayText.clean(body)).setPositiveButton(os.aios.core.DisplayText.clean("知道了"),null).show();}
    public static void confirm(Context c,CharSequence title,CharSequence body,CharSequence ok,Runnable action){dialog(c).setTitle(os.aios.core.DisplayText.clean(title)).setMessage(os.aios.core.DisplayText.clean(body)).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean(ok),(d,w)->action.run()).show();}

    // ---- Bottom navigation -----------------------------------------------------------------
    /** M3 navigation bar: 80dp, pill indicator (secondary_container) + 12sp label. */
    public static LinearLayout navigation(Context c,String[] icons,String[] labels,int selected,java.util.function.IntConsumer select){
        LinearLayout bar=row(c);bar.setBackgroundColor(AiosTheme.surfaceLow(c));bar.setMinimumHeight(dp(c,80));bar.setPadding(0,dp(c,12),0,dp(c,16));
        for(int i=0;i<labels.length;i++){
            final int at=i;boolean on=i==selected;LinearLayout item=column(c);item.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView ic=icon(c,icons[i],on?AiosTheme.onTonal(c):AiosTheme.muted(c));ic.setPadding(dp(c,20),dp(c,4),dp(c,20),dp(c,4));
            ic.setBackground(on?shape(c,AiosTheme.tonal(c),16):null);item.addView(ic,lp(dp(c,64),dp(c,32)));
            TextView l=text(c,labels[i],12,on?AiosTheme.text(c):AiosTheme.muted(c));l.setTypeface(Typeface.create(on?"sans-serif-medium":"sans-serif",Typeface.NORMAL));l.setGravity(Gravity.CENTER_HORIZONTAL);l.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);l.setPadding(0,dp(c,4),0,0);item.addView(l,lp(MATCH,WRAP));
            item.setBackground(ripple(c,null,0));item.setContentDescription(os.aios.core.DisplayText.clean(labels[i]));clickable(item,()->select.accept(at));
            bar.addView(item,weight());
        }
        return bar;
    }

    /** Apply page background and system-bar colours to an Activity root. */
    public static void page(android.app.Activity a,View root){root.setBackgroundColor(AiosTheme.background(a));AiosTheme.window(a);}
}
