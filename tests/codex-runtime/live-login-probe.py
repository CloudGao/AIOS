#!/usr/bin/env python3
"""Check official device-code transport, cancel immediately; never complete a user login."""
import json,runpy,sys
from pathlib import Path
api=runpy.run_path(str(Path(__file__).with_name('device-smoke.py')))
command,rpc=api['command'],api['rpc']
evidence={'kind':'Official device-code endpoint transport; no user authentication or inference'}
login=None
try:
    evidence['runtime']=command('start',mode='live')
    account=rpc('account/read')
    evidence['alreadyAuthenticated']=account.get('account') is not None
    if evidence['alreadyAuthenticated']:raise RuntimeError('Existing authentication detected; probe will not change it')
    login=rpc('account/login/start',type='chatgptDeviceCode')
    assert login.get('loginId') and login.get('userCode') and login.get('verificationUrl'), 'Incomplete device-code response'
    evidence['verificationUrl']=login['verificationUrl']
    evidence['status']='passed'
except Exception as error:
    evidence['status']='not_verified';evidence['error']=str(error)
finally:
    if login and login.get('loginId'):
        evidence['cancel']=rpc('account/login/cancel',loginId=login['loginId'])
    Path(sys.argv[1]).write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(evidence,ensure_ascii=False,indent=2))
