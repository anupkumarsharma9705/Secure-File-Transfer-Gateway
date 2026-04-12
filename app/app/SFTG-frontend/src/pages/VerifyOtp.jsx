import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Card, CardContent, Typography, TextField,
  Button, Stack, Alert, CircularProgress
} from "@mui/material";
import MarkEmailReadIcon from "@mui/icons-material/MarkEmailRead";
import api from "../api/axios";

export default function VerifyOtp() {
  const [otp, setOtp]         = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");
  const navigate = useNavigate();
  const email = localStorage.getItem("verifyEmail");

  const handleVerify = async () => {
    if (otp.length !== 6) { setError("Enter the full 6-digit OTP."); return; }
    setLoading(true); setError("");
    try {
      await api.post("/auth/verify-otp", { email, otp });
      localStorage.removeItem("verifyEmail");
      navigate("/login");
    } catch (err) {
      setError(err.response?.data?.message || "Invalid or expired OTP.");
    } finally { setLoading(false); }
  };

  return (
    <Box sx={{
      minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center",
      background: "linear-gradient(135deg, #0A0F1E 0%, #1E3A8A 55%, #0EA5E9 100%)", p: 2,
    }}>
      <Card sx={{ width: { xs: "100%", sm: 400 }, borderRadius: 3, boxShadow: "0 30px 60px rgba(0,0,0,0.45)" }}>
        <CardContent sx={{ p: 4 }}>
          <Stack alignItems="center" spacing={1} mb={3}>
            <Box sx={{
              width: 54, height: 54, borderRadius: "50%",
              background: "linear-gradient(135deg, #1E3A8A, #06B6D4)",
              display: "flex", alignItems: "center", justifyContent: "center",
            }}>
              <MarkEmailReadIcon sx={{ color: "#fff", fontSize: 26 }} />
            </Box>
            <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" color="#0A0F1E">
              Check your email
            </Typography>
            <Typography variant="body2" color="text.secondary" textAlign="center">
              6-digit code sent to<br />
              <strong style={{ color: "#1E3A8A" }}>{email}</strong>
            </Typography>
          </Stack>

          {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}

          <Stack spacing={2}>
            <TextField fullWidth label="6-Digit OTP" value={otp}
              onChange={e => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
              onKeyDown={e => e.key === "Enter" && handleVerify()}
              inputProps={{
                maxLength: 6,
                style: {
                  letterSpacing: "0.6em", fontSize: "1.6rem",
                  textAlign: "center", fontFamily: "'JetBrains Mono', monospace", fontWeight: 600
                }
              }}
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2 } }}
            />
            <Button fullWidth variant="contained" size="large" onClick={handleVerify} disabled={loading}
              sx={{
                py: 1.5, borderRadius: 2, textTransform: "none",
                background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)",
                fontWeight: 700, fontSize: "1rem",
              }}>
              {loading ? <CircularProgress size={22} color="inherit" /> : "Verify & Continue"}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}