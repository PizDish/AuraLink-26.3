(() => {
  if (window.__AuraLinkContent) return;
  window.__AuraLinkContent = true;
  let status = {title:'',artist:'',album:'',playing:false,position:0,duration:0};
  window.addEventListener('message', event => {
    if (event.source !== window || event.origin !== location.origin) return;
    const data = event.data;
    if (!data || data.source !== 'auralink-metadata-v1') return;
    status = {
      title:String(data.title||'').slice(0,200),artist:String(data.artist||'').slice(0,200),
      album:String(data.album||'').slice(0,200),playing:!!data.playing,
      position:Math.floor(Number(data.position)||0),duration:Math.floor(Number(data.duration)||0)
    };
  });
  const words = {
    play_pause: ['play','pause','воспроизвести','пауза','продолжить','приостановить'],
    next:['next','следующий','дальше','пропустить'],
    previous:['previous','предыдущий','назад'],
    like:['like','favorite','лайк','нравится','сердце'],
    shuffle:['shuffle','перемешать','вперемешку','случайный'],
    repeat:['repeat','повтор','зациклить']
  };
  const selectors = {
    play_pause:['[data-testid*="play"]','[class*="player-controls__btn_play"]','[class*="PlayerControls_playButton"]'],
    next:['[data-testid*="next"]','[class*="player-controls__btn_next"]'],
    previous:['[data-testid*="prev"]','[class*="player-controls__btn_prev"]'],
    like:['[data-testid*="like"]','[class*="likeButton"]'],
    shuffle:['[data-testid*="shuffle"]'],repeat:['[data-testid*="repeat"]']
  };
  function doCommand(action) {
    if (!Object.hasOwn(words,action)) return false;
    const nodes = [...document.querySelectorAll('button,[role="button"]')];
    let target = nodes.find(button => {
      const label = [button.getAttribute('aria-label'),button.getAttribute('title'),
        button.getAttribute('data-testid')].filter(Boolean).join(' ').toLowerCase();
      return words[action].some(w => label.includes(w));
    });
    if (!target) {
      for (const selector of selectors[action]) {
        target = document.querySelector(selector);
        if (target) break;
      }
    }
    if (!target) return false;
    target.click();
    return true;
  }
  let busy = false;
  async function tick() {
    if (busy) return;
    busy = true;
    try {
      const response = await chrome.runtime.sendMessage({type:'auralink-tick',state:status});
      if (response?.command) {
        const ok=doCommand(response.command);
        if (!ok) console.debug('AuraLink: Yandex page control not found for',response.command);
      }
    } catch (_) { /* Extension is not paired or Minecraft is closed. */ }
    finally { busy=false; }
  }
  setInterval(tick,1300);
  tick();
})();
