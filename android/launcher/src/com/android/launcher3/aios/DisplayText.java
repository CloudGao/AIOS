package com.android.launcher3.aios;

import android.text.SpannableStringBuilder;
import android.text.InputFilter;
import android.widget.TextView;
import android.widget.EditText;
import java.util.regex.Pattern;

/** Presentation only. Never apply to protocol identifiers, stored records or editable input. */
public final class DisplayText {
    private static final Pattern INTERNAL_NAME = Pattern.compile("codex", Pattern.CASE_INSENSITIVE);
    private DisplayText() {}
    public static String clean(String value) {
        return value == null ? null : INTERNAL_NAME.matcher(value).replaceAll("AIOS");
    }
    public static CharSequence clean(CharSequence value) {
        if (value == null || !INTERNAL_NAME.matcher(value).find()) return value;
        SpannableStringBuilder out = new SpannableStringBuilder(value);
        java.util.regex.Matcher m = INTERNAL_NAME.matcher(value);
        java.util.ArrayList<Integer> positions = new java.util.ArrayList<>();
        while (m.find()) positions.add(m.start());
        for (int i = positions.size()-1; i >= 0; i--) out.replace(positions.get(i), positions.get(i)+5, "AIOS");
        return out;
    }
    public static int clean(int resource) { return resource; }
    public static String[] clean(String[] values) {
        if (values == null) return null;
        String[] copy=values.clone();for(int i=0;i<copy.length;i++)copy[i]=clean(copy[i]);return copy;
    }
    public static CharSequence[] clean(CharSequence[] values) {
        if (values == null) return null;
        CharSequence[] copy=values.clone();for(int i=0;i<copy.length;i++)copy[i]=clean(copy[i]);return copy;
    }
    /** Live, same-length display view: edits/selection/composition keep the original offsets. */
    private static final class MaskedText implements android.text.Spanned, android.text.GetChars {
        final CharSequence source;
        MaskedText(CharSequence source){this.source=source;}
        public int length(){return source.length();}
        public char charAt(int index){
            if(index<0||index>=length())throw new IndexOutOfBoundsException();
            for(int start=Math.max(0,index-4);start<=index&&start+5<=length();start++){
                boolean match=true;for(int j=0;j<5;j++)if(Character.toLowerCase(source.charAt(start+j))!="codex".charAt(j)){match=false;break;}
                if(match)return "AIOS ".charAt(index-start);
            }
            return source.charAt(index);
        }
        public void getChars(int start,int end,char[] dest,int offset){for(int i=start;i<end;i++)dest[offset+i-start]=charAt(i);}
        public String toString(){return INTERNAL_NAME.matcher(source.toString()).replaceAll("AIOS ");}
        public CharSequence subSequence(int start,int end){return new SpannableStringBuilder(this,start,end);}
        @SuppressWarnings("unchecked") public <T> T[] getSpans(int start,int end,Class<T> type){return source instanceof android.text.Spanned?((android.text.Spanned)source).getSpans(start,end,type):(T[])java.lang.reflect.Array.newInstance(type,0);}
        public int getSpanStart(Object tag){return source instanceof android.text.Spanned?((android.text.Spanned)source).getSpanStart(tag):-1;}
        public int getSpanEnd(Object tag){return source instanceof android.text.Spanned?((android.text.Spanned)source).getSpanEnd(tag):-1;}
        public int getSpanFlags(Object tag){return source instanceof android.text.Spanned?((android.text.Spanned)source).getSpanFlags(tag):0;}
        public int nextSpanTransition(int start,int limit,Class type){return source instanceof android.text.Spanned?((android.text.Spanned)source).nextSpanTransition(start,limit,type):limit;}
    }
    public static <T extends EditText> T editable(T view) {
        view.setTransformationMethod(new android.text.method.TransformationMethod() {
            public CharSequence getTransformation(CharSequence source, android.view.View v) {
                return source==null?null:new MaskedText(source);
            }
            public void onFocusChanged(android.view.View v,CharSequence s,boolean focused,int direction,android.graphics.Rect previous) {}
        });return view;
    }
    /** Filters display widgets at every setText, including later asynchronous updates. */
    public static <T extends TextView> T bind(T view) {
        if (view instanceof EditText) throw new IllegalArgumentException("Editable input must retain its original value");
        InputFilter[] previous=view.getFilters();InputFilter[] filters=java.util.Arrays.copyOf(previous,previous.length+1);
        filters[previous.length]=(source,start,end,dest,dstart,dend)-> {
            CharSequence part=source.subSequence(start,end);
            return INTERNAL_NAME.matcher(part).find()?clean(part):null;
        };
        view.setFilters(filters);
        view.addTextChangedListener(new android.text.TextWatcher() {
            boolean changing;
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(android.text.Editable value) {
                if(changing)return;
                java.util.regex.Matcher m=INTERNAL_NAME.matcher(value);
                java.util.ArrayList<Integer> positions=new java.util.ArrayList<>();
                while(m.find())positions.add(m.start());
                changing=true;try {for(int i=positions.size()-1;i>=0;i--)value.replace(positions.get(i),positions.get(i)+5,"AIOS");}finally{changing=false;}
            }
        });return view;
    }
}
