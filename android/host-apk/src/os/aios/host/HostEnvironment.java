package os.aios.host;

/** Build-selected deployment configuration; contains no shared runtime logic. */
final class HostEnvironment {
 private HostEnvironment() {}
 static final String HOST_ID = "android-adb";
 static final String AGENT_PACKAGE = "os.aios.app";
 static final String CORE_PACKAGE = "os.aios.app";
 static final String RUNTIME_PACKAGE = "os.aios.app";
 static final String CORE_AUTHORITY = "os.aios.app.core";
 static final String CONTROL_PERMISSION = "os.aios.app.permission.CONTROL_SESSION";
 static final String CORE_PERMISSION = "os.aios.app.permission.CORE_RUNTIME";
 static final String CAPABILITY_AUTHORITY = "os.aios.app.capabilities";
 static final String CODEX_FALLBACK = null;
 static final String SEARCH_MODELS = "/nonexistent/aios-models-not-packaged";
 static final String TEXT_MODEL = "/nonexistent/aios-text-model-not-packaged";
 static final String COMMAND_PATH = "/system/bin";
}
