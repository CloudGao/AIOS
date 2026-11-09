package os.aios.agent;

import android.content.Context;

/** Explicit selected factories. Feature sections are removed by trusted staging when excluded. */
final class DeviceContributions {
    private static final os.aios.device.DeviceEventSink EVENTS=(topic,source,version,data)->
        EventStore.add(topic,new org.json.JSONObject(data.toString()).put("source_id",source).put("source_version",version));
    private static final os.aios.device.DeviceResources RESOURCES=type->{
        AgentService owner=AgentService.instance;
        if(owner==null)throw new IllegalStateException("foreground_owner_unavailable");
        String token=owner.acquireForeground(type);
        java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
        return ()->{if(closed.compareAndSet(false,true))owner.releaseForeground(token);};
    };
    static DeviceOperationRegistry create(Context context){
        DeviceOperationRegistry registry=new DeviceOperationRegistry();
        // feature:files:start
        if(os.aios.host.HostProduct.includes("files"))
            registry.register(op->op.startsWith("files."),
                ()->{FileOperations handler=new FileOperations(context,(kind,args)->UserRequest.enqueue(context,kind,args));return new DeviceOperationRegistry.Bound(handler::execute,()->{});});
        // feature:files:end
        // feature:photos:start
        if(os.aios.host.HostProduct.includes("photos"))
            registry.register(op->op.startsWith("photos."),
                ()->{PhotoOperations handler=new PhotoOperations(context,(kind,args)->UserRequest.enqueue(context,kind,args));return new DeviceOperationRegistry.Bound(handler::execute,()->{});});
        // feature:photos:end
        // feature:streams:start
        if(os.aios.host.HostProduct.includes("streams"))
            registry.register(op->op.startsWith("streams.")||op.equals("sensors.stream")||op.equals("location.stream")||op.equals("location.current"),
                ()->{Streams handler=new Streams(context,EVENTS,RESOURCES);return new DeviceOperationRegistry.Bound(handler::execute,handler::close);});
        // feature:streams:end
        // feature:recording:start
        if(os.aios.host.HostProduct.includes("recording"))
            registry.register(op->op.startsWith("microphone."),
                ()->{RecorderServices handler=new RecorderServices(context,RESOURCES);return new DeviceOperationRegistry.Bound(
                    (op,args)->handler.execute(op.equals("microphone.record")?"microphone.start":op,args),handler::close);});
        // feature:recording:end
        // feature:browser:start
        if(os.aios.host.HostProduct.includes("browser"))
            registry.register(op->op.equals("browser.action"),
                ()->{BrowserServices handler=new BrowserServices(context);return new DeviceOperationRegistry.Bound(
                    (op,args)->handler.execute(args),handler::close);});
        // feature:browser:end
        return registry;
    }
}
