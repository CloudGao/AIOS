package os.aios.codex;

import android.content.Context;
import java.util.Map;
import os.aios.host.HostProduct;

/** Trusted, build-selected extension wiring. */
final class RuntimeFeatureBindings {
    static RuntimeExtension cli(Context context)throws Exception{
        return HostProduct.includes("cli")?new CliPackages(context):null;
    }
    static RuntimeExtension.Mcp mcp(CodexProcess process){
        return HostProduct.includes("mcp")?new PhoneMcp(process):null;
    }
    static void environment(Context context,Map<String,String> environment)throws Exception{
        environment.put("PATH",os.aios.host.HostBindings.systemCommandPath());
        if(HostProduct.includes("cli"))try{CliPackages.prepare(context);CliPackages.environment(context,environment);RuntimeCapabilities.recovered("cli");}
        catch(Exception error){RuntimeCapabilities.failed("cli",error);}
    }
}
