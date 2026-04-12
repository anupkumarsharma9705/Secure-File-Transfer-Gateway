import { useEffect, useState } from "react";
import {
  Box, Card, CardContent, Typography, Chip, Stack,
  Collapse, IconButton, Divider, LinearProgress, Alert, Avatar,
  Table, TableBody, TableCell, TableHead, TableRow
} from "@mui/material";
import FolderIcon      from "@mui/icons-material/Folder";
import ExpandMoreIcon  from "@mui/icons-material/ExpandMore";
import ExpandLessIcon  from "@mui/icons-material/ExpandLess";
import DownloadIcon    from "@mui/icons-material/Download";
import api from "../api/axios";
import ExpiryTimer from "../components/ExpiryTimer";

function statusChip(file) {
  const expired = new Date() > new Date(file.expiryAt);
  if (!file.active)                            return <Chip label="Revoked"      size="small" color="default" />;
  if (file.downloadCount >= file.downloadLimit) return <Chip label="Limit Reached" size="small" color="warning" />;
  if (expired)                                  return <Chip label="Expired"      size="small" color="error" />;
  return                                               <Chip label="Active"       size="small" color="success" />;
}

function FileRow({ file }) {
  const [open, setOpen] = useState(false);
  const fmt = b => b < 1048576 ? (b/1024).toFixed(1)+" KB" : (b/1048576).toFixed(2)+" MB";
  const pct = file.downloadLimit > 0 ? (file.downloadCount / file.downloadLimit) * 100 : 0;

  return (
    <Card sx={{ borderRadius: 3, boxShadow: "0 2px 12px rgba(0,0,0,0.06)", mb: 2 }}>
      <CardContent sx={{ p: 3 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems={{ sm: "center" }}>
          <Stack direction="row" alignItems="center" spacing={2} flex={1}>
            <Avatar sx={{ background: "#1E3A8A12", width: 44, height: 44 }}>
              <FolderIcon sx={{ color: "#1E3A8A" }} />
            </Avatar>
            <Box>
              <Typography fontWeight={600} noWrap sx={{ maxWidth: 240, fontFamily: "'DM Sans', sans-serif" }}>
                {file.originalFilename}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {fmt(file.fileSize)} · {new Date(file.uploadDate).toLocaleDateString()}
              </Typography>
            </Box>
          </Stack>

          <Stack direction="row" alignItems="center" spacing={2}>
            {statusChip(file)}
            <ExpiryTimer expiryAt={file.expiryAt} />
          </Stack>

          <Box sx={{ minWidth: 110 }}>
            <Stack direction="row" spacing={0.5} alignItems="center" mb={0.5}>
              <DownloadIcon sx={{ fontSize: 13, color: "#64748B" }} />
              <Typography variant="caption" color="text.secondary" fontFamily="'JetBrains Mono', monospace">
                {file.downloadCount}/{file.downloadLimit}
              </Typography>
            </Stack>
            <LinearProgress variant="determinate" value={Math.min(pct, 100)} sx={{
              height: 6, borderRadius: 3,
              "& .MuiLinearProgress-bar": { background: pct >= 100 ? "#F59E0B" : "#06B6D4" }
            }} />
          </Box>

          {file.downloadLogs?.length > 0 && (
            <IconButton size="small" onClick={() => setOpen(!open)}
              sx={{ background: "#F1F5F9", borderRadius: 2 }}>
              {open ? <ExpandLessIcon /> : <ExpandMoreIcon />}
            </IconButton>
          )}
        </Stack>

        <Collapse in={open}>
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" fontWeight={700} color="#1E3A8A" mb={1.5}>Download History</Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, color: "#64748B", fontSize: "0.78rem" }}>Downloader</TableCell>
                <TableCell sx={{ fontWeight: 600, color: "#64748B", fontSize: "0.78rem" }}>Time</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {file.downloadLogs.map((log, i) => (
                <TableRow key={i} sx={{ "&:last-child td": { border: 0 } }}>
                  <TableCell sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "0.8rem" }}>
                    {log.downloaderEmail || "Anonymous"}
                  </TableCell>
                  <TableCell sx={{ fontSize: "0.8rem" }}>{new Date(log.downloadedAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Collapse>
      </CardContent>
    </Card>
  );
}

export default function MyFiles() {
  const [files, setFiles]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState("");

  useEffect(() => {
    api.get("/files/my-files")
      .then(r => setFiles(r.data))
      .catch(() => setError("Failed to load files."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LinearProgress sx={{ mt: 2, borderRadius: 2 }} />;

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" color="#0A0F1E">
          My Files
        </Typography>
        <Chip label={`${files.length} file${files.length !== 1 ? "s" : ""}`}
          sx={{ background: "#1E3A8A14", color: "#1E3A8A", fontWeight: 600 }} />
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {files.length === 0 ? (
        <Card sx={{ borderRadius: 3, p: 6, textAlign: "center", boxShadow: "0 2px 16px rgba(0,0,0,0.07)" }}>
          <FolderIcon sx={{ fontSize: 64, color: "#CBD5E1", mb: 2 }} />
          <Typography color="text.secondary">No files uploaded yet.</Typography>
        </Card>
      ) : files.map(f => <FileRow key={f.id} file={f} />)}
    </Box>
  );
}