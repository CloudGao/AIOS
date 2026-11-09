package os.aios.core.automation;

import os.aios.host.HostProduct;

/** Build-selected optional processor factories; no scheduler or business ledger here. */
public final class AutomationFeatures {
    public static void register(){
        if(HostProduct.includes("photo-index"))AutomationService.registerPipelineFactory(NativePipelineRuntime::new);
    }
}
