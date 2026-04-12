import Navbar from "./Navbar";
import { Box } from "@mui/material";

export default function Layout({ children }) {
  return (
    <>
      <Navbar />
      <Box sx={{
        mt: "64px",
        minHeight: "calc(100vh - 64px)",
        backgroundColor: "#F1F5F9",
        p: { xs: 2, sm: 3 },
      }}>
        <Box sx={{ maxWidth: 1100, mx: "auto" }}>
          {children}
        </Box>
      </Box>
    </>
  );
}