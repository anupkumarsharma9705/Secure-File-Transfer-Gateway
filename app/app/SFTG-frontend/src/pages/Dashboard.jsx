import { useEffect, useState } from "react";
import {
  Box, Card, CardContent, Typography, Stack, Avatar,
  Chip, Divider, LinearProgress
} from "@mui/material";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import DownloadIcon    from "@mui/icons-material/Download";
import LinkIcon        from "@mui/icons-material/Link";
import LoginIcon       from "@mui/icons-material/Login";
import LogoutIcon      from "@mui/icons-material/Logout";
import SecurityIcon    from "@mui/icons-material/Security";
import api from "../api/axios";
import PeopleIcon from "@mui/icons-material/People";

const ACTION_CFG = {
  LOGIN:         { icon: <LoginIcon      fontSize="small" />, color: "#10B981" },
  LOGOUT:        { icon: <LogoutIcon     fontSize="small" />, color: "#EF4444" },
  FILE_UPLOAD:   { icon: <UploadFileIcon fontSize="small" />, color: "#1E3A8A" },
  FILE_DOWNLOAD: { icon: <DownloadIcon   fontSize="small" />, color: "#06B6D4" },
  LINK_GENERATE: { icon: <LinkIcon       fontSize="small" />, color: "#F59E0B" },
};


function StatCard({ icon, label, value, accent }) {
  return (
    <Card sx={{ flex: 1, minWidth: 160, borderRadius: 3, boxShadow: "0 2px 16px rgba(0,0,0,0.07)" }}>
      <CardContent sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="caption" color="text.secondary" fontWeight={500}>{label}</Typography>
            <Typography variant="h3" fontWeight={700} color={accent} fontFamily="'JetBrains Mono', monospace">
              {value ?? "—"}
            </Typography>
          </Box>
          <Box sx={{
            width: 44, height: 44, borderRadius: 2,
            background: `${accent}18`,
            display: "flex", alignItems: "center", justifyContent: "center", color: accent
          }}>{icon}</Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

export default function Dashboard() {
  const [profile, setProfile] = useState(null);
  const [files, setFiles]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [globalStats, setGlobalStats] = useState(null);

    useEffect(() => {
      Promise.all([
        api.get("/account/me"),
        api.get("/files/my-files"),
        api.get("/stats/global")        // ← add this
      ])
        .then(([p, f, s]) => {
          setProfile(p.data);
          setFiles(f.data);
          setGlobalStats(s.data);       // ← add this
        })
        .catch(console.error)
        .finally(() => setLoading(false));
    }, []);
//   useEffect(() => {
//     Promise.all([api.get("/account/me"), api.get("/files/my-files")])
//       .then(([p, f]) => { setProfile(p.data); setFiles(f.data); })
//       .catch(console.error)
//       .finally(() => setLoading(false));
//   }, []);

  if (loading) return <LinearProgress sx={{ mt: 2, borderRadius: 2 }} />;

  const totalFiles      = files.length;
  const totalDownloads  = files.reduce((s, f) => s + (f.downloadCount || 0), 0);
  const recentLogs      = profile?.logs?.slice(0, 8) || [];

  return (
    <Box>
      {/* Banner */}
      <Card sx={{
        mb: 3, borderRadius: 3,
        background: "linear-gradient(135deg, #0A0F1E 0%, #1E3A8A 50%, #0EA5E9 100%)",
        color: "#fff", boxShadow: "0 8px 32px rgba(30,58,138,0.3)"
      }}>
        <CardContent sx={{ p: 3 }}>
          <Stack direction="row" alignItems="center" spacing={2}>
            <Avatar sx={{
              width: 52, height: 52, background: "rgba(255,255,255,0.15)",
              fontSize: "1.4rem", fontWeight: 700, fontFamily: "'DM Sans', sans-serif"
            }}>
              {profile?.email?.[0]?.toUpperCase()}
            </Avatar>
            <Box>
              <Typography variant="h6" fontWeight={700} fontFamily="'DM Sans', sans-serif">
                Welcome back 👋
              </Typography>
              <Typography variant="body2" sx={{ opacity: 0.75 }}>
                {profile?.email} ·{" "}
                <Chip label={profile?.role?.replace("ROLE_", "")} size="small"
                  sx={{ color: "#fff", background: "rgba(255,255,255,0.18)", fontSize: "0.68rem", height: 20 }} />
              </Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* Stats */}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} mb={3} flexWrap="wrap">
        <StatCard icon={<UploadFileIcon />} label="Files Uploaded" value={totalFiles}    accent="#1E3A8A" />
        <StatCard icon={<PeopleIcon />} label="Total Users" value={globalStats?.uniqueVisitors ?? "—"} accent="#7C3AED" />
        <StatCard icon={<DownloadIcon />}   label="Total Downloads" value={totalDownloads} accent="#10B981" />
      </Stack>

      {/* Activity */}
      <Card sx={{ borderRadius: 3, boxShadow: "0 2px 16px rgba(0,0,0,0.07)" }}>
        <CardContent sx={{ p: 3 }}>
          <Stack direction="row" alignItems="center" spacing={1} mb={2}>
            <SecurityIcon sx={{ color: "#1E3A8A", fontSize: 20 }} />
            <Typography variant="h6" fontWeight={600} fontFamily="'DM Sans', sans-serif">Recent Activity</Typography>
          </Stack>
          <Divider sx={{ mb: 2 }} />

          {recentLogs.length === 0 ? (
            <Typography color="text.secondary" textAlign="center" py={4}>No activity yet</Typography>
          ) : (
            <Stack spacing={1}>
              {recentLogs.map((log, i) => {
                const cfg = ACTION_CFG[log.action] || { icon: <SecurityIcon fontSize="small" />, color: "#64748B" };
                return (
                  <Stack key={i} direction="row" alignItems="center" spacing={2}
                    sx={{ p: 1.5, borderRadius: 2, background: "#F8FAFC", "&:hover": { background: "#F1F5F9" } }}>
                    <Box sx={{
                      width: 34, height: 34, borderRadius: "50%",
                      background: `${cfg.color}15`, color: cfg.color,
                      display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0
                    }}>{cfg.icon}</Box>
                    <Box flex={1} minWidth={0}>
                      <Typography variant="body2" fontWeight={600}>
                        {log.action?.replace(/_/g, " ")}
                      </Typography>
                      {log.metadata && (
                        <Typography variant="caption" color="text.secondary" noWrap>{log.metadata}</Typography>
                      )}
                    </Box>
                    <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0, fontFamily: "'JetBrains Mono', monospace", fontSize: "0.7rem" }}>
                      {new Date(log.timestamp).toLocaleString()}
                    </Typography>
                  </Stack>
                );
              })}
            </Stack>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}