package pl.commercelink.invoicing.dev;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DevInvoicePdfTest {

    @Test
    void rendersDocumentWithPdfHeaderAndTrailer() {
        String pdf = new String(DevInvoicePdf.render(List.of("Faktura FV/1/2026")), StandardCharsets.ISO_8859_1);

        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf).endsWith("%%EOF\n");
        assertThat(pdf).contains("startxref");
        assertThat(pdf).contains("(Faktura FV/1/2026) Tj");
    }

    @Test
    void crossReferenceOffsetsPointAtTheirObjects() {
        String pdf = new String(DevInvoicePdf.render(List.of("linia")), StandardCharsets.ISO_8859_1);
        int entriesStart = pdf.indexOf("0000000000 65535 f \n");

        for (int object = 1; object <= 5; object++) {
            String entry = pdf.substring(entriesStart + object * 20, entriesStart + (object + 1) * 20);
            int offset = Integer.parseInt(entry.substring(0, 10));
            assertThat(pdf.substring(offset)).startsWith(object + " 0 obj");
        }
    }

    @Test
    void everyCrossReferenceEntryIsExactlyTwentyBytes() {
        String pdf = new String(DevInvoicePdf.render(List.of("linia")), StandardCharsets.ISO_8859_1);
        int entriesStart = pdf.indexOf("0000000000 65535 f \n");

        assertThat(entriesStart).isPositive();
        for (int object = 0; object <= 5; object++) {
            String entry = pdf.substring(entriesStart + object * 20, entriesStart + (object + 1) * 20);
            assertThat(entry).hasSize(20).endsWith(" \n");
        }
    }

    @Test
    void startxrefPointsAtTheCrossReferenceTable() {
        String pdf = new String(DevInvoicePdf.render(List.of("linia")), StandardCharsets.ISO_8859_1);
        int declared = Integer.parseInt(pdf.substring(pdf.indexOf("startxref\n") + "startxref\n".length(),
                pdf.indexOf("\n%%EOF")));

        assertThat(pdf.substring(declared)).startsWith("xref\n");
    }

    @Test
    void keepsByteLengthEqualToCharacterLengthDespiteNonAsciiInput() {
        byte[] bytes = DevInvoicePdf.render(List.of("Obsługa płatności — Kraków"));
        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);

        assertThat(bytes.length).isEqualTo(pdf.length());
        assertThat(pdf).contains("Obs?uga p?atno?ci");
    }

    @Test
    void escapesParenthesesAndBackslashes() {
        assertThat(DevInvoicePdf.sanitize("a(b)c\\d")).isEqualTo("a\\(b\\)c\\\\d");
    }

    @Test
    void rendersEveryRequestedLine() {
        String pdf = new String(DevInvoicePdf.render(List.of("pierwsza", "druga", "trzecia")),
                StandardCharsets.ISO_8859_1);

        assertThat(pdf).contains("(pierwsza) Tj").contains("(druga) Tj").contains("(trzecia) Tj");
    }

    @Test
    void rendersValidXrefOffsetsUnderNonLatinDigitLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            String pdf = new String(DevInvoicePdf.render(List.of("linia")), StandardCharsets.ISO_8859_1);
            int entriesStart = pdf.indexOf("0000000000 65535 f \n");

            for (int object = 0; object <= 5; object++) {
                String entry = pdf.substring(entriesStart + object * 20, entriesStart + (object + 1) * 20);
                String offsetPart = entry.substring(0, 10);
                for (char c : offsetPart.toCharArray()) {
                    assertThat(c).isGreaterThanOrEqualTo('0').isLessThanOrEqualTo('9');
                }
            }

            for (int object = 1; object <= 5; object++) {
                String entry = pdf.substring(entriesStart + object * 20, entriesStart + (object + 1) * 20);
                int offset = Integer.parseInt(entry.substring(0, 10));
                assertThat(pdf.substring(offset)).startsWith(object + " 0 obj");
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
