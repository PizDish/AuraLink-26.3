const input=document.getElementById('key');
const result=document.getElementById('result');
chrome.storage.local.get('pairingKey').then(({pairingKey})=>{if(pairingKey)input.value=pairingKey;});
document.getElementById('save').addEventListener('click',async()=>{
  const token=input.value.toLowerCase().trim();
  if(!/^[a-f0-9]{32}$/.test(token)){result.textContent='Нужно ровно 32 символа 0-9, a-f.';return;}
  await chrome.storage.local.set({pairingKey:token});
  result.textContent='Сохранено. Обнови вкладку Яндекс Музыки.';
});
document.getElementById('open').addEventListener('click',()=>chrome.tabs.create({url:'https://music.yandex.ru/'}));
