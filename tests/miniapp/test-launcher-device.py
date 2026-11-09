from pathlib import Path
import subprocess,json,sys,time,re,os,xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding='utf-8')
root=Path(os.environ['AIOS_MINIAPP_VALIDATION_DIR']);root.mkdir(parents=True,exist_ok=True);adb=['adb','-s',os.environ['AIOS_ADB_SERIAL']]
checks=[]
def run(*args,raw=False):
 p=subprocess.run(adb+list(args),capture_output=True,check=True);return p.stdout if raw else p.stdout.decode('utf-8',errors='replace').strip()
def ui(name):
 for attempt in range(8):
  raw=run('shell','am','instrument','-w','os.aios.launcherprobe/os.aios.miniapp.MiniAppUiProbe');start=raw.find('<?xml');end=raw.find('</hierarchy>')+len('</hierarchy>')
  if start<0 or 'android.webkit.WebView' not in raw or any(n.get('text') for n in ET.fromstring(raw[start:end]).iter('node')):break
  time.sleep(.6)
 if start<0:raise RuntimeError(raw)
 xml=raw[start:end];(root/(name+'.xml')).write_text(xml,encoding='utf-8');(root/(name+'.png')).write_bytes(run('exec-out','screencap','-p',raw=True));return ET.fromstring(xml)
def nodes(tree,label=None):
 result=[]
 for n in tree.iter('node'):
  b=list(map(int,re.findall(r'-?\d+',n.get('bounds',''))))
  if len(b)!=4 or b[0]>=b[2] or b[1]>=b[3] or b[2]<=0 or b[3]<=0:continue
  if label is None or n.get('text')==label or n.get('content-desc')==label:result.append(n)
 return result
def labels(tree):return [n.get('text') or n.get('content-desc') for n in nodes(tree) if n.get('text') or n.get('content-desc')]
def tap(label,long=False,tree=None):
 tree=tree if tree is not None else ui('flow-current');found=nodes(tree,label)
 if len(found)>1:
  nonedit=[n for n in found if n.get('class')!='android.widget.EditText']
  if nonedit:found=nonedit
 if len(found)>1:
  clickable=[n for n in found if n.get('clickable')=='true']
  if len(clickable)==1:found=clickable
 if len(found)!=1:raise AssertionError('Expected one '+label+': '+str(len(found))+' labels='+str(labels(tree)))
 b=list(map(int,re.findall(r'-?\d+',found[0].get('bounds'))));x=(b[0]+b[2])//2;y=(b[1]+b[3])//2
 if long:run('shell','input','swipe',str(x),str(y),str(x),str(y),'850')
 else:run('shell','input','tap',str(x),str(y))
 time.sleep(.6)
def home():run('shell','input','keyevent','KEYCODE_HOME');time.sleep(.6)
def apps():home();run('shell','input','swipe','540','2100','540','600','350');time.sleep(.6)
def search(query):
 apps();tree=ui('flow-apps');edits=[n for n in nodes(tree) if n.get('class')=='android.widget.EditText'];assert edits
 b=list(map(int,re.findall(r'\d+',edits[0].get('bounds'))));run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));run('shell','am','instrument','-w','-e','query',query,'os.aios.launcherprobe/os.aios.miniapp.MiniAppUiProbe');time.sleep(.7);return ui('flow-search')
def fixture(action):
 result=run('shell','am','instrument','-w','-e','action',action,'os.aios.miniapp.acceptance/os.aios.miniapp.MiniAppLauncherFixture');(root/('fixture-'+action+'.txt')).write_text(result,encoding='utf-8');assert '"action":"'+action+'"' in result,result;time.sleep(1);return result
def check(name,fn):
 try:fn();checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
 except Exception as e:checks.append({'name':name,'passed':False,'error':str(e)});print('FAIL',name,str(e),flush=True)
 (root/('ui-results-'+sys.argv[1]+'.json')).write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
