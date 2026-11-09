package os.aios.agent;

import android.content.Context;

/** Optional hosted feature routes; shared dispatch does not reference feature implementations. */
final class AgentOperationContributions {
    static DeviceOperationRegistry create(Context context){
        DeviceOperationRegistry registry=new DeviceOperationRegistry();
        // feature:documents:start
        if(os.aios.host.HostProduct.includes("documents"))
            registry.register(op->op.startsWith("documents."),
                ()->new DeviceOperationRegistry.Bound((op,args)->DocumentTools.call(context,op.substring(10),args),()->{}));
        // feature:documents:end
        // feature:miniapps:start
        if(os.aios.host.HostProduct.includes("miniapps"))
            registry.register(op->op.startsWith("miniapp."),
                ()->new DeviceOperationRegistry.Bound((op,args)->MiniAppManagerClient.execute(context,op.substring(8),args),()->{}));
        // feature:miniapps:end
        return registry;
    }
}
