import React, { useEffect, useState } from 'react';
import { getQRCode } from '../api/urls';

interface QRModalProps {
  shortCode: string;
  onClose: () => void;
}

const QRModal: React.FC<QRModalProps> = ({ shortCode, onClose }) => {
  const [imgSrc, setImgSrc] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchQR = async () => {
      try {
        const blob = await getQRCode(shortCode);
        setImgSrc(window.URL.createObjectURL(blob));
      } catch {
        setError('Failed to load QR code.');
      } finally {
        setLoading(false);
      }
    };
    fetchQR();

    // Cleanup blob URL on unmount
    return () => {
      if (imgSrc) window.URL.revokeObjectURL(imgSrc);
    };
  }, [shortCode]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleDownload = () => {
    if (!imgSrc) return;
    const a = document.createElement('a');
    a.href = imgSrc;
    a.download = `${shortCode}-qr.png`;
    a.click();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>QR Code</h2>
          <button className="btn-icon" onClick={onClose} title="Close">✕</button>
        </div>
        <div className="modal-body">
          {loading && <p className="modal-loading">Loading...</p>}
          {error && <p className="modal-error">{error}</p>}
          {imgSrc && (
            <>
              <img src={imgSrc} alt={`QR code for ${shortCode}`} className="qr-image" />
              <p className="qr-code-label"><code>{shortCode}</code></p>
            </>
          )}
        </div>
        {imgSrc && (
          <div className="modal-footer">
            <button className="btn-secondary" onClick={onClose}>Close</button>
            <button className="btn-primary" onClick={handleDownload}>Download PNG</button>
          </div>
        )}
      </div>
    </div>
  );
};

export default QRModal;
