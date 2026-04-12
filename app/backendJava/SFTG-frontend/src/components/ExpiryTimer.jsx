import { useEffect, useState } from "react";

export default function ExpiryTimer({ expiryAt }) {
  const calc = () => {
    const diff = new Date(expiryAt) - new Date();
    if (diff <= 0) return null;
    const h = Math.floor(diff / 3600000);
    const m = Math.floor((diff % 3600000) / 60000);
    const s = Math.floor((diff % 60000) / 1000);
    return h > 0 ? `${h}h ${m}m` : `${m}:${s < 10 ? "0" : ""}${s}`;
  };

  const [timeLeft, setTimeLeft] = useState(calc);

  useEffect(() => {
    const t = setInterval(() => {
      const v = calc();
      setTimeLeft(v);
      if (!v) clearInterval(t);
    }, 1000);
    return () => clearInterval(t);
  }, [expiryAt]);

  if (!timeLeft) return <span style={{ color: "#EF4444", fontWeight: 500 }}>Expired</span>;
  return <span style={{ color: "#F59E0B", fontWeight: 600, fontFamily: "'JetBrains Mono', monospace" }}>⏱ {timeLeft}</span>;
}