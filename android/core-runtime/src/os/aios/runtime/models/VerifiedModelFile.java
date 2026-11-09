package os.aios.runtime.models;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Bounded atomic import; no Android dependency, executable by host behavior tests. */
public final class VerifiedModelFile {
    public interface Cancel { boolean cancelled(); }
    public static void install(InputStream input,File destination,long expectedBytes,String expectedHash,Cancel cancel)throws Exception{
        File parent=destination.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("model_directory_unavailable");
        File temporary=File.createTempFile("model-", ".part",parent);
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(FileOutputStream out=new FileOutputStream(temporary)){
                byte[] buffer=new byte[65536];int count;
                while((count=input.read(buffer))!=-1){
                    if(cancel.cancelled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("model_cancelled");
                    size+=count;if(size>expectedBytes)throw new IOException("model_size_mismatch");
                    digest.update(buffer,0,count);out.write(buffer,0,count);
                }
                if(size!=expectedBytes||!hex(digest.digest()).equals(expectedHash))throw new IOException("model_integrity_mismatch");
                out.getFD().sync();
            }
            if(cancel.cancelled()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("model_cancelled");
            Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary.toPath());}
    }
    public static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1)digest.update(block,0,n);}return hex(digest.digest());}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
