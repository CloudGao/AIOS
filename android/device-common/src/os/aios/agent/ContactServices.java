package os.aios.agent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.*;
import org.json.*;
import java.util.*;
final class ContactServices {
 final Context c;ContactServices(Context c){this.c=c;}
 JSONObject execute(String op,JSONObject p)throws Exception{
  if(c.checkSelfPermission("android.permission.WRITE_CONTACTS")!=0)return new JSONObject().put("state","permission_required").put("permission","android.permission.WRITE_CONTACTS");
  String name=p.getString("name").trim(),phone=p.optString("phone",""),email=p.optString("email","");if(name.isEmpty()||name.length()>200)throw new IllegalArgumentException("Name required");
  ArrayList<ContentProviderOperation> ops=new ArrayList<>();long raw=-1;
  if(op.equals("contacts.update")){
   raw=p.getLong("raw_contact_id");
   try(Cursor cur=c.getContentResolver().query(RawContacts.CONTENT_URI,new String[]{RawContacts.VERSION,RawContacts.DELETED},"_id=?",new String[]{String.valueOf(raw)},null)){if(cur==null||!cur.moveToFirst()||cur.getInt(1)!=0)throw new IllegalArgumentException("Raw contact absent");if(cur.getLong(0)!=p.getLong("expected_version"))throw new IllegalArgumentException("Contact changed; read again");}
   ops.add(ContentProviderOperation.newAssertQuery(RawContacts.CONTENT_URI).withSelection("_id=? AND version=?",new String[]{String.valueOf(raw),String.valueOf(p.getLong("expected_version"))}).withExpectedCount(1).build());
   ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI).withSelection("raw_contact_id=? AND mimetype IN (?,?,?)",new String[]{String.valueOf(raw),CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,CommonDataKinds.Phone.CONTENT_ITEM_TYPE,CommonDataKinds.Email.CONTENT_ITEM_TYPE}).build());
  }else ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI).withValue(RawContacts.ACCOUNT_NAME,null).withValue(RawContacts.ACCOUNT_TYPE,null).build());
  add(ops,raw,CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,name,null);
  if(!phone.isEmpty())add(ops,raw,CommonDataKinds.Phone.CONTENT_ITEM_TYPE,phone,CommonDataKinds.Phone.TYPE_MOBILE);
  if(!email.isEmpty())add(ops,raw,CommonDataKinds.Email.CONTENT_ITEM_TYPE,email,CommonDataKinds.Email.TYPE_OTHER);
  ContentProviderResult[] results=c.getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops);if(raw<0)raw=ContentUris.parseId(results[0].uri);
  JSONObject result=record(raw);return new JSONObject().put("state","succeeded").put("completion_verified",true).put("data",result);
 }
 void add(ArrayList<ContentProviderOperation> ops,long raw,String mime,String value,Integer type){ContentProviderOperation.Builder b=ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE,mime).withValue(Data.DATA1,value);if(type!=null)b.withValue(Data.DATA2,type);if(raw<0)b.withValueBackReference(Data.RAW_CONTACT_ID,0);else b.withValue(Data.RAW_CONTACT_ID,raw);ops.add(b.build());}
 JSONObject record(long raw)throws Exception{
  JSONObject out=new JSONObject().put("raw_contact_id",raw);try(Cursor cur=c.getContentResolver().query(ContentUris.withAppendedId(RawContacts.CONTENT_URI,raw),new String[]{RawContacts.CONTACT_ID,RawContacts.VERSION},null,null,null)){if(cur==null||!cur.moveToFirst())throw new IllegalStateException("Contact readback failed");out.put("contact_id",cur.getLong(0)).put("version",cur.getLong(1));}
  JSONArray data=new JSONArray();try(Cursor cur=c.getContentResolver().query(Data.CONTENT_URI,new String[]{Data.MIMETYPE,Data.DATA1},"raw_contact_id=?",new String[]{String.valueOf(raw)},null)){if(cur!=null)while(cur.moveToNext())data.put(new JSONObject().put("mime",cur.getString(0)).put("value",cur.getString(1)));}return out.put("fields",data);
 }
}
