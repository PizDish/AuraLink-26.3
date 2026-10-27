const BASE='http://127.0.0.1:27363/v1/';
async function send(path,token,method='GET',payload=null) {
  const res=await fetch(BASE+path,{
    method,cache:'no-store',headers:{'X-AuraLink-Key':token,'Content-Type':'application/json'},
    body:payload==null?undefined:JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('AuraLink server '+res.status);
  return res.json();
}
chrome.runtime.onMessage.addListener((message,sender,reply)=>{
  if (message?.type !== 'auralink-tick') return;
  // Only Yandex music tabs can send status to the local mod.
  if (!/^https:\/\/music\.yandex\.(ru|com|kz)\//.test(sender.url||'')) return;
  (async()=>{
    const {pairingKey}=await chrome.storage.local.get('pairingKey');
    if (!/^[a-f0-9]{32}$/.test(pairingKey||'')) {reply({error:'Pairing required'});return;}
    await send('state',pairingKey,'POST',message.state);
    const result=await send('poll',pairingKey);
    reply({command:result.action||''});
  })().catch(error=>reply({error:String(error)}));
  return true;
});
