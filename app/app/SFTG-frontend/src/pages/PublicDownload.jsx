import { useState } from "react";
import { useParams } from "react-router-dom";
import {
  Box, Card, CardContent, Typography, Button, Stack,
  LinearProgress, Alert, Chip
} from "@mui/material";
import DownloadIcon      from "@mui/icons-material/Download";
import ShieldIcon        from "@mui/icons-material/Shield";
import CheckCircleIcon   from "@mui/icons-material/CheckCircle";
import api from "../api/axios";

export default function PublicDownload() {
  const { token } = useParams();
  const [progress, setProgress]   = useState(0);
  const [downloading, setDownloading] = useState(false);
  const [done, setDone]           = useState(false);
  const [error, setError]         = useState("");

  const handleDownload = async () => {
    setDownloading(true); setError("");
    try {
      const res = await api.get(`/share/download/${token}`, {
        responseType: "blob",
        onDownloadProgress: e => e.total && setProgress(Math.round((e.loaded * 100) / e.total))
      });

      const cd = res.headers["content-disposition"];
      let filename = "download";
      if (cd) {
        const m = cd.match(/filename="?([^"]+)"?/);
        if (m) filename = m[1];
      }

      const url = window.URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement("a");
      a.href = url; a.download = filename; a.click();
      window.URL.revokeObjectURL(url);
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.message || "Download failed. Link may be expired or invalid.");
    } finally { setDownloading(false); }
  };

  return (
    <Box sx={{
      minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center",
      background: "linear-gradient(135deg, #0A0F1E 0%, #1E3A8A 55%, #0EA5E9 100%)", p: 2,
    }}>
      <Card sx={{ width: { xs: "100%", sm: 420 }, borderRadius: 3, boxShadow: "0 30px 60px rgba(0,0,0,0.45)" }}>
        <CardContent sx={{ p: 4, textAlign: "center" }}>
          <Box sx={{
            width: 62, height: 62, borderRadius: "50%",
            background: "linear-gradient(135deg, #1E3A8A, #06B6D4)",
            display: "flex", alignItems: "center", justifyContent: "center", mx: "auto", mb: 2,
          }}>
            <ShieldIcon sx={{ color: "#fff", fontSize: 30 }} />
          </Box>

          <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" mb={0.5}>
            Secure Download
          </Typography>
          <Typography color="text.secondary" variant="body2" mb={2.5}>
            You've been shared a file via SFTG
          </Typography>

          <Chip label={`${token?.slice(0, 8)}...`} size="small"
            sx={{ background: "#1E3A8A12", color: "#1E3A8A", mb: 3,
              fontFamily: "'JetBrains Mono', monospace", fontSize: "0.75rem" }} />

          {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2, textAlign: "left" }}>{error}</Alert>}

          {done ? (
            <Stack alignItems="center" spacing={1}>
              <CheckCircleIcon sx={{ fontSize: 52, color: "#10B981" }} />
              <Typography fontWeight={700} color="#10B981">Download Complete!</Typography>
              <Button variant="outlined" size="small" onClick={() => { setDone(false); setProgress(0); }}
                sx={{ mt: 1, borderRadius: 2, textTransform: "none", borderColor: "#CBD5E1", color: "#64748B" }}>
                Download Again
              </Button>
            </Stack>
          ) : (
            <>
              {downloading && (
                <Box mb={2.5}>
                  <Stack direction="row" justifyContent="space-between" mb={1}>
                    <Typography variant="caption" color="text.secondary">Downloading...</Typography>
                    <Typography variant="caption" fontWeight={700} fontFamily="'JetBrains Mono', monospace">{progress}%</Typography>
                  </Stack>
                  <LinearProgress
                    variant={progress > 0 ? "determinate" : "indeterminate"}
                    value={progress}
                    sx={{ height: 8, borderRadius: 4,
                      "& .MuiLinearProgress-bar": { background: "linear-gradient(90deg, #1E3A8A, #06B6D4)" } }}
                  />
                </Box>
              )}
              <Button fullWidth variant="contained" size="large"
                startIcon={<DownloadIcon />} onClick={handleDownload} disabled={downloading}
                sx={{
                  py: 1.5, borderRadius: 2, textTransform: "none",
                  background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)",
                  fontWeight: 700, fontSize: "1rem", fontFamily: "'DM Sans', sans-serif",
                }}>
                {downloading ? "Downloading..." : "Download File"}
              </Button>
            </>
          )}

          <Typography variant="caption" color="text.secondary" display="block" mt={2.5}>
            🔒 Secured by SFTG — Secure File Transfer Gateway
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}