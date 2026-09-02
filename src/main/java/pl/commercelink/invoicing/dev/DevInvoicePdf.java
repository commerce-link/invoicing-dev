package pl.commercelink.invoicing.dev;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a minimal but valid single-page PDF, so a mailed attachment opens in a viewer instead of
 * being garbage. Text is reduced to ASCII on purpose: cross-reference offsets are computed from
 * string length, which only equals byte length while every character is single-byte.
 */
final class DevInvoicePdf {

    private static final String XREF_FREE_ENTRY = "0000000000 65535 f \n";

    private DevInvoicePdf() {
    }

    static byte[] render(List<String> lines) {
        String content = contentStream(lines);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                        + " /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }

        int xrefOffset = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append('\n').append(XREF_FREE_ENTRY);
        for (int offset : offsets) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xrefOffset).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String contentStream(List<String> lines) {
        StringBuilder stream = new StringBuilder("BT\n/F1 12 Tf\n16 TL\n50 780 Td\n");
        for (String line : lines) {
            stream.append('(').append(sanitize(line)).append(") Tj\nT*\n");
        }
        return stream.append("ET\n").toString();
    }

    /** Escapes PDF string syntax and replaces every non-ASCII character with a question mark. */
    static String sanitize(String text) {
        StringBuilder sanitized = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (character == '(' || character == ')' || character == '\\') {
                sanitized.append('\\').append(character);
            } else if (character >= 32 && character < 127) {
                sanitized.append(character);
            } else {
                sanitized.append('?');
            }
        }
        return sanitized.toString();
    }
}
