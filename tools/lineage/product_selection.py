"""Compile-time product selection. Pure planning; never changes source or device state."""
import importlib.util
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET


class ProductSelection:
    def __init__(self, source, product=None):
        self.source = Path(source)
        host = self.source / 'android/host-rom'
        spec = importlib.util.spec_from_file_location('aios_product_manifest', host / 'generate_product.py')
        generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(generator)
        self.manifest = generator.resolve(json.loads((host / 'modules.json').read_text()),
                                          json.loads(Path(product or host / 'product.json').read_text()))
        self.selected = {item['id'] for item in self.manifest['modules'] if item['packaged']}

    def has(self, module):
        return module in self.selected

    def includes_source(self, module, relative):
        path = str(relative).replace('\\', '/')
        if module == 'device-common':
            owner={'src/os/aios/agent/Streams.java':'streams','src/os/aios/agent/RecorderServices.java':'recording',
                   'src/os/aios/agent/FileOperations.java':'files','src/os/aios/agent/FileTransfers.java':'files',
                   'src/os/aios/agent/PhotoOperations.java':'photos'}.get(path)
            if owner and not self.has(owner):
                return False
        if module == 'codex-runtime':
            if not self.has('cli') and (path=='CliPackages.java' or path.startswith('assets/cli-runtime/')):
                return False
            if not self.has('mcp') and path=='PhoneMcp.java':
                return False
            if not self.has('miniapps') and path=='MiniAppSourceProvider.java':
                return False
        if module == 'device-rom' and path == 'src/os/aios/agent/BrowserServices.java' and not self.has('browser'):
            return False
        if module == 'agent':
            owner={'Streams.java':'streams','RecorderServices.java':'recording','BrowserServices.java':'browser',
                   'PhoneNotifications.java':'notification-events','NotificationListenerAccess.java':'notification-events',
                   'MinutesCodexBridge.java':'minutes','MiniAppManagerClient.java':'miniapps',
                   'DocumentTools.java':'documents'}.get(path)
            if owner and not self.has(owner):
                return False
        if module == 'agent' and not self.has('notification-governance'):
            if path in {'NotificationGovernanceHooks.java','NotificationGovernanceService.java',
                        'NotificationGovernanceProvider.java','NotificationManagementActivity.java',
                        'NotificationLedger.java','NotificationRules.java','NotificationPolicyImplementation.java'}:
                return False
            if path.startswith('res/') and path.endswith('/notification_management.xml'):
                return False
        if module != 'core-services':
            return True
        if path == 'res/xml/local_inference_network.xml' and not self.has('local-inference'):
            return False
        owners = {
            'src/os/aios/core/documents/': 'documents',
            'src/os/aios/core/inference/': 'local-inference',
            'src/os/aios/core/embedding/': 'photo-index',
            'src/os/aios/core/indexing/': 'photo-index',
            'src/os/aios/core/automation/NativePipelineRuntime.java': 'photo-index',
            'src/os/aios/core/search/MediaSemanticAdapter.java': 'photo-index',
            'src/os/aios/core/search/DerivedPhotoAdapter.java': 'photo-index',
            'src/os/aios/core/search/IndexedPhotoSearch.java': 'photo-index',
        }
        return all(self.has(owner) for prefix, owner in owners.items() if path.startswith(prefix))

    @staticmethod
    def remove_list_values(text, field, values):
        pattern = r'(\b' + re.escape(field) + r'\s*:\s*\[)(.*?)(\])'
        def replace(match):
            body = match.group(2)
            for value in values:
                body = re.sub(r'"' + re.escape(value) + r'"\s*,?\s*', '', body)
            return match.group(1) + body + match.group(3)
        return re.sub(pattern, replace, text, flags=re.S)

    def transform(self, module, relative, data):
        path = str(relative).replace('\\', '/')
        if module == 'codex-runtime' and path == 'AndroidManifest.xml' and not self.has('miniapps'):
            ET.register_namespace('android','http://schemas.android.com/apk/res/android')
            tree=ET.fromstring(data)
            application=tree.find('application')
            for node in list(application):
                if node.get('{http://schemas.android.com/apk/res/android}name')=='.MiniAppSourceProvider':
                    application.remove(node)
            return ET.tostring(tree,encoding='utf-8')+b'\n'
        if module == 'codex-runtime' and path == 'Android.bp' and not self.has('cli'):
            return self.remove_list_values(data.decode(),'jni_libs',['libaios_node','libaios_cli_launcher']).encode()
        if module == 'host-rom' and path == 'runtime/os/aios/codex/RuntimeFeatureBindings.java':
            cli='new CliPackages(context)' if self.has('cli') else 'null'
            mcp='new PhoneMcp(process)' if self.has('mcp') else 'null'
            setup='try{CliPackages.prepare(context);CliPackages.environment(context,environment);RuntimeCapabilities.recovered("cli");}catch(Exception error){RuntimeCapabilities.failed("cli",error);}' if self.has('cli') else ''
            return ('package os.aios.codex;\n'
                    'final class RuntimeFeatureBindings {\n'
                    ' static RuntimeExtension cli(android.content.Context context)throws Exception{return '+cli+';}\n'
                    ' static RuntimeExtension.Mcp mcp(CodexProcess process){return '+mcp+';}\n'
                    ' static void environment(android.content.Context context,java.util.Map<String,String> environment)throws Exception{\n'
                    '  environment.put("PATH",os.aios.host.HostBindings.systemCommandPath());'+setup+'\n'
                    ' }\n}\n').encode()
        if module == 'host-rom' and path in ('agent/os/aios/agent/DeviceContributions.java','agent/os/aios/agent/AgentOperationContributions.java'):
            known={item['id'] for item in self.manifest['modules']}
            def select(match):
                owner=match.group(1)
                if owner not in known:
                    raise ValueError('unknown_contribution_owner:'+owner)
                return match.group(0) if self.has(owner) else ''
            return re.sub(r'(?m)^ *// feature:([a-z-]+):start\n.*?^ *// feature:\1:end\n',
                          select,data.decode().replace('\r\n','\n'),flags=re.S|re.M).encode()
        if module == 'agent' and path == 'AndroidManifest.xml' and not all(self.has(x) for x in ('notification-governance','notification-events')):
            ET.register_namespace('android','http://schemas.android.com/apk/res/android')
            ET.register_namespace('tools','http://schemas.android.com/tools')
            tree=ET.fromstring(data)
            application=tree.find('application')
            name='{http://schemas.android.com/apk/res/android}name'
            excluded=set()
            if not self.has('notification-governance'):
                excluded.update(('.NotificationGovernanceService','.NotificationGovernanceProvider','.NotificationManagementActivity'))
            if not self.has('notification-events'):
                excluded.add('.PhoneNotifications')
            for node in list(application):
                if node.get(name) in excluded:
                    application.remove(node)
            return ET.tostring(tree,encoding='utf-8')+b'\n'
        if module == 'host-rom' and path == 'agent/os/aios/agent/AgentFeatureInstaller.java':
            source='new NotificationListenerAccess()' if self.has('notification-events') else 'null'
            hooks='new NotificationGovernanceHooks()' if self.has('notification-governance') else 'null'
            minutes='new MinutesCodexBridge(context)' if self.has('minutes') else 'null'
            work='new NotificationPolicyImplementation(context,parameters).doWork()' if self.has('notification-governance') else 'androidx.work.ListenableWorker.Result.failure(new androidx.work.Data.Builder().putString("reason_code","not_packaged").build())'
            return (
                'package os.aios.agent;\n'
                'final class AgentFeatureInstaller {\n'
                ' private static boolean installed;\n'
                ' static synchronized void install(){if(installed)return;AgentFeatureBindings.install(SOURCE,HOOKS,AgentFeatureInstaller::minutes,AgentFeatureInstaller::notificationWork,AgentOperationContributions::create);installed=true;}\n'
                ' private static final NotificationAccess SOURCE='+source+';\n'
                ' static NotificationAccess notificationSource(){return SOURCE;}\n'
                ' static FeatureConsumer minutes(android.content.Context context){return '+minutes+';}\n'
                ' private static final NotificationHooks HOOKS='+hooks+';\n'
                ' static NotificationHooks notifications(){return HOOKS;}\n'
                ' static androidx.work.ListenableWorker.Result notificationWork(android.content.Context context,androidx.work.WorkerParameters parameters){\n'
                '  return '+work+';\n'
                ' }\n}\n').encode()
        if module == 'core-services' and path == 'Android.bp':
            text = data.decode()
            if not self.has('documents'):
                text = self.remove_list_values(text, 'static_libs', ['AIOSDocumentPdfBox'])
                text = self.remove_list_values(text, 'asset_dirs', ['third_party/documents/licenses'])
            if not self.has('photo-index'):
                text = self.remove_list_values(text, 'static_libs', ['AIOSLiteRtOfflineApi','androidx.exifinterface_exifinterface'])
                text = self.remove_list_values(text, 'jni_libs', ['libLiteRt','liblitert_jni','libLiteRtClGlAccelerator','libaios_search'])
            if not self.has('local-inference'):
                text = self.remove_list_values(text, 'jni_libs', ['libaios_llama_server'])
                text = self.remove_list_values(text, 'required', ['aios-local-text-model','aios-local-text-model-license'])
            return text.encode()
        if module == 'core-services' and path == 'AndroidManifest.xml':
            excluded = set()
            if not self.has('documents'):
                excluded.add('.documents.DocumentWorkerService')
            if not self.has('photo-index'):
                excluded.update(('.embedding.EmbeddingService','os.aios.core.embedding.DeviceModelValidation','os.aios.core.embedding.FaceModelActivity','os.aios.core.embedding.PhotoModelSelectionActivity'))
            if not self.has('local-inference'):
                excluded.add('os.aios.core.inference.LocalTextModelActivity')
            if not excluded and self.has('local-inference'):
                return data
            ET.register_namespace('android','http://schemas.android.com/apk/res/android')
            ET.register_namespace('tools','http://schemas.android.com/tools')
            tree = ET.fromstring(data)
            application = tree.find('application')
            name = '{http://schemas.android.com/apk/res/android}name'
            for node in list(application):
                if node.get(name) in excluded:
                    application.remove(node)
            if not self.has('local-inference'):
                application.attrib.pop('{http://schemas.android.com/apk/res/android}networkSecurityConfig',None)
            return ET.tostring(tree, encoding='utf-8') + b'\n'
        if module == 'host-rom' and path == 'core/os/aios/core/CoreFeatureBindings.java':
            lines = ['package os.aios.core;', 'import android.content.Context;',
                     '/** Generated trusted product bindings; excluded classes are not referenced. */',
                     'final class CoreFeatureBindings {',
                     ' private static boolean registered;',
                     ' static synchronized void register(Context context) {',
                     '  if (registered) return;',
                     '  os.aios.core.automation.AutomationFeatures.register();']
            if self.has('documents'):
                lines.append('  CoreFeatures.registerDocuments(os.aios.core.documents.DocumentCoordinator::get);')
            if self.has('local-inference'):
                lines.append('  CoreFeatures.registerInference(os.aios.core.inference.LocalTextInference::get);')
            if self.has('photo-index'):
                lines.extend(['  os.aios.core.embedding.BundledModels.initialize(context);',
                              '  CoreFeatures.registerPhotoSearch(new os.aios.core.search.IndexedPhotoSearch());'])
            lines.extend(['  registered = true;', ' }',
                          ' static org.json.JSONObject photoModelStatus(Context context) throws Exception {'])
            lines.append('  return new os.aios.core.embedding.ModelRegistry(context).status();' if self.has('photo-index')
                         else '  return new org.json.JSONObject().put("state","not_packaged").put("reason_code","photo_index_not_packaged");')
            lines.extend([' }','}'])
            return ('\n'.join(lines)+'\n').encode()
        if module == 'host-rom' and path == 'core/os/aios/core/automation/AutomationFeatures.java':
            return ('package os.aios.core.automation;\n'
                    'public final class AutomationFeatures {\n'
                    ' public static void register(){'+('AutomationService.registerPipelineFactory(NativePipelineRuntime::new);' if self.has('photo-index') else '')+'}\n'
                    '}\n').encode()
        return data

    def product_makefile(self, data):
        if not self.has('photo-index'):
            data=data.replace(b'include vendor/aios/search-models/models.mk', b'# Photo index/model payload excluded by product selection.')
        if not self.has('browser'):
            data=data.replace(b'PRODUCT_PACKAGES += agent-browser',b'# Structured browser runtime excluded by product selection.')
        if not self.has('miniapps'):
            data=data.replace(b'PRODUCT_PACKAGES += AIOSMiniApps',b'# MiniApp host excluded by product selection.')
        return data
