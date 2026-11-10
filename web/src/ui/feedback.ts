export function tapSound(enabled: boolean): void {
  if (!enabled || typeof window === "undefined") return;
  const Ctx = window.AudioContext;
  if (!Ctx) return;
  try {
    const audio = new Ctx();
    const oscillator = audio.createOscillator();
    const gain = audio.createGain();
    oscillator.frequency.value = 540;
    gain.gain.value = 0.03;
    oscillator.connect(gain);
    gain.connect(audio.destination);
    oscillator.start();
    oscillator.stop(audio.currentTime + 0.03);
    oscillator.onended = () => {
      void audio.close();
    };
  } catch {
    /* o navegador pode recusar áudio; não há dado para registrar */
  }
}
