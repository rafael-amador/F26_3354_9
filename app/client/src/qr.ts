// TD-1 (2): QR code of the authenticator setup link, shown only on larger screens. Encoding uses the `qrcode`
// package; drawing is done with plain Views (see SetupQrCode in LoginScreen) so no SVG/canvas is needed.
import QRCode from 'qrcode';

/** QR modules as rows of booleans (true = dark), with no quiet zone. */
export function qrMatrix(text: string): boolean[][] {
  const { modules } = QRCode.create(text, { errorCorrectionLevel: 'M' });
  const rows: boolean[][] = [];
  for (let r = 0; r < modules.size; r++) {
    const row: boolean[] = [];
    for (let c = 0; c < modules.size; c++) row.push(Boolean(modules.get(r, c)));
    rows.push(row);
  }
  return rows;
}
