import { useState, useEffect } from "react";

export default function FileCard({ file }) {

  const [timeLeft, setTimeLeft] = useState("");

  useEffect(() => {

    const interval = setInterval(() => {

      const expiry = new Date(file.expiresAt).getTime();
      const now = new Date().getTime();

      const distance = expiry - now;

      if (distance <= 0) {
        setTimeLeft("Expired");
        clearInterval(interval);
        return;
      }

      const minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
      const seconds = Math.floor((distance % (1000 * 60)) / 1000);

      setTimeLeft(
        `${minutes.toString().padStart(2,"0")}:${seconds.toString().padStart(2,"0")}`
      );

    }, 1000);

    return () => clearInterval(interval);

  }, [file.expiresAt]);

  return (

    <div className="file-card">

      <h3>{file.originalFilename}</h3>

      <p>Downloads left: {file.remainingDownloads}</p>

      <p>Expires in: {timeLeft}</p>

    </div>

  );
}

