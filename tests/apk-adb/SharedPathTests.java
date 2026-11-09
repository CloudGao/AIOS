package os.aios.apk.shell;
import java.io.*;import java.nio.file.*;
public final class SharedPathTests {
 static int checks;
 static void check(boolean yes){checks++;if(!yes)throw new AssertionError("check "+checks);}
 static void denied(File root,String path)throws Exception{boolean blocked=false;try{SharedPath.resolve(root,path);}catch(SecurityException|IllegalArgumentException e){blocked=true;}check(blocked);}
 public static void main(String[] args)throws Exception{
  Path temp=Files.createTempDirectory("aios-shared-path-");File root=Files.createDirectory(temp.resolve("shared")).toFile();
  try{
   check(SharedPath.resolve(root,new File(root,"Download/a.apk").getPath()).getPath().endsWith("Download/a.apk"));
   denied(root,"relative.apk");denied(root,root.getPath());denied(root,new File(root,"../secret").getPath());denied(root,root.getPath()+"-other/a.apk");denied(root,"/data/data/other/app");
   Files.createSymbolicLink(root.toPath().resolve("escape"),temp);denied(root,new File(root,"escape/secret").getPath());
   check(SharedPath.resolve(root,new File(root,"Download/../file.txt").getPath()).equals(new File(root,"file.txt")));
   System.out.println("PASS: "+checks+" shared-path boundary assertions");
  }finally{Files.deleteIfExists(root.toPath().resolve("escape"));Files.delete(root.toPath());Files.delete(temp);}
 }
}
