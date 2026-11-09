package os.aios.agent;
import java.util.Set;
public final class AppDiscoveryTest {
 static int count;static void check(boolean x){count++;if(!x)throw new AssertionError("case "+count);}
 public static void main(String[] args){
  check(AppDiscovery.matches("高德","com.autonavi.minimap","高德地图"));
  check(AppDiscovery.matches("搜狗","com.sohu.inputmethod.sogou","搜狗输入法"));
  check(AppDiscovery.matches("COM.AUTONAVI","com.autonavi.minimap","高德地图"));
  check(AppDiscovery.matches(" ＡＢＣ ","org.example","Abc App"));
  check(!AppDiscovery.matches("高德","com.other.maps","其他地图"));
  check(!AppDiscovery.matches("not installed","com.autonavi.minimap","高德地图"));
  check(AppDiscovery.matches("","com.any.app","任意"));
  check(!AppDiscovery.isHandler("android/ResolverActivity",Set.of("com.maps/Search","com.other/Search")));
  check(AppDiscovery.isHandler("com.maps/Search",Set.of("com.maps/Search","com.other/Search")));
  check(AppDiscovery.isHandler("com.maps/Search",Set.of("com.maps/Search")));
  check(!AppDiscovery.isHandler(null,Set.of()));
  check(!AppDiscovery.isHandler("com.missing/Search",Set.of()));
  check(AppDiscovery.pageEnd(0,100,300)==100);
  check(AppDiscovery.pageEnd(200,100,201)==201);
  check(AppDiscovery.pageEnd(300,100,201)==201);
  check(AppDiscovery.pageEnd(Integer.MAX_VALUE,100,201)==201);
  check(AppDiscovery.pageEnd(0,100,0)==0);
  System.out.println("App discovery and intent policy passed: "+count);
 }
}
