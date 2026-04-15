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
import ShieldIcon from "@mui/icons-material/Shield";
import api from "../api/axios";

export default function Login() {
  const [email, setEmail]       = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow]         = useState(false);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState("");
  const navigate = useNavigate();

  const handleLogin = async () => {
    if (!email || !password) { setError("Please fill in all fields."); return; }
    setLoading(true); setError("");
    try {
      const res = await apiWithRetry(
        () => api.post("/auth/login", { email, password }),
        3,    // 3 attempts
        5000  // 5 seconds between attempts
      );
      localStorage.setItem("token", res.data.jwtToken);
      localStorage.setItem("userEmail", email);
      const redirect = localStorage.getItem("redirectAfterLogin") || "/dashboard";
      localStorage.removeItem("redirectAfterLogin");
      navigate(redirect);
    } catch (err) {
      if (!err.response) {
        setError("Backend is starting up. Please wait 30 seconds and try again.");
      } else {
        setError(err.response?.data?.message || "Invalid credentials.");
      }
    } finally { setLoading(false); }
  };
//   const handleLogin = async () => {
//     if (!email || !password) { setError("Please fill in all fields."); return; }
//     setLoading(true); setError("");
//     try {
//       const res = await api.post("/auth/login", { email, password });
//       localStorage.setItem("token", res.data.jwtToken);
//       localStorage.setItem("userEmail", email);
//       const redirect = localStorage.getItem("redirectAfterLogin") || "/dashboard";
//       localStorage.removeItem("redirectAfterLogin");
//       navigate(redirect);
//     } catch (err) {
//       setError(err.response?.data?.message || "Invalid credentials.");
//     } finally { setLoading(false); }
//   };

  return (
    <Box sx={{
      minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center",
      background: "linear-gradient(135deg, #0A0F1E 0%, #1E3A8A 55%, #0EA5E9 100%)", p: 2,
    }}>
      <Card sx={{
        width: { xs: "100%", sm: 400 }, borderRadius: 3,
        boxShadow: "0 30px 60px rgba(0,0,0,0.45)",
      }}>
        <CardContent sx={{ p: 4 }}>
          <Stack alignItems="center" spacing={1} mb={3}>
            <Box sx={{
              width: 54, height: 54, borderRadius: "50%",
              background: "linear-gradient(135deg, #1E3A8A, #06B6D4)",
              display: "flex", alignItems: "center", justifyContent: "center",
            }}>
              <ShieldIcon sx={{ color: "#fff", fontSize: 26 }} />
            </Box>
            <Typography variant="h5" fontWeight={700} fontFamily="'DM Sans', sans-serif" color="#0A0F1E">
              Welcome back
            </Typography>
            <Typography variant="body2" color="text.secondary">Sign in to your SFTG account</Typography>
          </Stack>

          {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}

          <Stack spacing={2}>
            <TextField fullWidth label="Email" type="email" value={email}
              onChange={e => setEmail(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleLogin()}
              InputProps={{ startAdornment: <InputAdornment position="start"><EmailIcon sx={{ color: "#1E3A8A", fontSize: 19 }} /></InputAdornment> }}
              sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2, fontFamily: "'DM Sans', sans-serif" } }}
            />
            <TextField fullWidth label="Password" type={show ? "text" : "password"} value={password}
              onChange={e => setPassword(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleLogin()}
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
            <Button fullWidth variant="contained" size="large" onClick={handleLogin} disabled={loading}
              sx={{
                py: 1.5, borderRadius: 2, textTransform: "none",
                background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)",
                fontWeight: 700, fontSize: "1rem", fontFamily: "'DM Sans', sans-serif",
                "&:hover": { background: "linear-gradient(135deg, #152d6e, #0284c7)", boxShadow: "0 4px 20px rgba(30,58,138,0.4)" }
              }}>
              {loading ? <CircularProgress size={22} color="inherit" /> : "Sign In"}
            </Button>
          </Stack>

          <Typography textAlign="center" mt={3} variant="body2" color="text.secondary">
            No account?{" "}
            <Link to="/register" style={{ color: "#1E3A8A", fontWeight: 700, textDecoration: "none" }}>Create one</Link>
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
