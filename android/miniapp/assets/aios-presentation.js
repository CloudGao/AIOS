(() => {
 if(window.__aiosPresentationInstalled)return;window.__aiosPresentationInstalled=true;
 const clean=s=>s.replace(/codex/gi,'AIOS');
 const attrs=['title','alt','aria-label','aria-description','placeholder'];
 const visit=n=>{
  if(n.nodeType===3){if(!n.parentElement?.closest('script,style,textarea,input,[contenteditable="true"]')){let v=clean(n.data);if(v!==n.data)n.data=v;}return;}
  if(n.nodeType!==1&&n.nodeType!==9)return;
  if(n.nodeType===1){for(const a of attrs)if(n.hasAttribute(a)){let v=n.getAttribute(a),c=clean(v);if(c!==v)n.setAttribute(a,c);}}
  for(const c of n.childNodes)visit(c);
 };
 new MutationObserver(ms=>{for(const m of ms){if(m.type==='childList')for(const n of m.addedNodes)visit(n);else visit(m.target);}}).observe(document,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:attrs});
 visit(document);
})();
