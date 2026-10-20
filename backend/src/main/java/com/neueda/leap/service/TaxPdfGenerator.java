package com.neueda.leap.service;

import com.neueda.leap.models.TaxReport;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Two lightweight layouts share the same pagination, typography, and FIFO report data. */
@Component
public class TaxPdfGenerator {
    public static final String DISCLAIMER = "SIMULATED / NOT FOR TAX FILING";
    private static final Color INK = new Color(25, 45, 64);
    private static final float[] WIDTHS = {174, 72, 72, 85, 98, 98, 98, 73};

    public byte[] generate(TaxReport report) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(report.year() + " Simulated " + report.document());
            document.getDocumentInformation().setSubject(DISCLAIMER + "; educational brokerage report");
            try (var layout = new Layout(document, report)) {
                layout.newPage();
                if (report.sales().isEmpty()) {
                    layout.row(List.of("No completed dispositions for this year.", "", "", "", "", "", "", ""));
                }
                for (var sale : report.sales()) {
                    String asset = sale.symbol() + " - " + sale.description() + "\n"
                            + sale.assetClass() + " | " + sale.currency() + " | Fill " + sale.fillId();
                    if (sale.lots().isEmpty()) {
                        layout.row(List.of(asset, "Unavailable", sale.sold().toString(),
                                number(sale.quantity()), number(sale.proceeds()), "Unavailable", "Unavailable", "Unknown"));
                    } else {
                        for (var lot : sale.lots()) {
                            layout.row(List.of(asset, lot.acquired().toString(), sale.sold().toString(),
                                    number(lot.quantity()), number(lot.proceeds()), number(lot.basis()),
                                    number(lot.gain()), lot.holdingPeriod()));
                        }
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static String number(BigDecimal value) {
        return value == null ? "Unavailable" : value.stripTrailingZeros().toPlainString();
    }

    private static final class Layout implements AutoCloseable {
        private final PDDocument document;
        private final TaxReport report;
        private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument document, TaxReport report) { this.document = document; this.report = report; }

        void newPage() throws IOException {
            if (stream != null) stream.close();
            var page = new PDPage(new PDRectangle(842, 595));
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            text("INSIDE TR8DERS", 36, 557, 12, true);
            text("Simulated Form " + report.document() + " | " + report.year(), 36, 531, 22, true);
            text(DISCLAIMER, 36, 508, 12, true);
            text(report.clientReference() + " | All your accounts | Currency: " + report.currency() + " | Dates in UTC", 36, 488, 10, false);
            boolean crypto = report.document().equals("1099-DA");
            text(crypto ? "Digital asset dispositions - supported USD buy/sell transactions only"
                    : "Stock and ETF dispositions", 36, 469, 10, true);
            String unit = report.currency().equals("Mixed") ? "" : " " + report.currency();
            text("Realized gains: " + number(report.gains()) + unit, 36, 443, 10, true);
            text("Realized losses: " + number(report.losses()) + unit, 300, 443, 10, true);
            text("Net: " + number(report.net()) + unit, 566, 443, 10, true);
            text(report.currency().equals("Mixed")
                    ? "Combined totals unavailable: multiple currencies. Each asset row shows its currency; no currency conversion is applied."
                    : "Totals use combined FIFO cost per sale. Rows show purchase lots; unavailable-basis sales appear once.", 36, 424, 9, false);
            text(report.unavailableBasisCount() > 0
                    ? "PARTIAL TOTALS: " + report.unavailableBasisCount() + " sale(s) excluded from gains/losses because FIFO basis is unavailable."
                    : "Amounts retain up to 8 decimal places. No wash-sale adjustments or tax liability calculations.", 36, 408, 9, false);
            stream.setNonStrokingColor(new Color(232, 239, 244));
            stream.addRect(36, 368, 770, 27);
            stream.fill();
            String[] headers = {crypto ? "Digital asset / reference" : "Asset / reference", "Acquired",
                    crypto ? "Disposed" : "Sold", "Quantity", "Proceeds", "FIFO basis", "Gain/loss", "Holding period"};
            float x = 40;
            for (int i = 0; i < headers.length; i++) {
                text(headers[i], x, 379, 8, true);
                x += WIDTHS[i];
            }
            text("Educational simulation only. This is not an IRS information return and is not compliant for filing.", 36, 39, 9, false);
            text(DISCLAIMER + " | Page " + document.getNumberOfPages(), 36, 24, 8, true);
            y = 353;
        }

        void row(List<String> cells) throws IOException {
            List<List<String>> lines = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) lines.add(wrap(cells.get(i), WIDTHS[i] - 10));
            int count = lines.stream().mapToInt(List::size).max().orElse(1);
            // Keep normal rows together; very long names can safely continue on another page.
            if (y - count * 12 < 64 && count * 12 < 280) newPage();
            for (int line = 0; line < count; line++) {
                if (y < 64) newPage();
                float x = 40;
                for (int col = 0; col < cells.size(); col++) {
                    if (line < lines.get(col).size()) text(lines.get(col).get(line), x, y, 8, false);
                    x += WIDTHS[col];
                }
                y -= 12;
            }
            stream.setStrokingColor(new Color(216, 225, 231));
            stream.moveTo(36, y + 3);
            stream.lineTo(806, y + 3);
            stream.stroke();
            y -= 9;
        }

        private List<String> wrap(String value, float width) throws IOException {
            List<String> result = new ArrayList<>();
            for (String paragraph : printable(value).split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (char ch : paragraph.toCharArray()) {
                    if (font.getStringWidth(line.toString() + ch) / 1000 * 8 > width && !line.isEmpty()) {
                        result.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(ch);
                }
                result.add(line.toString());
            }
            return result;
        }

        private String printable(String value) {
            StringBuilder result = new StringBuilder();
            value.codePoints().forEach(cp -> {
                if (cp == '\n') { result.append('\n'); return; }
                String character = new String(Character.toChars(cp));
                try { font.encode(character); result.append(character); }
                catch (IllegalArgumentException | IOException unsupported) { result.append('?'); }
            });
            return result.toString();
        }

        private void text(String value, float x, float baseline, float size, boolean strong) throws IOException {
            stream.setNonStrokingColor(INK);
            stream.beginText();
            stream.setFont(strong ? bold : font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(printable(value));
            stream.endText();
        }

        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
