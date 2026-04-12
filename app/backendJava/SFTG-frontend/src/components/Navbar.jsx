import { useState } from "react";
import {
  AppBar, Toolbar, Button, Typography, Box, Tooltip,
  Avatar, Menu, MenuItem, ListItemIcon, Divider,
  Drawer, List, ListItem, ListItemButton, ListItemText,
  IconButton
} from "@mui/material";
import { Link, useNavigate, useLocation } from "react-router-dom";
import DashboardIcon  from "@mui/icons-material/Dashboard";
import UploadIcon     from "@mui/icons-material/Upload";
import FolderIcon     from "@mui/icons-material/Folder";
import PersonIcon     from "@mui/icons-material/Person";
import LogoutIcon     from "@mui/icons-material/Logout";
import ShieldIcon     from "@mui/icons-material/Shield";
import MenuIcon       from "@mui/icons-material/Menu";
import api from "../api/axios";

const NAV = [
  { path: "/dashboard", label: "Dashboard", icon: <DashboardIcon sx={{ fontSize: 20 }} /> },
  { path: "/upload",    label: "Upload",    icon: <UploadIcon    sx={{ fontSize: 20 }} /> },
  { path: "/my-files",  label: "My Files",  icon: <FolderIcon    sx={{ fontSize: 20 }} /> },
];

