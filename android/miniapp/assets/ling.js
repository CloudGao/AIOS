(() => {
  'use strict';
  const pending = new Map();
  LingBridge.onmessage = event => {
    const reply = JSON.parse(event.data);
    const item = pending.get(reply.id);
    if (!item) return;
    pending.delete(reply.id); clearTimeout(item.timer); item.resolve(reply.result);
  };
  const call = (capability, args = {}, options = {}) => {
    const id = options.requestId || crypto.randomUUID();
    if (pending.has(id)) return Promise.reject(new Error('Request already pending'));
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { pending.delete(id); resolve({state:'outcome_unknown',request_id:id,error:'No reply. Query result before retrying a write.'}); }, 120000);
      pending.set(id, {resolve, timer});
      try { LingBridge.postMessage(JSON.stringify({id, capability, arguments:args})); }
      catch (error) {clearTimeout(timer);pending.delete(id);reject(error);}
    });
  };
  Object.defineProperty(window, 'ling', {value:Object.freeze({apiVersion:1,call,capabilities:()=>call('capabilities'),result:requestId=>call('requests.get',{request_id:requestId})}),writable:false});
})();
