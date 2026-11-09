package os.aios.agent;
import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.net.Uri;import android.os.*;import java.io.*;import java.util.zip.*;
/** One-way, signature-protected migration source. It never accepts writes. */
public final class LegacyStateProvider extends ContentProvider{
 public boolean onCreate(){return true;}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
  if(!"r".equals(mode))throw new SecurityException("Legacy state is read-only");java.util.List<String> p=uri.getPathSegments();if(p.size()!=2)throw new FileNotFoundException();
  try{if(p.get(0).equals("database")){String name=p.get(1);if(!java.util.Set.of("aios_workspace.db","aios_context.db","aios_automation.db","aios_events.db").contains(name))throw new SecurityException("Unknown database");File db=getContext().getDatabasePath(name);if(!db.isFile())throw new FileNotFoundException(name);try(SQLiteDatabase open=SQLiteDatabase.openDatabase(db.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE)){open.rawQuery("PRAGMA wal_checkpoint(FULL)",null).close();}return ParcelFileDescriptor.open(db,ParcelFileDescriptor.MODE_READ_ONLY);}
   if(p.get(0).equals("asset")&&p.get(1).equals("skills.zip")){ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();new Thread(()->writeSkills(pipe[1]),"AIOS-legacy-skills").start();return pipe[0];}
  }catch(FileNotFoundException e){throw e;}catch(Exception e){throw new FileNotFoundException(e.toString());}throw new FileNotFoundException();
 }
 private void writeSkills(ParcelFileDescriptor output){try(OutputStream raw=new ParcelFileDescriptor.AutoCloseOutputStream(output);ZipOutputStream zip=new ZipOutputStream(raw)){File root=new File(getContext().getFilesDir(),"skills");add(zip,root,root);}catch(Exception e){android.util.Log.e("AIOSMigration","Skill export failed",e);}}
 private void add(ZipOutputStream zip,File root,File file)throws Exception{if(!file.exists())return;if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)add(zip,root,child);return;}String name=root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');zip.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(file)){in.transferTo(zip);}zip.closeEntry();}
 public String getType(Uri u){return "application/octet-stream";}public Cursor query(Uri u,String[]p,String s,String[]a,String o){throw new UnsupportedOperationException();}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
}
