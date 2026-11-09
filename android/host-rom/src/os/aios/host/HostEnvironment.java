package os.aios.host;

/** Build-selected deployment configuration; contains no shared runtime logic. */
final class HostEnvironment {
 private HostEnvironment() {}
 static final String HOST_ID = "lineage-rom";
 static final String AGENT_PACKAGE = "os.aios.agent";
 static final String CORE_PACKAGE = "os.aios.core";
 static final String RUNTIME_PACKAGE = "os.aios.codex";
 static final String CORE_AUTHORITY = "os.aios.core.runtime";
 static final String CONTROL_PERMISSION = "os.aios.permission.CONTROL_SESSION";
 static final String CORE_PERMISSION = "os.aios.permission.CORE_RUNTIME";
 static final String CAPABILITY_AUTHORITY = "os.aios.core.capabilities";
 static final String CODEX_FALLBACK = "/system_ext/bin/aios-codex-app-server";
 static final String SEARCH_MODELS = "/system_ext/etc/aios/search-models";
 static final String TEXT_MODEL = "/system_ext/etc/aios/text-models/LFM2.5-350M-Q4_K_M.gguf";
 static final String COMMAND_PATH = "/system/bin:/system_ext/bin";
}
