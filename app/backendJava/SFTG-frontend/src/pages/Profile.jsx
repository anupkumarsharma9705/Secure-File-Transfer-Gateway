import { useEffect, useState } from "react";
import {
  Box, Card, CardContent, Typography, Stack, Avatar,
  Chip, Divider, LinearProgress, Alert
} from "@mui/material";
import SecurityIcon    from "@mui/icons-material/Security";
import LoginIcon       from "@mui/icons-material/Login";
import LogoutIcon      from "@mui/icons-material/Logout";
import UploadFileIcon  from "@mui/icons-material/UploadFile";
import DownloadIcon    from "@mui/icons-material/Download";
import LinkIcon        from "@mui/icons-material/Link";
import CalendarIcon    from "@mui/icons-material/CalendarToday";
import VerifiedIcon    from "@mui/icons-material/VerifiedUser";
import api from "../api/axios";

const ACT = {
  LOGIN:         { icon: <LoginIcon      fontSize="small" />, color: "#10B981", label: "Signed In" },
  LOGOUT:        { icon: <LogoutIcon     fontSize="small" />, color: "#EF4444", label: "Signed Out" },
  FILE_UPLOAD:   { icon: <UploadFileIcon fontSize="small" />, color: "#1E3A8A", label: "File Uploaded" },
  FILE_DOWNLOAD: { icon: <DownloadIcon   fontSize="small" />, color: "#06B6D4", label: "File Downloaded" },
  LINK_GENERATE: { icon: <LinkIcon       fontSize="small" />, color: "#F59E0B", label: "Link Generated" },
};

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState("");

  useEffect(() => {
    api.get("/account/me")
      .then(r => setProfile(r.data))
      .catch(() => setError("Failed to load profile."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LinearProgress sx={{ mt: 2, borderRadius: 2 }} />;
  if (error)   return <Alert severity="error">{error}</Alert>;

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" color="#0A0F1E" mb={3}>
        My Profile
      </Typography>

      <Stack direction={{ xs: "column", md: "row" }} spacing={3} alignItems="flex-start">
        {/* Profile card */}
        <Card sx={{ borderRadius: 3, boxShadow: "0 2px 16px rgba(0,0,0,0.07)", width: { xs: "100%", md: 280 }, flexShrink: 0 }}>
          <CardContent sx={{ p: 4, textAlign: "center" }}>
            <Avatar sx={{
              width: 80, height: 80, mx: "auto", mb: 2,
              background: "linear-gradient(135deg, #1E3A8A, #06B6D4)",
              fontSize: "2rem", fontWeight: 700, fontFamily: "'DM Sans', sans-serif"
            }}>
              {profile?.email?.[0]?.toUpperCase()}
            </Avatar>
            <Typography variant="h6" fontWeight={700} mb={0.5}>{profile?.email?.split("@")[0]}</Typography>
            <Typography variant="caption" color="text.secondary" display="block" mb={2}>{profile?.email}</Typography>

            <Chip
              label={profile?.role?.replace("ROLE_", "")}
              sx={{ background: "linear-gradient(135deg, #1E3A8A, #06B6D4)", color: "#fff", fontWeight: 600, mb: 3 }}
            />

            <Divider sx={{ mb: 2.5 }} />

            <Stack spacing={2} textAlign="left">
              <Stack direction="row" spacing={1.5} alignItems="center">
                <CalendarIcon sx={{ color: "#1E3A8A", fontSize: 18, flexShrink: 0 }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">Member Since</Typography>
                  <Typography variant="body2" fontWeight={600} fontSize="0.82rem">
                    {profile?.createdAt
                      ? new Date(profile.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" })
                      : "—"}
                  </Typography>
                </Box>
              </Stack>

              <Stack direction="row" spacing={1.5} alignItems="center">
                <VerifiedIcon sx={{ color: "#10B981", fontSize: 18, flexShrink: 0 }} />
                <Box>
                  <Typography variant="caption" color="text.secondary">Email Status</Typography>
                  <Typography variant="body2" fontWeight={600} color="#10B981" fontSize="0.82rem">Verified</Typography>
                </Box>
              </Stack>
            </Stack>
          </CardContent>
        </Card>

        {/* Audit log */}
        <Card sx={{ borderRadius: 3, boxShadow: "0 2px 16px rgba(0,0,0,0.07)", flex: 1 }}>
          <CardContent sx={{ p: 3 }}>
            <Stack direction="row" alignItems="center" spacing={1} mb={2}>
              <SecurityIcon sx={{ color: "#1E3A8A", fontSize: 20 }} />
              <Typography variant="h6" fontWeight={600} fontFamily="'DM Sans', sans-serif">Audit Log</Typography>
              <Chip label={`${profile?.logs?.length || 0} events`} size="small"
                sx={{ background: "#1E3A8A12", color: "#1E3A8A", ml: "auto" }} />
            </Stack>
            <Divider sx={{ mb: 2 }} />

            {!profile?.logs?.length ? (
              <Typography color="text.secondary" textAlign="center" py={5}>No activity recorded yet</Typography>
            ) : (
              <Stack spacing={1} sx={{ maxHeight: 460, overflowY: "auto", pr: 0.5 }}>
                {profile.logs.map((log, i) => {
                  const cfg = ACT[log.action] || { icon: <SecurityIcon fontSize="small" />, color: "#64748B", label: log.action };
                  return (
                    <Stack key={i} direction="row" alignItems="flex-start" spacing={2}
                      sx={{ p: 1.5, borderRadius: 2, background: "#F8FAFC", "&:hover": { background: "#F1F5F9" }, transition: "background 0.15s" }}>
                      <Box sx={{
                        width: 34, height: 34, borderRadius: "50%", flexShrink: 0,
                        background: `${cfg.color}15`, color: cfg.color,
                        display: "flex", alignItems: "center", justifyContent: "center",
                      }}>{cfg.icon}</Box>
                      <Box flex={1} minWidth={0}>
                        <Stack direction="row" justifyContent="space-between">
                          <Typography variant="body2" fontWeight={600}>{cfg.label}</Typography>
                          <Typography variant="caption" color="text.secondary"
                            sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: "0.7rem", flexShrink: 0, ml: 1 }}>
                            {new Date(log.timestamp).toLocaleString()}
                          </Typography>
                        </Stack>
                        {(log.metadata || log.ipAddress) && (
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {log.metadata && `${log.metadata}`}
                            {log.ipAddress && ` · ${log.ipAddress}`}
                          </Typography>
                        )}
                      </Box>
                    </Stack>
                  );
                })}
              </Stack>
            )}
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
}