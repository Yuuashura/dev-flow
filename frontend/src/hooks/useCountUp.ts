import { useEffect, useRef, useState } from 'react';

/**
 * Menghitung naik dari 0 ke `target`.
 *
 * CSS tidak bisa menganimasikan isi teks, jadi ini satu-satunya bagian gerak yang
 * butuh JavaScript. Satu loop requestAnimationFrame per angka, berhenti sendiri saat
 * selesai — tidak ada interval yang menggantung.
 *
 * Menghormati prefers-reduced-motion: kalau user mematikan gerak, durasinya nol dan
 * frame pertama langsung mendarat di nilai akhir. Blok reduced-motion di index.css
 * hanya mengurus CSS, jadi pengecekannya harus diulang di sini.
 *
 * Semua penulisan state terjadi di dalam callback rAF, bukan di badan effect —
 * setState sinkron di sana memicu render berantai.
 */
export function useCountUp(target: number, durationMs = 700): number {
  const [value, setValue] = useState(0);
  const frame = useRef<number | undefined>(undefined);

  useEffect(() => {
    const end = Number.isFinite(target) ? target : 0;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    // Durasi nol: frame pertama menghitung t = 1 dan langsung mendarat di `end`.
    const duration = reduced || end === 0 ? 0 : durationMs;

    let start: number | undefined;
    const tick = (now: number) => {
      if (start === undefined) start = now;
      const t = duration === 0 ? 1 : Math.min((now - start) / duration, 1);
      // ease-out: cepat di awal, melambat di akhir — terbaca seperti berhenti,
      // bukan terpotong.
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(Math.round(end * eased));
      if (t < 1) {
        frame.current = requestAnimationFrame(tick);
      }
    };
    frame.current = requestAnimationFrame(tick);

    return () => {
      if (frame.current !== undefined) cancelAnimationFrame(frame.current);
    };
  }, [target, durationMs]);

  return value;
}