def expect(tree,text):assert text.casefold() in [s.casefold() for s in labels(tree)],(text,labels(tree))
def open_fixture(name):tree=search('Mini');tap(name,tree=tree);return ui('opened-'+name.replace(' ','-'))
mode=sys.argv[1]
if mode=='host':
 def direct(id,manage=False):
  db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'));gen=db['apps'][id]['generation'];component='ManageActivity' if manage else 'HostActivity';run('shell','am','start','-W','-n','os.aios.miniapp/.'+component,'-a','android.intent.action.VIEW','-d','aios-miniapp://app/'+id+'/'+gen,'--es','app_id',id,'--es','generation',gen,'-f','0x10000000' if manage else '0x10080000');time.sleep(.5);return ui('direct-'+component+'-'+id)
 check('seed isolated management fixtures',lambda:fixture('seed'))
 def fill(id):
  t=direct(id);expect(t,'Empty');tap('Save fixture',tree=t);expect(ui('saved-'+id),'Saved')
 check('save A data',lambda:fill('launcher-test-a'))
 check('save B independently',lambda:fill('launcher-test-b'))
 def permissions():
  t=direct('launcher-test-a',True);expect(t,'Mini Launcher A');expect(t,'版本 1 · 小程序');tap('移除本小程序的通知',tree=t);db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'));assert db['apps']['launcher-test-a']['grants'].get('notifications.cancel') is True;tap('移除本小程序的通知');db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'));assert db['apps']['launcher-test-a']['grants'].get('notifications.cancel') is False
 check('individual info and permission controls',permissions)
 def clear():
  tap('清除数据');tap('清除数据');time.sleep(2);expect(direct('launcher-test-a'),'Empty');expect(direct('launcher-test-b'),'saved')
 check('clear A data preserves B data',clear)
 def english():
  fixture('locale-en');t=direct('launcher-test-b',True);expect(t,'Permissions');expect(t,'Clear data');expect(t,'Uninstall');expect(t,'Version 1 · Mini app')
 check('English management UI',english)
 check('restore default locale',lambda:fixture('locale-reset'))
 def uninstall():
  t=direct('launcher-test-a',True);tap('卸载',tree=t);tap('卸载');time.sleep(2);db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'));assert 'launcher-test-a' not in db['apps'];expect(direct('launcher-test-b'),'saved')
 check('individual uninstall preserves B data',uninstall)
 check('cleanup management fixtures',lambda:fixture('cleanup'))
elif mode=='first':
 check('seed two temporary mini apps',lambda:fixture('seed'))
 def no_library():
  apps();t=ui('integrated-apps');assert '小程序' not in labels(t), 'Legacy library entry remains in Apps or predictions'
 check('legacy library is absent from app list and predictions',no_library)
 def search_both():
  tree=search('Mini');expect(tree,'Mini Launcher A');expect(tree,'Mini Launcher B');assert any('小程序' in n.get('content-desc','') for n in nodes(tree,'Mini Launcher A'));ui('search-two-independent')
 check('search returns independent entries with accessible type',search_both)
 def open_a():
  t=open_fixture('Mini Launcher A');expect(t,'Mini Launcher A');expect(t,'Empty');assert '‹ 小程序' not in labels(t);tap('Save fixture',tree=t);expect(ui('fixture-a-saved'),'Saved')
 check('A opens directly and saves local data',open_a)
 def open_b():
  t=open_fixture('Mini Launcher B');expect(t,'Mini Launcher B');expect(t,'Empty');tap('Save fixture',tree=t);expect(ui('fixture-b-saved'),'Saved')
 check('B opens independently and has separate data',open_b)
 def manage():
  t=search('Mini');tap('Mini Launcher A',long=True,tree=t);t=ui('long-press-a');expect(t,'应用信息');expect(t,'卸载');tap('应用信息',tree=t);t=ui('manage-a');expect(t,'Mini Launcher A');expect(t,'版本 1 · 小程序');expect(t,'权限')
 check('long press exposes individual info and uninstall',manage)
 def add(name):
  t=search('Mini');tap(name,long=True,tree=t);t=ui('add-menu');tap('添加到主屏幕',tree=t);time.sleep(1);expect(ui('home-'+name.replace(' ','-')),name)
 check('add A to home',lambda:add('Mini Launcher A'))
 check('add B to home',lambda:add('Mini Launcher B'))
 def restart():
  run('shell','am','force-stop','com.android.launcher3');home();time.sleep(2);t=ui('home-after-launcher-restart');expect(t,'Mini Launcher A');expect(t,'Mini Launcher B');tap('Mini Launcher A',tree=t);expect(ui('home-a-opened'),'saved')
 check('both home icons survive launcher process restart and open correct data',restart)
 def native():
  t=search('AudioFX');expect(t,'AudioFX');tap('AudioFX',tree=t);t=ui('native-app-opened');assert 'AudioFX' in labels(t)
 check('ordinary app search and launch still work',native)
