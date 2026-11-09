package os.aios.speech;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Shared MIT Whisper decoder for assistant input and recording/minutes. */
public final class WhisperRecognizer implements AutoCloseable {
 public static final String MODEL="ggml-small-q5_1.bin";
 public static final String SHA256="ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb";
 private long handle;
 private final java.util.concurrent.locks.ReentrantReadWriteLock lifetime=new java.util.concurrent.locks.ReentrantReadWriteLock();
 static {System.loadLibrary("aios_whisper");}
 public WhisperRecognizer(Context context)throws IOException{handle=load(install(context,MODEL,SHA256).getPath());if(handle==0)throw new IOException("whisper_model_load_failed");}
 public static synchronized File install(Context context,String name,String expected)throws IOException{
  File dir=new File(context.getFilesDir(),"whisper-models");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("speech_model_directory");
  File file=new File(dir,name);if(file.isFile()&&expected.equals(hash(file)))return file;
  AtomicFile atomic=new AtomicFile(file);FileOutputStream out=atomic.startWrite();
  try{MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
   try(InputStream in=context.getAssets().open(name)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);digest.update(bytes,0,n);}}out.getFD().sync();
   StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));
   if(!expected.contentEquals(actual))throw new IOException("speech_model_hash_mismatch");atomic.finishWrite(out);
  }catch(IOException|RuntimeException error){atomic.failWrite(out);throw error;}
  return file;
 }
 private static String hash(File file)throws IOException{
  try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder text=new StringBuilder();for(byte v:d.digest())text.append(String.format(Locale.ROOT,"%02x",v&255));return text.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
 }
 public synchronized String decode(float[] audio,String language){
  lifetime.readLock().lock();try{if(handle==0)throw new IllegalStateException("whisper_closed");return new String(transcribe(handle,audio,language==null?"auto":language),StandardCharsets.UTF_8).trim();}finally{lifetime.readLock().unlock();}
 }
 public void cancel(){setCancellation(true);}
 public void resetCancellation(){setCancellation(false);}
 private void setCancellation(boolean value){lifetime.readLock().lock();try{if(handle!=0)setCancelled(handle,value);}finally{lifetime.readLock().unlock();}}
 public void close(){lifetime.writeLock().lock();try{long h=handle;handle=0;if(h!=0)release(h);}finally{lifetime.writeLock().unlock();}}
 private static native long load(String path);
 private static native byte[] transcribe(long handle,float[] samples,String language);
 private static native void setCancelled(long handle,boolean cancelled);
 private static native void release(long handle);
}
