import { QRCodeCanvas } from "qrcode.react";

function PublicLinkModal({ fileName, publicLink, onClose }) {
  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <button onClick={onClose} style={closeStyle}>✖</button>

        <h3>Public Link Generated</h3>
        <p><strong>File:</strong> {fileName}</p>

        <input
          type="text"
          value={publicLink}
          readOnly
          style={{ width: "100%", padding: "8px", marginBottom: "20px" }}
        />

        <QRCodeCanvas value={publicLink} size={150} />
      </div>
    </div>
  );
}

