import { useState, useRef } from "react";
import {
  Box, Card, CardContent, Typography, Button, Stack,
  LinearProgress, Alert, Chip
} from "@mui/material";
import CloudUploadIcon   from "@mui/icons-material/CloudUpload";
import InsertDriveFileIcon from "@mui/icons-material/InsertDriveFile";
import CheckCircleIcon   from "@mui/icons-material/CheckCircle";
import api from "../api/axios";
import PublicLinkModal from "../components/PublicLinkModal";

export default function Upload() {
  const [file, setFile]             = useState(null);
  const [uploading, setUploading]   = useState(false);
  const [progress, setProgress]     = useState(0);
  const [error, setError]           = useState("");
  const [uploadedFile, setUploadedFile] = useState(null);
  const [shareToken, setShareToken] = useState(null);
  const [showModal, setShowModal]   = useState(false);
  const [dragOver, setDragOver]     = useState(false);
  const ref = useRef();

  const fmt = (b) => b < 1048576 ? (b/1024).toFixed(1)+" KB" : (b/1048576).toFixed(2)+" MB";

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true); setError(""); setProgress(0);
    const fd = new FormData();
    fd.append("file", file);
    try {
      const up = await api.post("/files/upload", fd, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: e => setProgress(Math.round((e.loaded * 100) / e.total))
      });
      setUploadedFile(up.data);
      const share = await api.post("/share/generate", { storedFilename: up.data.storedFilename });
      setShareToken(share.data);
      setShowModal(true);
    } catch (err) {
      setError(err.response?.data?.message || "Upload failed. Please try again.");
    } finally { setUploading(false); }
  };

  const reset = () => { setFile(null); setUploadedFile(null); setShareToken(null); setError(""); setProgress(0); };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" color="#0A0F1E" mb={3}>
        Upload File
      </Typography>
      {/* ClamAV Notice for Recruiters */}
      <Box sx={{
        mb: 2, p: 2,
        border: "1px solid #F59E0B",
        borderRadius: 2,
        background: "rgba(245,158,11,0.06)",
        display: "flex",
        gap: 1.5,
        alignItems: "flex-start"
      }}>
        <Typography sx={{ fontSize: "1.2rem" }}>⚠️</Typography>
        <Box>
          <Typography variant="body2" fontWeight={700} color="#B45309">
            Virus Scanning Disabled on Free Tier
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" mt={0.3}>
            ClamAV requires ~1GB RAM which exceeds free hosting limits.
            The integration is fully implemented in the backend code —
            it runs correctly in local Docker environment.
            For a complete demo with live virus scanning, contact me directly.
          </Typography>
        </Box>
      </Box>


      {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}

      {!uploadedFile ? (
        <Card sx={{ borderRadius: 3, boxShadow: "0 2px 16px rgba(0,0,0,0.07)" }}>
          <CardContent sx={{ p: 4 }}>
            {/* Drop zone */}
            <Box
              onDragOver={e => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={e => { e.preventDefault(); setDragOver(false); setFile(e.dataTransfer.files[0]); }}
              onClick={() => ref.current.click()}
              sx={{
                border: `2px dashed ${dragOver ? "#06B6D4" : file ? "#10B981" : "#CBD5E1"}`,
                borderRadius: 3, p: { xs: 4, md: 7 }, textAlign: "center", cursor: "pointer",
                background: dragOver ? "rgba(6,182,212,0.04)" : file ? "rgba(16,185,129,0.04)" : "#F8FAFC",
                transition: "all 0.2s",
                "&:hover": { borderColor: "#1E3A8A", background: "rgba(30,58,138,0.03)" }
              }}
            >
              <input type="file" ref={ref} style={{ display: "none" }} onChange={e => setFile(e.target.files[0])} />
              {file ? (
                <Stack alignItems="center" spacing={1.5}>
                  <InsertDriveFileIcon sx={{ fontSize: 52, color: "#10B981" }} />
                  <Typography fontWeight={700} color="#0A0F1E">{file.name}</Typography>
                  <Chip label={fmt(file.size)} size="small" color="success" />
                  <Typography variant="caption" color="text.secondary">Click to change</Typography>
                </Stack>
              ) : (
                <Stack alignItems="center" spacing={1.5}>
                  <CloudUploadIcon sx={{ fontSize: 52, color: "#CBD5E1" }} />
                  <Typography fontWeight={600} color="#64748B">Drop your file here or click to browse</Typography>
                  <Typography variant="caption" color="text.secondary">Max 100MB · Executables not allowed</Typography>
                </Stack>
              )}
            </Box>

            {uploading && (
              <Box mt={3}>
                <Stack direction="row" justifyContent="space-between" mb={1}>
                  <Typography variant="caption" color="text.secondary">Uploading & scanning...</Typography>
                  <Typography variant="caption" fontWeight={700} fontFamily="'JetBrains Mono', monospace">{progress}%</Typography>
                </Stack>
                <LinearProgress variant="determinate" value={progress} sx={{
                  height: 8, borderRadius: 4,
                  "& .MuiLinearProgress-bar": { background: "linear-gradient(90deg, #1E3A8A, #06B6D4)" }
                }} />
              </Box>
            )}

            <Stack direction="row" spacing={2} mt={3}>
              <Button fullWidth variant="contained" size="large" onClick={handleUpload}
                disabled={!file || uploading}
                sx={{
                  py: 1.5, borderRadius: 2, textTransform: "none",
                  background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)",
                  fontWeight: 700, fontFamily: "'DM Sans', sans-serif",
                }}>
                {uploading ? "Uploading..." : "Upload & Generate Link"}
              </Button>
              {file && !uploading && (
                <Button variant="outlined" size="large" onClick={reset}
                  sx={{ borderRadius: 2, textTransform: "none", borderColor: "#E2E8F0", color: "#64748B" }}>
                  Clear
                </Button>
              )}
            </Stack>
          </CardContent>
        </Card>
      ) : (
        <Card sx={{ borderRadius: 3, boxShadow: "0 2px 16px rgba(0,0,0,0.07)" }}>
          <CardContent sx={{ p: 6, textAlign: "center" }}>
            <CheckCircleIcon sx={{ fontSize: 68, color: "#10B981", mb: 2 }} />
            <Typography variant="h6" fontWeight={700} mb={1}>Upload Successful!</Typography>
            <Typography color="text.secondary" mb={3}>{uploadedFile.originalFilename}</Typography>
            <Button variant="contained" onClick={reset}
              sx={{ borderRadius: 2, textTransform: "none", background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)" }}>
              Upload Another
            </Button>
          </CardContent>
        </Card>
      )}

      {showModal && shareToken && uploadedFile && (
        <PublicLinkModal
          fileName={uploadedFile.originalFilename}
          publicLink={`http://localhost:5173/public/${shareToken}`}
          onClose={() => setShowModal(false)}
        />
      )}
    </Box>
  );
}