elif mode=='native':
 def native():
  t=search('AudioFX');expect(t,'AudioFX');tap('AudioFX',tree=t);t=ui('native-app-opened');assert 'AudioFX' in labels(t)
 check('ordinary app search and launch still work',native)
elif mode=='drag-final':
 check('cleanup earlier fixtures',lambda:fixture('cleanup'))
 check('seed folder fixtures',lambda:fixture('seed'))
 def pin(name):
  t=search('Mini');tap(name,long=True,tree=t);tap('添加到主屏幕');time.sleep(1)
 check('pin folder A',lambda:pin('Mini Launcher A'))
 check('pin folder B',lambda:pin('Mini Launcher B'))
 def folder():
  home();t=ui('folder-before');a=nodes(t,'Mini Launcher A')[0];b=nodes(t,'Mini Launcher B')[0]
  def center(n):
   x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));return str((x1+x2)//2),str((y1+y2)//2)
  ax,ay=center(a);bx,by=center(b);assert (ax,ay,bx,by)==('126','392','333','392'), 'Unexpected grid; use observed spring-loaded geometry'
  # Pixel 10 baseline: spring-loaded workspace scales and offsets the target cell.
  run('shell','am','instrument','-w','-e','drag',ax+','+ay+',382,585','os.aios.launcherprobe/os.aios.miniapp.MiniAppUiProbe');time.sleep(1);t=ui('final-folder-created');assert any(n.get('content-desc','').startswith('文件夹：') and '2 项' in n.get('content-desc','') for n in nodes(t)),labels(t)
  (root/'final-drag-preview.png').write_bytes(run('exec-out','cat','/data/user/0/os.aios.launcherprobe/files/drag.png',raw=True))
 check('drag two individual icons into a native folder',folder)
elif mode=='folder-verify':
 def opened():
  home();t=ui('folder-closed');folder=[n for n in nodes(t) if n.get('content-desc','').startswith('文件夹：') and '2 项' in n.get('content-desc','')];assert len(folder)==1
  tap(folder[0].get('content-desc'),tree=t);t=ui('folder-opened');expect(t,'Mini Launcher A');expect(t,'Mini Launcher B');tap('Mini Launcher A',tree=t);expect(ui('folder-a-opened'),'Mini Launcher A')
 check('folder contains independent A and B and opens A',opened)
 def restart():
  run('shell','am','force-stop','com.android.launcher3');home();time.sleep(2);opened()
 check('folder and both entries survive Launcher restart',restart)
 def remove():
  home();t=ui('folder-remove-before');f=[n for n in nodes(t) if n.get('content-desc','').startswith('文件夹：') and '2 项' in n.get('content-desc','')][0];tap(f.get('content-desc'),tree=t);tap('Mini Launcher A',long=True);tap('移除');home();t=ui('folder-after-remove');expect(t,'Mini Launcher B');assert 'Mini Launcher A' not in labels(t);t=search('Mini');expect(t,'Mini Launcher A');expect(t,'Mini Launcher B')
 check('remove A from folder keeps A installed and B on home',remove)
elif mode=='fallback':
 def unavailable():
  try:
   run('shell','pm','disable-user','--user','0','os.aios.miniapp');run('shell','am','force-stop','com.android.launcher3');home();time.sleep(2);t=search('AudioFX');expect(t,'AudioFX');tap('AudioFX',tree=t);expect(ui('native-with-host-disabled'),'AudioFX')
  finally:run('shell','pm','default-state','--user','0','os.aios.miniapp')
 check('ordinary Launcher works while host is unavailable',unavailable)
 def recovered():
  home();time.sleep(1);t=search('记事本');tap('记事本, 小程序',tree=t);t=ui('host-recovered');assert any(n.get('class')=='android.webkit.WebView' for n in nodes(t))
 check('mini apps return when host becomes available',recovered)
elif mode=='update':
 check('publish new versions for fixture apps',lambda:fixture('update'))
 def renamed():
  t=search('Mini');expect(t,'Mini Launcher Updated');assert 'Mini Launcher A' not in labels(t);tap('Mini Launcher Updated',tree=t);expect(ui('updated-a-opened'),'saved')
 check('updated name refreshes and stored data survives',renamed)
 def home_update():
  home();t=ui('updated-home');expect(t,'Mini Launcher Updated');expect(t,'Mini Launcher B')
 check('home entries refresh after update',home_update)
 check('stale generation cannot open content',lambda:fixture('stale'))
elif mode=='host-update':
 def host_update():
  assert 'Success' in run('install','-r',str(root/'apps/AIOSMiniApps.apk'));time.sleep(2);home();t=ui('home-after-host-apk-update');expect(t,'Mini Launcher A');expect(t,'Mini Launcher B');tap('Mini Launcher A',tree=t);expect(ui('a-after-host-apk-update'),'saved')
 check('host APK update retains separate home intents and data',host_update)
elif mode=='manage':
 def permissions():
  t=search('Mini');tap('Mini Launcher Updated',long=True,tree=t);t=ui('manage-menu-updated');tap('应用信息',tree=t);t=ui('permissions-before');tap('移除本小程序的通知',tree=t)
  db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'));assert db['apps']['launcher-test-a']['grants'].get('notifications.cancel') is True
  tap('移除本小程序的通知');db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'));assert db['apps']['launcher-test-a']['grants'].get('notifications.cancel') is False
 check('per-app permission can be granted and revoked',permissions)
 def clear():
  tap('清除数据');t=ui('clear-confirm');tap('清除数据',tree=t);time.sleep(2);t=ui('clear-complete');tap('打开',tree=t);expect(ui('a-after-clear'),'Empty');t=open_fixture('Mini Launcher B');expect(t,'saved')
 check('clear A data keeps installation and B data',clear)
 def uninstall():
  t=search('Mini');tap('Mini Launcher Updated',long=True,tree=t);tap('卸载');t=ui('uninstall-confirm');tap('卸载',tree=t);time.sleep(2);t=search('Mini');assert 'Mini Launcher Updated' not in labels(t);expect(t,'Mini Launcher B');home();t=ui('home-after-a-uninstall');assert 'Mini Launcher Updated' not in labels(t);expect(t,'Mini Launcher B');tap('Mini Launcher B',tree=t);expect(ui('b-after-a-uninstall'),'saved')
 check('uninstall removes A from Apps and home while keeping B data',uninstall)
elif mode=='locale':
 check('switch mini-app host to English for resource verification',lambda:fixture('locale-en'))
 def english():
  t=search('Mini');tap('Mini Launcher B',long=True,tree=t);tap('应用信息');t=ui('manage-english');expect(t,'Permissions');expect(t,'Clear data');expect(t,'Uninstall');expect(t,'Version 2 · Mini app')
 check('English management resources render',english)
 check('restore app locale to system default',lambda:fixture('locale-reset'))
elif mode=='real':
 def calendar():
  t=search('日历');tap('日历, 小程序',tree=t);t=ui('real-calendar');assert '‹ 小程序' not in labels(t);assert any(n.get('class')=='android.webkit.WebView' for n in nodes(t));expect(t,'今天')
 check('existing calendar launches directly',calendar)
 def notes():
  t=search('记事本');tap('记事本, 小程序',tree=t);t=ui('real-notepad');assert '‹ 小程序' not in labels(t);assert any(n.get('class')=='android.webkit.WebView' for n in nodes(t));assert len(nodes(t))>5
 check('existing notepad launches directly',notes)
 def retained():
  before=json.loads((root/'before.json').read_text(encoding='utf-8'));db=json.loads(run('shell','cat','/data/user/0/os.aios.miniapp/files/miniapps/registry.json'))
  for id,old in before['apps'].items():assert db['apps'][id]['generation']==old['generation'] and db['apps'][id]['manifest']==old['manifest']
  packages=run('shell','pm','list','packages').splitlines();assert packages.count('package:os.aios.miniapp')==1;assert not any('launcher-test-' in p for p in packages)
 check('original app generations and versions remain; Android sees one host',retained)
elif mode=='cleanup':
 check('remove temporary fixtures',lambda:fixture('cleanup'));home();ui('final-home')
elif mode=='show':
 t=ui(sys.argv[2]);print(json.dumps([{'text':n.get('text'),'desc':n.get('content-desc'),'bounds':n.get('bounds')} for n in nodes(t) if n.get('text') or n.get('content-desc')],ensure_ascii=False))
elif mode=='search':
 t=search(sys.argv[2]);print(json.dumps(labels(t),ensure_ascii=False))
