import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import {
  Box, Card, CardContent, Typography, TextField, Button,
  Stack, Alert, CircularProgress, InputAdornment, IconButton
} from "@mui/material";
import EmailIcon from "@mui/icons-material/Email";
import LockIcon from "@mui/icons-material/Lock";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import PersonAddIcon from "@mui/icons-material/PersonAdd";
import api from "../api/axios";

export default function Register() {
  const [email, setEmail]       = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow]         = useState(false);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState("");
  const navigate = useNavigate();

  const handleRegister = async () => {
    if (!email || !password) { setError("Please fill in all fields."); return; }
    setLoading(true); setError("");
    try {
      await apiWithRetry(
        () => api.post("/auth/register", { email, password }),
        3,
        5000
      );
      localStorage.setItem("verifyEmail", email);
      navigate("/verify-otp");
    } catch (err) {
      if (!err.response) {
        setError("Backend is starting up. Please wait 30 seconds and try again.");
      } else {
        setError(err.response?.data?.message || "Registration failed.");
      }
    } finally { setLoading(false); }
  };

//   const handleRegister = async () => {
//     if (!email || !password) { setError("Please fill in all fields."); return; }
//     setLoading(true); setError("");
//     try {
//       await api.post("/auth/register", { email, password });
//       localStorage.setItem("verifyEmail", email);
//       navigate("/verify-otp");
//     } catch (err) {
//       setError(err.response?.data?.message || "Registration failed.");
//     } finally { setLoading(false); }
//   };

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
              <PersonAddIcon sx={{ color: "#fff", fontSize: 26 }} />
            </Box>
            <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" color="#0A0F1E">
              Create account
            </Typography>
            <Typography variant="body2" color="text.secondary">Join SFTG — Secure File Transfer</Typography>
          </Stack>

          {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}

          <Stack spacing={2}>
            <TextField fullWidth label="Email" type="email" value={email}
              onChange={e => setEmail(e.target.value)}
              InputProps={{ startAdornment: <InputAdornment position="start"><EmailIcon sx={{ color: "#1E3A8A", fontSize: 19 }} /></InputAdornment> }}
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2 } }}
            />
            <TextField fullWidth label="Password" type={show ? "text" : "password"} value={password}
              onChange={e => setPassword(e.target.value)}
              helperText="Min 8 chars · uppercase · lowercase · number · special char"
              InputProps={{
                startAdornment: <InputAdornment position="start"><LockIcon sx={{ color: "#1E3A8A", fontSize: 19 }} /></InputAdornment>,
                endAdornment: <InputAdornment position="end">
                  <IconButton onClick={() => setShow(!show)} size="small">
                    {show ? <VisibilityOff /> : <Visibility />}
                  </IconButton>
                </InputAdornment>
              }}
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2 } }}
            />
            <Button fullWidth variant="contained" size="large" onClick={handleRegister} disabled={loading}
              sx={{
                py: 1.5, borderRadius: 2, textTransform: "none",
                background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)",
                fontWeight: 700, fontSize: "1rem", fontFamily: "'DM Sans', sans-serif",
              }}>
              {loading ? <CircularProgress size={22} color="inherit" /> : "Create Account"}
            </Button>
          </Stack>

          <Typography textAlign="center" mt={3} variant="body2" color="text.secondary">
            Already have an account?{" "}
            <Link to="/login" style={{ color: "#1E3A8A", fontWeight: 700, textDecoration: "none" }}>Sign in</Link>
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
