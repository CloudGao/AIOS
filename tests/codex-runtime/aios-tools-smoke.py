#!/usr/bin/env python3
"""Phone-local Binder contract tests + real ChatGPT tool calls. Keeps credentials private."""
import argparse, importlib.util, json, socket, time, uuid
from pathlib import Path

spec=importlib.util.spec_from_file_location('smoke',Path(__file__).with_name('device-smoke.py'))
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
command,rpc,complete=smoke.command,smoke.rpc,smoke.complete

def backend(cap,**params):
    with socket.create_connection(('127.0.0.1',18765),timeout=30) as s:
        s.sendall((json.dumps(dict(op='service',service=cap,params=params))+'\n').encode())
        result=json.loads(s.makefile().readline())
    assert result['state']=='succeeded',result
    return result['data']

def tool(name,args=None,rid=None):
    return command('tools/test',params=dict(op='tools/call',name=name,arguments=args or {},request_id=rid or str(uuid.uuid4())))

def observations(events):
    return [e['params'] for e in events if e.get('method')=='runtime/aiosToolExecuted']

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--live-only',action='store_true');a=p.parse_args()
    evidence={'kind':'On-device AIOS Binder + real ChatGPT tools','checks':[]}
    initial=backend('display.read')['brightness'];changed=None
    try:
        if not a.live_only:contract_tests(evidence,initial)
        command('start',mode='live')
        account=rpc('account/read').get('account');assert account and account['type']=='chatgpt','ChatGPT account unavailable'
        evidence['checks'].append('ChatGPT account available on device')
        thread=command('thread/start')['thread']['id'];evidence['threadId']=thread
        events=complete(thread,'请实际调用手机工具读取当前电量、网络状态和屏幕亮度，用中文简短汇报。不要修改任何设置。',timeout=150)
        calls=observations(events);evidence['readCalls']=calls
        assert {'aios_battery_read','aios_network_read','aios_display_read'}.issubset({e['tool'] for e in calls if e['state']=='succeeded'}),calls
        evidence['checks'].append('Real model used phone Binder tools for battery, network and brightness')
        changed=initial+1 if initial<255 else initial-1
        events=complete(thread,f'这是一次已授权的亮度集成测试。请先读取当前屏幕亮度，再调用工具将亮度设置为整数 {changed}（0到255的原始值，不是百分比），最后重新读取确认。',timeout=150)
        calls=observations(events);evidence['writeCalls']=calls
        assert any(e['tool']=='aios_display_brightness_set' and e['state']=='succeeded' for e in calls),calls
        assert backend('display.read')['brightness']==changed
        events=complete(thread,f'测试完成，请将屏幕亮度恢复为原始整数 {initial}，并重新读取确认。',timeout=150)
        calls=observations(events);evidence['restoreCalls']=calls
        assert any(e['tool']=='aios_display_brightness_set' and e['state']=='succeeded' for e in calls),calls
        assert backend('display.read')['brightness']==initial
        changed=None
        evidence['checks'].append('Real model changed brightness, independent readback verified it, then restored original value')
        evidence['status']='passed'
    except Exception as e:
        evidence['status']='failed';evidence['error']=str(e);raise
    finally:
        if changed is not None:
            current=backend('display.read')['brightness']
            if current==changed:backend('display.brightness.set',value=initial)
        command('start',mode='live')
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(evidence,ensure_ascii=False,indent=2))

def contract_tests(evidence,initial):
    changed=None
    try:
        command('start',mode='fixture')
        catalog=command('tools/test',params={'op':'tools/list'})
        assert catalog['state']=='succeeded' and len(catalog['tools'])==9,catalog
        evidence['tools']=[t['name'] for t in catalog['tools']]
        assert tool('aios_battery_read')['state']=='succeeded'
        assert tool('aios_display_read',{'unexpected':1})['state']=='failed'
        assert tool('aios_audio_set',{'stream':1,'value':1,'expected':1})['state']=='failed'
        assert tool('aios_files_delete',{'name':'not-executed'})['state']=='failed'
        assert tool('aios_display_brightness_set',{'value':initial,'expected':(initial+1)%256})['state']=='failed'
        evidence['checks'].append('Binder discovery, reads, argument validation, unexposed tool rejection and stale-state rejection')
        changed=initial+1 if initial<255 else initial-1
        rid=str(uuid.uuid4());args={'value':changed,'expected':initial}
        first=tool('aios_display_brightness_set',args,rid);assert first['state']=='succeeded',first
        assert backend('display.read')['brightness']==changed
        assert tool('aios_display_brightness_set',args,rid)==first
        assert tool('aios_display_brightness_set',{'value':initial,'expected':changed},rid)['state']=='failed'
        restored=tool('aios_display_brightness_set',{'value':initial,'expected':changed});assert restored['state']=='succeeded',restored
        changed=None
        evidence['checks'].append('Actual brightness write/readback, identical request replay, conflicting request rejection, restoration')
    finally:
        if changed is not None:
            current=backend('display.read')['brightness']
            if current==changed:backend('display.brightness.set',value=initial)
        command('start',mode='live')

if __name__=='__main__':main()
