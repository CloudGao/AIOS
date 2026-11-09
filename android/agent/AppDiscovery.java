package os.aios.agent;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
/** Shared pure matching and resolver policy; never guesses a package from a brand. */
final class AppDiscovery {
 static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+","").trim();}
 static boolean matches(String query,String pkg,String label){String q=normalize(query);return normalize(pkg).contains(q)||normalize(label).contains(q);}
 static boolean isHandler(String resolved,Set<String> handlers){return resolved!=null&&handlers.contains(resolved);}
 static int pageEnd(int offset,int limit,int total){return (int)Math.min((long)offset+limit,total);}
}
