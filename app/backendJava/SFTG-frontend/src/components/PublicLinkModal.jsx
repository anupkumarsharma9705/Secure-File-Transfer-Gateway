import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Stack, Typography, Box, IconButton, Tooltip
} from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CheckIcon from "@mui/icons-material/Check";
import { QRCodeCanvas } from "qrcode.react";
import { useState } from "react";

export default function PublicLinkModal({ fileName, publicLink, onClose }) {
  const [copied, setCopied] = useState(false);

  if (!publicLink) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(publicLink);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth
      PaperProps={{ sx: { borderRadius: 3, fontFamily: "'DM Sans', sans-serif" } }}>
      <DialogTitle sx={{ pb: 0 }}>
        <Typography fontWeight={700} fontSize="1rem">Link Generated 🔗</Typography>
        <Typography variant="caption" color="text.secondary">{fileName}</Typography>
      </DialogTitle>

      <DialogContent>
        <Stack spacing={2} alignItems="center" pt={1}>
          <Box sx={{ p: 2, background: "#F8FAFC", borderRadius: 2, border: "1px solid #E2E8F0" }}>
            <QRCodeCanvas value={publicLink} size={160} />
          </Box>

          <Box width="100%">
            <Typography variant="caption" color="text.secondary" mb={0.5} display="block">Share link</Typography>
            <Stack direction="row" spacing={1}>
              <TextField
                fullWidth size="small" value={publicLink}
                InputProps={{ readOnly: true, sx: { fontFamily: "'JetBrains Mono', monospace", fontSize: "0.72rem" } }}
                sx={{ "& .MuiOutlinedInput-root": { borderRadius: 2 } }}
              />
              <Tooltip title={copied ? "Copied!" : "Copy"}>
                <IconButton onClick={handleCopy} sx={{
                  borderRadius: 2, background: copied ? "#10B98118" : "#1E3A8A10",
                  color: copied ? "#10B981" : "#1E3A8A", border: "1px solid",
                  borderColor: copied ? "#10B98140" : "#1E3A8A20",
                }}>
                  {copied ? <CheckIcon fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
                </IconButton>
              </Tooltip>
            </Stack>
          </Box>

          <Typography variant="caption" color="text.secondary" textAlign="center">
            Expires in 1 hour · Max 5 downloads
          </Typography>
        </Stack>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} variant="contained" fullWidth
          sx={{
            borderRadius: 2, textTransform: "none", py: 1,
            background: "linear-gradient(135deg, #1E3A8A, #0EA5E9)",
            fontFamily: "'DM Sans', sans-serif", fontWeight: 600,
          }}>
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}