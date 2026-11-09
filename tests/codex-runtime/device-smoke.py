#!/usr/bin/env python3
"""Device-side runtime integration with a deterministic model fixture, NOT live inference.

Forward adb tcp:18767 to localabstract:aios_codex_runtime_debug first.
"""
import argparse, json, socket, time
from pathlib import Path

def command(op, **kwargs):
    with socket.create_connection(('127.0.0.1',18767),timeout=60) as s:
        s.sendall((json.dumps(dict(op=op,**kwargs))+'\n').encode())
        result=json.loads(s.makefile().readline())
    if not result.get('ok'):raise RuntimeError(result)
    return result['result']

def rpc(method, **params):return command('rpc',method=method,params=params)

def complete(thread, text, timeout=60):
    cursor=command('status')['cursor']
    result=rpc('turn/start',threadId=thread,input=[dict(type='text',text=text)])
    turn=result['turn']['id'];events=[];deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        batch=command('events',after=cursor);cursor=batch['cursor'];events+=batch['items']
        for item in batch['items']:
            p=item.get('params',{});t=p.get('turn',{})
            if item.get('method')=='turn/completed' and t.get('id')==turn:
                assert t['status']=='completed',t
                return events
        time.sleep(.2)
    raise TimeoutError('turn did not complete: '+json.dumps(events[-8:]))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    evidence={'kind':'Android local fixture integration; no real LLM','checks':[]}
    try:
        state=command('start',mode='fixture');assert state['running'] and state['androidUid']>=10000,state
        assert state['initialization']['platformOs']=='android',state
        evidence['start']=state;evidence['checks'].append('app UID native initialize')
        thread=command('thread/start')['thread']['id'];evidence['threadId']=thread
        events=complete(thread,'device-test')
        assert any(e.get('method')=='runtime/deviceToolExecuted' for e in events),events
        assert 'DEVICE_RESULT' in json.dumps(events),events
        evidence['checks'].append('Responses streaming plus actual Android device dynamic tool')
        evidence['toolEvents']=[e for e in events if e.get('method')=='runtime/deviceToolExecuted']
        complete(thread,'remember memory-sentinel-7391')
        oldPid=command('status')['pid'];command('stop');command('start',mode='fixture')
        assert command('status')['pid']!=oldPid
        rpc('thread/resume',threadId=thread)
        events=complete(thread,'what was the earlier memory marker?')
        assert 'memory-sentinel-7391' in json.dumps(events),events
        evidence['checks'].append('native process restart, persisted thread resume and previous input replay')
        cursor=command('status')['cursor']
        turn=rpc('turn/start',threadId=thread,input=[dict(type='text',text='slow-test')])['turn']['id']
        time.sleep(.3);rpc('turn/interrupt',threadId=thread,turnId=turn)
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            batch=command('events',after=cursor);cursor=batch['cursor']
            if any(e.get('method')=='turn/completed' and e.get('params',{}).get('turn',{}).get('status')=='interrupted' for e in batch['items']):break
            time.sleep(.2)
        else:raise AssertionError('missing interrupted turn notification')
        evidence['checks'].append('turn interruption')
        try:rpc('config/value/write',keyPath='features.shell_tool',value=True,mergeStrategy='replace')
        except RuntimeError:pass
        else:raise AssertionError('unexposed config mutation accepted')
        evidence['checks'].append('host rejects unexposed RPC')
        evidence['status']='passed'
    except Exception as e:
        evidence['status']='failed';evidence['error']=str(e)
        try:evidence['diagnostics']=command('diagnostics')
        except Exception:pass
        raise
    finally:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(evidence,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
