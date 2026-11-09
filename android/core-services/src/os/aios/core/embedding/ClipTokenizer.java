package os.aios.core.embedding;
import android.content.Context;
import org.json.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Pinned OpenCLIP byte-pair tokenizer, ASCII visual queries only; unsupported text is explicit. */
public final class ClipTokenizer {
    private final JSONObject encoder,bytes;private final Map<String,Integer> ranks=new HashMap<>();
    private static final Pattern TOKENS=Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d|[a-z]+|[0-9]|[^\\sa-z0-9]+",Pattern.CASE_INSENSITIVE);
    public ClipTokenizer(Context c)throws Exception{this(c,null);}
    public ClipTokenizer(Context c,String space)throws Exception{ModelRegistry registry=new ModelRegistry(c,space);File file=new File(registry.directory(),"tokenizer.json");JSONObject manifest=registry.manifest();if(!file.isFile()||file.length()>5000000||!ModelRegistry.sha256(file).equals(manifest.getString("tokenizer_sha256")))throw new IllegalStateException("tokenizer_hash_mismatch");JSONObject data=new JSONObject(os.aios.core.CompatFiles.readString(file.toPath()));encoder=data.getJSONObject("encoder");bytes=data.getJSONObject("byte_encoder");JSONArray merges=data.getJSONArray("merges");for(int i=0;i<merges.length();i++){JSONArray pair=merges.getJSONArray(i);ranks.put(pair.getString(0)+"\u0000"+pair.getString(1),i);}}
    public int[] encode(String query)throws Exception{
        if(!query.chars().allMatch(c->c>=32&&c<127))throw new IllegalArgumentException("visual_query_requires_ascii_english");
        query=query.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");ArrayList<Integer> tokens=new ArrayList<>();tokens.add(49406);Matcher match=TOKENS.matcher(query);
        while(match.find()){
            byte[] input=match.group().getBytes(StandardCharsets.UTF_8);ArrayList<String> word=new ArrayList<>();for(byte b:input)word.add(bytes.getString(Integer.toString(b&255)));word.set(word.size()-1,word.get(word.size()-1)+"</w>");
            while(word.size()>1){int best=Integer.MAX_VALUE,position=-1;for(int i=0;i<word.size()-1;i++){int rank=ranks.getOrDefault(word.get(i)+"\u0000"+word.get(i+1),Integer.MAX_VALUE);if(rank<best){best=rank;position=i;}}if(position<0)break;String left=word.get(position),right=word.get(position+1);ArrayList<String> merged=new ArrayList<>();for(int i=0;i<word.size();i++){if(i+1<word.size()&&word.get(i).equals(left)&&word.get(i+1).equals(right)){merged.add(left+right);i++;}else merged.add(word.get(i));}word=merged;}
            for(String piece:word)tokens.add(encoder.getInt(piece));
        }
        if(tokens.size()>76)throw new IllegalArgumentException("Visual query exceeds model context");tokens.add(49407);int[] result=new int[77];for(int i=0;i<tokens.size();i++)result[i]=tokens.get(i);return result;
    }
    public static String rewrite(String query){switch(query.trim()){case "自行车":case "单车":return "a photo of a bicycle";case "狗":return "a photo of a dog";case "猫":return "a photo of a cat";case "海滩":return "a photo of a beach";case "杯子":return "a photo of a cup";case "汽车":return "a photo of a car";default:return query;}}
}