export default function Navbar() {
  const navigate  = useNavigate();
  const location  = useLocation();

  // Avatar dropdown (desktop)
  const [anchorEl, setAnchorEl] = useState(null);
  const menuOpen = Boolean(anchorEl);

  // Sidebar drawer (mobile)
  const [drawerOpen, setDrawerOpen] = useState(false);

  const userEmail = localStorage.getItem("userEmail") || "U";
  const initial   = userEmail[0].toUpperCase();

  const handleLogout = async () => {
    setAnchorEl(null);
    setDrawerOpen(false);
    try { await api.post("/auth/logout"); } catch (e) {}
    finally {
      localStorage.removeItem("token");
      localStorage.removeItem("userEmail");
      navigate("/login");
    }
  };

  // ── Desktop avatar menu ──────────────────────────────
  const AvatarMenu = (
    <>
      <Tooltip title="Account">
        <IconButton onClick={e => setAnchorEl(e.currentTarget)} size="small">
          <Avatar sx={{
            width: 36, height: 36,
            background: "linear-gradient(135deg, #06B6D4, #1E3A8A)",
            fontSize: "0.9rem", fontWeight: 700,
            border: "2px solid rgba(6,182,212,0.5)",
            cursor: "pointer",
          }}>
            {initial}
          </Avatar>
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchorEl}
        open={menuOpen}
        onClose={() => setAnchorEl(null)}
        transformOrigin={{ horizontal: "right", vertical: "top" }}
        anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        PaperProps={{
          sx: {
            mt: 1, minWidth: 180, borderRadius: 2,
            boxShadow: "0 8px 32px rgba(0,0,0,0.15)",
          }
        }}
      >
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="caption" color="text.secondary">Signed in as</Typography>
          <Typography variant="body2" fontWeight={600} noWrap>{userEmail}</Typography>
        </Box>
        <Divider />
        <MenuItem
          onClick={() => { setAnchorEl(null); navigate("/profile"); }}
          sx={{ gap: 1.5, py: 1.2 }}
        >
          <ListItemIcon><PersonIcon fontSize="small" sx={{ color: "#1E3A8A" }} /></ListItemIcon>
          Profile
        </MenuItem>
        <MenuItem
          onClick={handleLogout}
          sx={{ gap: 1.5, py: 1.2, color: "#EF4444" }}
        >
          <ListItemIcon><LogoutIcon fontSize="small" sx={{ color: "#EF4444" }} /></ListItemIcon>
          Logout
        </MenuItem>
      </Menu>
    </>
  );

  // ── Mobile sidebar drawer ────────────────────────────
  const MobileDrawer = (
    <Drawer
      anchor="right"
      open={drawerOpen}
      onClose={() => setDrawerOpen(false)}
      PaperProps={{
        sx: {
          width: 260,
          background: "linear-gradient(180deg, #0A0F1E 0%, #1E3A8A 100%)",
          color: "#fff",
        }
      }}
    >
      {/* Drawer header */}
      <Box sx={{
        p: 3, display: "flex", alignItems: "center", gap: 2,
        borderBottom: "1px solid rgba(255,255,255,0.1)"
      }}>
        <Avatar sx={{
          width: 44, height: 44,
          background: "linear-gradient(135deg, #06B6D4, #1E3A8A)",
          border: "2px solid rgba(6,182,212,0.5)",
          fontWeight: 700,
        }}>
          {initial}
        </Avatar>
        <Box>
          <Typography variant="body2" fontWeight={700}>My Account</Typography>
          <Typography variant="caption" sx={{ opacity: 0.65 }} noWrap>{userEmail}</Typography>
        </Box>
      </Box>

      {/* Nav links */}
      <List sx={{ px: 1, pt: 2 }}>
        {NAV.map(({ path, label, icon }) => {
          const active = location.pathname === path;
          return (
            <ListItem key={path} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={Link} to={path}
                onClick={() => setDrawerOpen(false)}
                sx={{
                  borderRadius: 2,
                  background: active ? "rgba(6,182,212,0.15)" : "transparent",
                  color: active ? "#06B6D4" : "rgba(255,255,255,0.8)",
                  "&:hover": { background: "rgba(255,255,255,0.08)" },
                  gap: 1.5, py: 1.2,
                }}
              >
                <ListItemIcon sx={{ minWidth: "auto", color: "inherit" }}>{icon}</ListItemIcon>
                <ListItemText
                  primary={label}
                  primaryTypographyProps={{ fontWeight: active ? 700 : 400, fontFamily: "'DM Sans', sans-serif" }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}

        {/* Profile in sidebar */}
        <ListItem disablePadding sx={{ mb: 0.5 }}>
          <ListItemButton
            component={Link} to="/profile"
            onClick={() => setDrawerOpen(false)}
            sx={{
              borderRadius: 2,
              background: location.pathname === "/profile" ? "rgba(6,182,212,0.15)" : "transparent",
              color: location.pathname === "/profile" ? "#06B6D4" : "rgba(255,255,255,0.8)",
              "&:hover": { background: "rgba(255,255,255,0.08)" },
              gap: 1.5, py: 1.2,
            }}
          >
            <ListItemIcon sx={{ minWidth: "auto", color: "inherit" }}>
              <PersonIcon sx={{ fontSize: 20 }} />
            </ListItemIcon>
            <ListItemText
              primary="Profile"
              primaryTypographyProps={{ fontFamily: "'DM Sans', sans-serif" }}
            />
          </ListItemButton>
        </ListItem>
      </List>

      {/* Logout at bottom */}
      <Box sx={{ mt: "auto", p: 2, borderTop: "1px solid rgba(255,255,255,0.1)" }}>
        <ListItemButton
          onClick={handleLogout}
          sx={{
            borderRadius: 2, gap: 1.5, py: 1.2,
            color: "#EF4444",
            "&:hover": { background: "rgba(239,68,68,0.1)" },
          }}
        >
          <ListItemIcon sx={{ minWidth: "auto", color: "#EF4444" }}>
            <LogoutIcon sx={{ fontSize: 20 }} />
          </ListItemIcon>
          <ListItemText
            primary="Logout"
            primaryTypographyProps={{ fontWeight: 600, fontFamily: "'DM Sans', sans-serif" }}
          />
        </ListItemButton>
      </Box>
    </Drawer>
  );

  // ── Main render ──────────────────────────────────────
  return (
    <AppBar position="fixed" elevation={0} sx={{
      background: "linear-gradient(90deg, #0A0F1E 0%, #1E3A8A 100%)",
      borderBottom: "1px solid rgba(6,182,212,0.15)",
    }}>
      <Toolbar sx={{ display: "flex", justifyContent: "space-between", px: { xs: 2, md: 4 } }}>

        {/* Logo */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <ShieldIcon sx={{ color: "#06B6D4", fontSize: 26 }} />
          <Typography sx={{
            fontWeight: 700, fontSize: "1.1rem",
            fontFamily: "'JetBrains Mono', monospace",
            background: "linear-gradient(90deg, #fff 40%, #06B6D4)",
            WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
            letterSpacing: 1,
          }}>
            SFTG
          </Typography>
        </Box>

        {/* Desktop nav links — hidden on mobile */}
        <Box sx={{ display: { xs: "none", md: "flex" }, gap: 0.5 }}>
          {NAV.map(({ path, label, icon }) => {
            const active = location.pathname === path;
            return (
              <Button key={path} component={Link} to={path} startIcon={icon}
                sx={{
                  color: active ? "#06B6D4" : "rgba(255,255,255,0.7)",
                  fontWeight: active ? 700 : 400,
                  fontSize: "0.82rem", textTransform: "none",
                  borderRadius: 2, px: 2, py: 0.75,
                  background: active ? "rgba(6,182,212,0.12)" : "transparent",
                  fontFamily: "'DM Sans', sans-serif",
                  transition: "all 0.2s",
                  "&:hover": { background: "rgba(255,255,255,0.07)", color: "#fff" },
                }}>
                {label}
              </Button>
            );
          })}
        </Box>

        {/* Right side */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {/* Avatar menu — desktop only */}
          <Box sx={{ display: { xs: "none", md: "flex" } }}>
            {AvatarMenu}
          </Box>

          {/* Hamburger — mobile only */}
          <IconButton
            onClick={() => setDrawerOpen(true)}
            sx={{
              display: { xs: "flex", md: "none" },
              color: "#fff",
              border: "1px solid rgba(255,255,255,0.2)",
              borderRadius: 2, p: 0.8,
            }}
          >
            <MenuIcon />
          </IconButton>
        </Box>

      </Toolbar>

      {/* Mobile drawer */}
      {MobileDrawer}
    </AppBar>
  );
}

// import { AppBar, Toolbar, Button, Typography, Box, Tooltip } from "@mui/material";
// import { Link, useNavigate, useLocation } from "react-router-dom";
// import DashboardIcon from "@mui/icons-material/Dashboard";
// import UploadIcon from "@mui/icons-material/Upload";
// import FolderIcon from "@mui/icons-material/Folder";
// import PersonIcon from "@mui/icons-material/Person";
// import LogoutIcon from "@mui/icons-material/Logout";
// import ShieldIcon from "@mui/icons-material/Shield";
// import api from "../api/axios";
//
// const NAV = [
//   { path: "/dashboard", label: "Dashboard", icon: <DashboardIcon sx={{ fontSize: 18 }} /> },
//   { path: "/upload",    label: "Upload",    icon: <UploadIcon    sx={{ fontSize: 18 }} /> },
//   { path: "/my-files",  label: "My Files",  icon: <FolderIcon    sx={{ fontSize: 18 }} /> },
//   { path: "/profile",   label: "Profile",   icon: <PersonIcon    sx={{ fontSize: 18 }} /> },
// ];
//
// export default function Navbar() {
//   const navigate = useNavigate();
//   const location = useLocation();
//
//   const handleLogout = async () => {
//     try {
//       await api.post("/auth/logout");
//     } catch (e) {
//       // If API fails we still clear and redirect
//     } finally {
//       localStorage.removeItem("token");
//       navigate("/login");
//     }
//   };
//
//   return (
//     <AppBar position="fixed" elevation={0} sx={{
//       background: "linear-gradient(90deg, #0A0F1E 0%, #1E3A8A 100%)",
//       borderBottom: "1px solid rgba(6,182,212,0.15)",
//     }}>
//       <Toolbar sx={{ display: "flex", justifyContent: "space-between", px: { xs: 2, md: 4 } }}>
//         {/* Logo */}
//         <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
//           <ShieldIcon sx={{ color: "#06B6D4", fontSize: 26 }} />
//           <Typography sx={{
//             fontWeight: 700, fontSize: "1.1rem",
//             fontFamily: "'JetBrains Mono', monospace",
//             background: "linear-gradient(90deg, #fff 40%, #06B6D4)",
//             WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
//             letterSpacing: 1,
//           }}>
//             SFTG
//           </Typography>
//         </Box>
//
//         {/* Links */}
//         <Box sx={{ display: { xs: "none", md: "flex" }, gap: 0.5 }}>
//           {NAV.map(({ path, label, icon }) => {
//             const active = location.pathname === path;
//             return (
//               <Button key={path} component={Link} to={path} startIcon={icon}
//                 sx={{
//                   color: active ? "#06B6D4" : "rgba(255,255,255,0.7)",
//                   fontWeight: active ? 700 : 400,
//                   fontSize: "0.82rem",
//                   textTransform: "none",
//                   borderRadius: 2,
//                   px: 2, py: 0.75,
//                   background: active ? "rgba(6,182,212,0.12)" : "transparent",
//                   fontFamily: "'DM Sans', sans-serif",
//                   transition: "all 0.2s",
//                   "&:hover": { background: "rgba(255,255,255,0.07)", color: "#fff" },
//                 }}>
//                 {label}
//               </Button>
//             );
//           })}
//         </Box>
//
//         {/* Logout */}
//         <Tooltip title="Sign out">
//           <Button onClick={handleLogout} startIcon={<LogoutIcon sx={{ fontSize: 17 }} />}
//             size="small"
//             sx={{
//               color: "#EF4444", border: "1px solid rgba(239,68,68,0.35)",
//               borderRadius: 2, textTransform: "none", fontFamily: "'DM Sans', sans-serif",
//               fontSize: "0.82rem",
//               "&:hover": { background: "rgba(239,68,68,0.08)", borderColor: "#EF4444" }
//             }}>
//             Logout
//           </Button>
//         </Tooltip>
//       </Toolbar>
//     </AppBar>
//   );
// }