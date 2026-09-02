package pl.commercelink.invoicing.dev;

import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Supplier invoices, which no app action creates. Synthesised from the supplier's order number
 * (a delivery's external id) or from an invoice id typed into the UI, so the same input always
 * yields the same invoice — no state, restart-proof by construction.
 *
 * <p>Two markers in the key force scenarios, the way {@code SIM-*} products do for supplier-acme:
 * {@code NOINV} means no invoice exists, {@code PAID} means the invoice comes back settled.
 */
final class DevPurchaseInvoices {

    static final String ID_PREFIX = "dev-pur-";

    /** Matches the shipping cost of every seeded delivery, so the sync screen auto-matches it. */
    static final double SHIPPING_POSITION_NET = 15.00;

    private static final double PAYMENT_POSITION_NET = 5.00;
    private static final String NO_INVOICE_MARKER = "NOINV";
    private static final String PAID_MARKER = "PAID";

    private DevPurchaseInvoices() {
    }

    static Invoice byInvoiceId(String invoiceId) {
        String orderId = invoiceId.startsWith(ID_PREFIX)
                ? invoiceId.substring(ID_PREFIX.length())
                : invoiceId;
        return byOrderId(orderId);
    }

    static Invoice byOrderId(String orderId) {
        String markers = orderId.toUpperCase(Locale.ROOT);
        if (markers.contains(NO_INVOICE_MARKER)) {
            return null;
        }

        int hash = hash(orderId);
        List<InvoicePosition> positions = List.of(
                new InvoicePosition("dev-pos-1", "Towar wg zamówienia " + orderId, 1,
                        Price.fromNet(500 + hash % 4500)),
                new InvoicePosition("dev-pos-2", "Transport", 1, Price.fromNet(SHIPPING_POSITION_NET)),
                new InvoicePosition("dev-pos-3", "Obsługa płatności", 1, Price.fromNet(PAYMENT_POSITION_NET)));

        double net = positions.stream().mapToDouble(position -> position.totalPrice().netValue()).sum();
        double gross = positions.stream().mapToDouble(position -> position.totalPrice().grossValue()).sum();

        // Blank seller shortcut on purpose: InvoiceSyncService writes it into delivery.provider,
        // so a made-up shortcut would rename the delivery's supplier on every sync.
        BillingParty seller = DevBillingParties.fromKey(orderId, "");

        return new Invoice(
                ID_PREFIX + orderId,
                "FZ/" + (1 + hash % 9999) + "/" + LocalDate.now().getYear(),
                orderId,
                new Price(net, gross),
                "https://invoicing-dev.local/purchases/" + orderId,
                Price.DEFAULT_CURRENCY,
                1.0,
                markers.contains(PAID_MARKER),
                LocalDate.now().plusDays(14),
                positions,
                seller,
                DevBillingParties.costCenter());
    }

    private static int hash(String key) {
        int hashCode = key.hashCode();
        return hashCode == Integer.MIN_VALUE ? 0 : Math.abs(hashCode);
    }
}
