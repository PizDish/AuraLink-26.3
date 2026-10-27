// Executes in the Yandex Music page context; reports standard MediaSession metadata only.
// No login cookies, tokens, account identifiers or network traffic are inspected.
(() => {
  if (window.__AuraLinkPageMetadata) return;
  window.__AuraLinkPageMetadata = true;
  function collect() {
    const media = navigator.mediaSession;
    const data = media?.metadata;
    let position = 0;
    let duration = 0;
    try {
      // MediaSession#getPositionState is not supported consistently by all browsers.
      const current = document.querySelector('audio');
      if (current) { position = Number(current.currentTime) || 0; duration = Number(current.duration) || 0; }
    } catch (_) { }
    const playing = ![...document.querySelectorAll('audio')].some(a => !a.paused)
      ? !(document.querySelector('[aria-label="Пауза"],[aria-label="Pause"],[title="Пауза"]') == null)
      : true;
    window.postMessage({ source: 'auralink-metadata-v1', title: data?.title || '',
      artist: data?.artist || '', album: data?.album || '', position, duration, playing }, location.origin);
  }
  setInterval(collect, 1300);
  collect();
})();
