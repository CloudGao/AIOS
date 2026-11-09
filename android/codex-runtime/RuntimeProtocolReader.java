package os.aios.codex;

import java.io.*;

/** Bounded display-side projection. Never changes outgoing model input or tool requests. */
final class RuntimeProtocolReader {
    static final int MAX_CHARS = 4 * 1024 * 1024;
    private final Reader input;
    private int next = -2;
    private StringBuilder output;
    RuntimeProtocolReader(Reader input) { this.input = input; }
    private int peek() throws IOException { if(next == -2) next=input.read(); return next; }
    private int take() throws IOException { int c=peek(); next=-2; return c; }
    private void emit(int c) throws IOException {
        if(c<0) throw new EOFException("Incomplete runtime frame");
        if(output.length()>=MAX_CHARS) throw new IOException("Runtime text frame exceeds bounded projection");
        output.append((char)c);
    }
    private void spaces() throws IOException { while(peek()==' '||peek()=='\t'||peek()=='\r') emit(take()); }
    String readFrame() throws IOException {
        while(peek()=='\n'||peek()=='\r') take();
        if(peek()<0) return null;
        output=new StringBuilder(); value(false,0); spaces();
        if(peek()!='\n'&&peek()!=-1) throw new IOException("Invalid runtime frame boundary");
        if(peek()=='\n') take();
        String result=output.toString(); output=null; return result;
    }
    private void value(boolean projection,int depth) throws IOException {
        if(depth>128) throw new IOException("Runtime frame nesting limit");
        spaces(); int c=peek();
        if(c=='{') {
            emit(take()); spaces();
            if(peek()=='}'){emit(take());return;}
            while(true){
                if(peek()!='"')throw new IOException("Invalid object key");
                String key=string(false,true); spaces();
                if(take()!=':')throw new IOException("Missing colon"); emit(':');
                value(projection||(depth==0&&key.equals("result")),depth+1); spaces();
                c=take();emit(c);if(c=='}')return;if(c!=',')throw new IOException("Invalid object separator");spaces();
            }
        } else if(c=='[') {
            emit(take());spaces();if(peek()==']'){emit(take());return;}
            while(true){value(projection,depth+1);spaces();c=take();emit(c);if(c==']')return;if(c!=',')throw new IOException("Invalid array separator");}
        } else if(c=='"') string(projection,false);
        else {
            int count=0;
            while((c=peek())!=-1&&c!=','&&c!=']'&&c!='}'&&c!='\n'&&c!='\r'&&c!=' '&&c!='\t'){emit(take());count++;}
            if(count==0)throw new IOException("Missing JSON value");
        }
    }
    private String string(boolean projection,boolean key) throws IOException {
        emit(take()); StringBuilder prefix=new StringBuilder(); boolean dropping=false;
        int start=output.length();
        while(true){
            int c=take();if(c<0||c=='\n'||c=='\r')throw new IOException("Incomplete JSON string");
            if(c=='"'){emit(c);return key?prefix.toString():"";}
            int decoded=c;
            if(c=='\\'){
                int escaped=take();if(escaped<0)throw new EOFException();
                if(!dropping){emit(c);emit(escaped);}
                if(escaped=='u'){
                    decoded=0;for(int i=0;i<4;i++){int h=take(),v=Character.digit(h,16);if(v<0)throw new IOException("Invalid Unicode escape");decoded=decoded*16+v;if(!dropping)emit(h);}
                }else decoded=escaped;
            }else if(!dropping)emit(c);
            if(prefix.length()<256)prefix.append((char)decoded);
            else if(key)throw new IOException("Oversized object key");
            if(projection&&!dropping&&prefix.length()==11&&(prefix.toString().startsWith("data:image/")||prefix.toString().startsWith("data:audio/")||prefix.toString().startsWith("data:video/"))){
                output.setLength(start);output.append("[media retained in original phone thread]");dropping=true;
            }
        }
    }
}
