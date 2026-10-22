package com.neueda.leap.service;

import com.neueda.leap.models.TaxReport;
import com.neueda.leap.models.TaxReport.Lot;
import com.neueda.leap.models.TaxReport.Sale;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Recipient form layouts sharing the reporting service's persisted FIFO data. */
@Component
public class TaxPdfGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/uuuu");
    private static final Color INK = new Color(24, 29, 34);
    private static final Color MUTED = new Color(91, 96, 102);
    private static final Color PAPER_GREY = new Color(244, 245, 246);
    private static final float LEFT = 36;
    private static final float WIDTH = 540;
    private static final float SPLIT = 270;

    public byte[] generate(TaxReport report) throws IOException {
        List<Entry> entries = entries(report);
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(report.year() + " Form " + report.document());
            document.getDocumentInformation().setAuthor("Inside Tr8ders");
            document.getDocumentInformation().setSubject("Brokerage recipient copy");
            var layout = new Layout(document, report);
            for (int index = 0; index < entries.size(); index++) layout.page(entries.get(index), index + 1, entries.size());
            document.save(output);
            return output.toByteArray();
        }
    }

    private List<Entry> entries(TaxReport report) {
        List<Entry> result = new ArrayList<>();
        for (var sale : report.sales()) {
            if (sale.lots().isEmpty()) result.add(new Entry(sale, null, 0));
            else for (int lot = 0; lot < sale.lots().size(); lot++) result.add(new Entry(sale, sale.lots().get(lot), lot + 1));
        }
        if (result.isEmpty()) result.add(new Entry(null, null, 0));
        return result;
    }

    private record Entry(Sale sale, Lot lot, int lotNumber) {
        String quantity() { return sale == null ? "--" : number(lot == null ? sale.quantity() : lot.quantity()); }
        BigDecimal proceeds() { return sale == null ? null : lot == null ? sale.proceeds() : lot.proceeds(); }
        String basis() { return number(lot == null ? null : lot.basis()); }
        String gain() { return number(lot == null ? null : lot.gain()); }
        String acquired() { return lot == null ? "Unavailable" : DATE.format(lot.acquired()); }
        String disposed() { return sale == null ? "--" : DATE.format(sale.sold()); }
        String term() { return lot == null ? "Unknown" : lot.holdingPeriod(); }
        String currency() { return sale == null ? "USD" : sale.currency(); }
        String description() { return sale == null ? "No completed dispositions for this year." : sale.symbol() + " - " + sale.description(); }
    }

    private static String number(BigDecimal value) {
        return value == null ? "Unavailable" : value.stripTrailingZeros().toPlainString();
    }

    private static final class Layout {
        private final PDDocument document;
        private final TaxReport report;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;

        Layout(PDDocument document, TaxReport report) { this.document = document; this.report = report; }

        void page(Entry entry, int page, int pageCount) throws IOException {
            var sheet = new PDPage(PDRectangle.LETTER);
            document.addPage(sheet);
            try (var content = new PDPageContentStream(document, sheet)) {
                stream = content;
                header();
                identity();
                if (report.document().equals("1099-DA")) digitalAsset(entry);
                else securities(entry);
                state();
                supplemental(entry, page, pageCount);
            }
        }

        private void header() throws IOException {
            text("INSIDE TR8DERS", LEFT, 37, 14, true, INK);
            text("BROKERAGE TAX DOCUMENTS", LEFT, 54, 7, false, MUTED);
            text("Form " + report.document(), 373, 37, 25, true, INK);
            text(report.document().equals("1099-DA") ? "Digital Asset Proceeds From Broker Transactions"
                    : "Proceeds From Broker and Barter Exchange Transactions", LEFT, 78, 12, true, INK);
            line(LEFT, 90, LEFT + WIDTH, 90);
            text("Copy B - For Recipient", LEFT, 102, 8, true, INK);
            text("Tax year " + report.year(), 468, 102, 9, true, INK);
        }

        private void identity() throws IOException {
            boolean crypto = report.document().equals("1099-DA");
            field(LEFT, 112, 234, 92, crypto ? "FILER'S name, address and telephone number" : "PAYER'S name, address and telephone number",
                    "INSIDE TR8DERS\n100 Example Plaza, Suite 200\nExample City, ZZ 00000\nUnited States | (000) 555-0100", 10);
            field(LEFT, 204, 117, 46, crypto ? "FILER'S TIN" : "PAYER'S TIN", "**-*******", 13);
            field(153, 204, 117, 46, "RECIPIENT'S TIN", "***-**-****", 13);
            field(LEFT, 250, 234, 128, "RECIPIENT'S name and address",
                    report.clientReference() + "\n200 Sample Avenue, Apt 4\nDemo City, ZZ 00000\nUnited States", 11);
            field(LEFT, 378, 234, 48, "Account / client reference", report.clientReference() + " | All accounts", 10);
            field(LEFT, 426, 234, 46, "CUSIP number", "Unavailable", 10);
            field(LEFT, 472, 234, 56, "Statement identification", report.year() + " / " + report.document() + "\n" + report.clientReference(), 10);
        }

        private void securities(Entry e) throws IOException {
            field(SPLIT, 112, 306, 68, "1a  Description of property", e.description() + "\nQuantity: " + e.quantity(), 10);
            field(SPLIT, 180, 153, 48, "1b  Date acquired", e.acquired(), 11);
            field(423, 180, 153, 48, "1c  Date sold or disposed", e.disposed(), 11);
            field(SPLIT, 228, 153, 56, "1d  Proceeds (" + e.currency() + ")", number(e.proceeds()), 14);
            field(423, 228, 153, 56, "1e  Cost or other basis (" + e.currency() + ")", e.basis(), 14);
            field(SPLIT, 284, 153, 48, "1f  Accrued market discount", "Unavailable", 10);
            field(423, 284, 153, 48, "1g  Wash sale loss disallowed", "Unavailable", 10);
            holdingPeriod(e, 332, "2  Type of gain or loss");
            field(SPLIT, 380, 153, 48, "3  Proceeds classification", "Unavailable", 10);
            field(423, 380, 153, 48, "4  Federal income tax withheld", "Unavailable", 10);
            field(SPLIT, 428, 306, 44, "5 / 6 / 12  Covered status and IRS reporting information", "Unavailable", 10);
            field(SPLIT, 472, 306, 56, "Supplemental information", "Quantity: " + e.quantity() + " | Basis method: FIFO\nCurrency: " + e.currency(), 10);
        }

        private void digitalAsset(Entry e) throws IOException {
            field(SPLIT, 112, 153, 40, "1a  Code for digital asset", "Unavailable", 10);
            field(423, 112, 153, 40, "1c  Number of units", e.quantity(), 11);
            field(SPLIT, 152, 306, 44, "1b  Name of digital asset", e.description(), 10);
            field(SPLIT, 196, 153, 44, "1d  Date acquired", e.acquired(), 11);
            field(423, 196, 153, 44, "1e  Date sold or disposed", e.disposed(), 11);
            field(SPLIT, 240, 153, 56, "1f  Proceeds (USD)", number(e.proceeds()), 14);
            field(423, 240, 153, 56, "1g  Cost or other basis (USD)", e.basis(), 14);
            field(SPLIT, 296, 153, 44, "1h  Accrued market discount", "Unavailable", 10);
            field(423, 296, 153, 44, "1i  Wash sale loss disallowed", "Unavailable", 10);
            holdingPeriod(e, 340, "6  Gain or loss");
            field(SPLIT, 388, 153, 40, "4  Federal income tax withheld", "Unavailable", 10);
            field(423, 388, 153, 40, "12a / 12b  Transfer-in information", "Unavailable", 10);
            field(SPLIT, 428, 306, 44, "Transaction information", "USD buy/sell | Basis method: FIFO", 10);
            field(SPLIT, 472, 306, 56, "2 / 3a / 9  Covered status and IRS reporting information", "Unavailable", 10);
        }

        private void holdingPeriod(Entry e, float top, String label) throws IOException {
            field(SPLIT, top, 306, 48, label, "", 8);
            checkbox(SPLIT + 7, top + 25, "Short-term", e.term().equals("Short-term"));
            checkbox(SPLIT + 102, top + 25, "Long-term", e.term().equals("Long-term"));
            checkbox(SPLIT + 197, top + 25, "Unknown", e.term().equals("Unknown"));
        }

        private void state() throws IOException {
            field(LEFT, 528, 180, 52, "14  State name", "ZZ - Example State", 10);
            field(216, 528, 180, 52, "15  State identification no.", "**-*******", 11);
            field(396, 528, 180, 52, "16  State tax withheld", "Unavailable", 10);
        }

        private void supplemental(Entry e, int page, int pageCount) throws IOException {
            text("SUPPLEMENTAL BROKERAGE INFORMATION", LEFT, 605, 9, true, INK);
            field(LEFT, 615, 180, 44, "Realized gain / loss on this lot (" + e.currency() + ")", e.gain(), 12);
            field(216, 615, 180, 44, "Sale reference", e.sale() == null ? "--" : "Fill " + e.sale().fillId(), 11);
            field(396, 615, 180, 44, "Purchase lot", e.lotNumber() == 0 ? "Unavailable" : Integer.toString(e.lotNumber()), 11);
            String unit = report.currency().equals("Mixed") ? "" : " " + report.currency();
            text("ANNUAL SUMMARY" + (report.currency().equals("Mixed") ? " | Multiple currencies" : " | " + report.currency()), LEFT, 682, 8, true, INK);
            fitted("Realized gains: " + number(report.gains()) + unit, LEFT, 700, 170);
            fitted("Realized losses: " + number(report.losses()) + unit, 216, 700, 170);
            fitted("Net: " + number(report.net()) + unit, 396, 700, 180);
            String note = report.currency().equals("Mixed") ? "Combined annual totals are unavailable for multiple currencies."
                    : "Annual totals combine each sale's FIFO lots. Dates shown in UTC.";
            text(note, LEFT, 719, 8, false, MUTED);
            if (report.unavailableBasisCount() > 0) text("Partial totals: " + report.unavailableBasisCount()
                    + " sale(s) with unavailable basis excluded from gain/loss totals.", LEFT, 732, 8, false, MUTED);
            line(LEFT, 750, 576, 750);
            text("Inside Tr8ders | " + report.year() + " | " + report.clientReference(), LEFT, 764, 8, false, MUTED);
            text("Page " + page + " of " + pageCount, 510, 764, 8, false, MUTED);
        }

        private void fitted(String value, float x, float top, float width) throws IOException {
            float size = Math.min(9, width * 1000 / regular.getStringWidth(printable(value)));
            text(value, x, top, size, false, INK);
        }

        private void field(float x, float top, float width, float height, String label, String value, float size) throws IOException {
            outline(x, top, width, height);
            stream.setNonStrokingColor(PAPER_GREY);
            stream.addRect(x + 0.5f, 792 - top - 15, width - 1, 14.5f);
            stream.fill();
            text(label, x + 6, top + 11, 6.8f, false, INK);
            List<String> lines = wrap(value, width - 12, size);
            float leading = size + 3;
            int capacity = Math.max(1, (int) ((height - 23) / leading));
            while (lines.size() > capacity && size > 7) {
                size -= 0.5f;
                leading = size + 3;
                capacity = Math.max(1, (int) ((height - 23) / leading));
                lines = wrap(value, width - 12, size);
            }
            float baseline = top + 27;
            for (int i = 0; i < Math.min(capacity, lines.size()); i++) {
                String line = lines.get(i);
                if (i == capacity - 1 && lines.size() > capacity) line = ellipsis(line, width - 12, size);
                text(line, x + 6, baseline, size, true, INK);
                baseline += leading;
            }
        }

        private String ellipsis(String value, float width, float size) throws IOException {
            while (!value.isEmpty() && bold.getStringWidth(value + "...") / 1000 * size > width) value = value.substring(0, value.length() - 1);
            return value + "...";
        }

        private List<String> wrap(String value, float width, float size) throws IOException {
            List<String> result = new ArrayList<>();
            for (String paragraph : printable(value).split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (char ch : paragraph.toCharArray()) {
                    if (bold.getStringWidth(line.toString() + ch) / 1000 * size > width && !line.isEmpty()) {
                        result.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(ch);
                }
                result.add(line.toString());
            }
            return result;
        }

        private void checkbox(float x, float top, String label, boolean checked) throws IOException {
            outline(x, top, 9, 9);
            if (checked) {
                line(x + 2, top + 2, x + 7, top + 7);
                line(x + 7, top + 2, x + 2, top + 7);
            }
            text(label, x + 14, top + 8, 8, false, INK);
        }

        private void outline(float x, float top, float width, float height) throws IOException {
            stream.setStrokingColor(INK);
            stream.setLineWidth(0.55f);
            stream.addRect(x, 792 - top - height, width, height);
            stream.stroke();
        }

        private void line(float x1, float top1, float x2, float top2) throws IOException {
            stream.setStrokingColor(INK);
            stream.setLineWidth(0.55f);
            stream.moveTo(x1, 792 - top1);
            stream.lineTo(x2, 792 - top2);
            stream.stroke();
        }

        private String printable(String value) {
            StringBuilder result = new StringBuilder();
            value.codePoints().forEach(cp -> {
                if (cp == '\n') { result.append('\n'); return; }
                String character = new String(Character.toChars(cp));
                try { regular.encode(character); result.append(character); }
                catch (IllegalArgumentException | IOException unsupported) { result.append('?'); }
            });
            return result.toString();
        }

        private void text(String value, float x, float top, float size, boolean strong, Color color) throws IOException {
            stream.setNonStrokingColor(color);
            stream.beginText();
            stream.setFont(strong ? bold : regular, size);
            stream.newLineAtOffset(x, 792 - top);
            stream.showText(printable(value));
            stream.endText();
        }
    }
}
