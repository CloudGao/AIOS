package os.aios.core.indexing;

import androidx.exifinterface.media.ExifInterface;
import android.os.ParcelFileDescriptor;
import org.json.*;
import java.lang.reflect.Field;
import java.util.*;

/** Reads original authorized FD. Absent metadata is distinct from unsupported/failed parsing. */
public final class PhotoExif {
    public static JSONObject extract(ParcelFileDescriptor fd,boolean locationAllowed)throws Exception {
        ExifInterface exif=new ExifInterface(fd.getFileDescriptor());JSONObject raw=new JSONObject(),fields=new JSONObject();int bytes=0;boolean truncated=false;boolean metadata=false;Set<String> seen=new HashSet<>();
        for(Field field:ExifInterface.class.getFields()){
            if(!field.getName().startsWith("TAG_")||field.getType()!=String.class)continue;String tag=(String)field.get(null);if(!seen.add(tag))continue;
            if(tag.startsWith("GPS")&&!locationAllowed)continue;
            String value=exif.getAttribute(tag);if(value==null)continue;
            if(value.length()>2048||bytes+tag.length()+value.length()>24000){truncated=true;continue;}
            if(!tag.equals("ImageWidth")&&!tag.equals("ImageLength")&&!(java.util.Set.of("Orientation","LightSource").contains(tag)&&value.equals("0")))metadata=true;
            raw.put(tag,new JSONObject().put("value",value).put("representation","ExifInterface string"));bytes+=tag.length()+value.length();
        }
        text(exif,fields,"camera_make",ExifInterface.TAG_MAKE);text(exif,fields,"camera_model",ExifInterface.TAG_MODEL);text(exif,fields,"lens_make",ExifInterface.TAG_LENS_MAKE);text(exif,fields,"lens_model",ExifInterface.TAG_LENS_MODEL);
        number(exif,fields,"exposure_seconds",ExifInterface.TAG_EXPOSURE_TIME);number(exif,fields,"f_number",ExifInterface.TAG_F_NUMBER);number(exif,fields,"iso",ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);number(exif,fields,"focal_length_mm",ExifInterface.TAG_FOCAL_LENGTH);number(exif,fields,"orientation",ExifInterface.TAG_ORIENTATION);
        String date=exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),offset=exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL);
        if(date!=null){fields.put("taken_local",date);fields.put("offset_unknown",offset==null);if(offset!=null){fields.put("taken_offset",offset);try{java.time.LocalDateTime local=java.time.LocalDateTime.parse(date,java.time.format.DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT));fields.put("taken_at_ms",local.toInstant(java.time.ZoneOffset.of(offset)).toEpochMilli());}catch(Exception e){fields.put("time_parse_state","invalid");}}}
        if(locationAllowed){double[] gps=exif.getLatLong();if(gps!=null){fields.put("latitude",gps[0]).put("longitude",gps[1]).put("gps_state","available");double altitude=exif.getAltitude(Double.NaN);if(Double.isFinite(altitude))fields.put("altitude_m",altitude);}else fields.put("gps_state","absent");}else fields.put("gps_state","permission_required_or_redacted");
        return new JSONObject().put("state",!metadata?"no_metadata":"ready").put("parser_version","androidx-exif-1.4.0-rc01").put("raw_exif",raw).put("fields",fields).put("truncated",truncated).put("tag_coverage","public_TAG_constants_readable_as_strings");
    }
    private static void text(ExifInterface exif,JSONObject out,String key,String tag)throws Exception{String v=exif.getAttribute(tag);if(v!=null)out.put(key,v.trim());}
    private static void number(ExifInterface exif,JSONObject out,String key,String tag)throws Exception{double v=exif.getAttributeDouble(tag,Double.NaN);if(Double.isFinite(v))out.put(key,v);}
}
