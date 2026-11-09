package os.aios.agent;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import java.io.*;
public final class ShareFixtureProvider extends ContentProvider{
 public boolean onCreate(){return true;}
 private File file(Uri uri)throws FileNotFoundException{String name=uri.getLastPathSegment();if(!java.util.Set.of("probe.png","probe.mp4").contains(name))throw new FileNotFoundException();return new File(getContext().getCacheDir(),name);}
 public String getType(Uri u){return u.toString().endsWith("mp4")?"video/mp4":"image/png";}
 public Cursor query(Uri u,String[] projection,String s,String[] args,String order){try{File f=file(u);String[] cols=projection==null?new String[]{"_display_name","_size"}:projection;MatrixCursor cursor=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=cols[i].equals("_display_name")?f.getName():cols[i].equals("_size")?f.length():null;cursor.addRow(row);return cursor;}catch(Exception e){return null;}}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!mode.equals("r"))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
