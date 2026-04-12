import { Card, CardContent, Typography, Box } from "@mui/material";
import InsertChartIcon from "@mui/icons-material/InsertChart";

function MetricCard({ title, value }) {
  return (
    <Card sx={{ flex: "1 1 200px", minWidth: 200, background: "linear-gradient(135deg, #1E3A8A, #06B6D4)", color: "#fff", boxShadow: 3 }}>
      <CardContent sx={{ textAlign: "center" }}>
        <Box sx={{ display: "flex", justifyContent: "center", mb: 1 }}>
          <InsertChartIcon fontSize="large" />
        </Box>
        <Typography variant="h6">{title}</Typography>
        <Typography variant="h4">{value}</Typography>
      </CardContent>
    </Card>
  );
}

export default MetricCard;