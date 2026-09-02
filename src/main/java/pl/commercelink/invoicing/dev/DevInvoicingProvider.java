package pl.commercelink.invoicing.dev;

import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoiceKind;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoiceRequest;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * In-memory invoicing for development and demo environments. Sale invoices are created and kept
 * for this JVM's lifetime; supplier (purchase) invoices and company data are synthesised, because
 * no app action creates them.
 */
public class DevInvoicingProvider implements InvoicingProvider {

    private static final String SALE_ID_PREFIX = "dev-sale-";

    private final DevInvoiceStore store;

    DevInvoicingProvider(DevInvoiceStore store) {
        this.store = store;
    }

    @Override
    public Invoice createInvoice(InvoiceRequest request) {
        int sequence = store.nextSequence();
        List<InvoicePosition> positions = request.positions() != null ? request.positions() : List.of();
        LocalDate sellDate = request.sellDate() != null ? request.sellDate() : LocalDate.now();
        String id = SALE_ID_PREFIX + sequence;

        Price amount;
        boolean paid;
        if (!positions.isEmpty()) {
            double net = positions.stream().mapToDouble(position -> position.totalPrice().netValue()).sum();
            double gross = positions.stream().mapToDouble(position -> position.totalPrice().grossValue()).sum();
            amount = new Price(net, gross);
            paid = gross > 0 && request.paidAmount() >= gross;
        } else if (request.invoiceKind() == InvoiceKind.Advance) {
            // Advance requests carry no positions: the app fills in paidAmount instead.
            amount = Price.fromGross(request.paidAmount());
            paid = request.paidAmount() > 0;
        } else if (request.invoiceKind() == InvoiceKind.Final) {
            // Final requests carry no positions either: the app fills in leftToPay instead.
            amount = Price.fromGross(request.leftToPay());
            paid = false;
        } else {
            amount = new Price(0, 0);
            paid = false;
        }

        Invoice invoice = new Invoice(
                id,
                numberPrefix(request.invoiceKind()) + "/" + sequence + "/" + sellDate.getYear(),
                request.orderId(),
                amount,
                "https://invoicing-dev.local/invoices/" + id,
                Price.DEFAULT_CURRENCY,
                1.0,
                paid,
                sellDate.plusDays(request.paymentTerms()),
                positions,
                DevBillingParties.costCenter(),
                request.billingParty());

        store.save(invoice);
        return invoice;
    }

    static String numberPrefix(InvoiceKind kind) {
        if (kind == null) {
            return "FV";
        }
        return switch (kind) {
            case Proforma -> "FP";
            case Estimate -> "OF";
            case Standard -> "FV";
            case Advance -> "FZ";
            case Final -> "FK";
            case Receipt -> "PA";
        };
    }

    @Override
    public Invoice fetchInvoiceById(String invoiceId, InvoiceDirection direction) {
        if (isBlank(invoiceId)) {
            return null;
        }
        if (direction == InvoiceDirection.Purchase) {
            return DevPurchaseInvoices.byInvoiceId(invoiceId);
        }
        return store.findById(invoiceId);
    }

    @Override
    public List<Invoice> fetchInvoicesByOrderId(String orderId, InvoiceDirection direction) {
        if (isBlank(orderId)) {
            return List.of();
        }
        if (direction == InvoiceDirection.Purchase) {
            Invoice invoice = DevPurchaseInvoices.byOrderId(orderId);
            return invoice != null ? List.of(invoice) : List.of();
        }
        return store.findByOrderId(orderId);
    }

    @Override
    public byte[] fetchInvoicePdf(String invoiceId) {
        Invoice invoice = store.findById(invoiceId);
        if (invoice == null) {
            return DevInvoicePdf.render(List.of(
                    "Faktura " + invoiceId,
                    "Dokument wygenerowany przez invoicing-dev"));
        }
        return DevInvoicePdf.render(List.of(
                "Faktura " + invoice.number(),
                "Sprzedawca: " + invoice.seller().company(),
                "Nabywca: " + buyerLabel(invoice),
                String.format(Locale.ROOT, "Do zaplaty: %.2f %s", invoice.amount().grossValue(), invoice.currency())));
    }

    @Override
    public BillingParty fetchCostCenterById(String costCenterId) {
        // The id is ignored on purpose: it is a Fakturownia department reference and the store's own
        // company never depends on it. SaldeoSmart ignores it too.
        return DevBillingParties.costCenter();
    }

    @Override
    public BillingParty fetchBillingPartyById(String billingPartyId) {
        if (isBlank(billingPartyId)) {
            return null;
        }
        // Blank shortcut: the only caller uses it to fill in a seller shortcut, which the app writes
        // into delivery.provider. Inventing one would rename the delivery's supplier.
        return DevBillingParties.fromKey(billingPartyId, "");
    }

    @Override
    public BillingParty fetchBillingPartyByShortcut(String billingPartyShortcut) {
        if (isBlank(billingPartyShortcut)) {
            return null;
        }
        return DevBillingParties.fromKey(billingPartyShortcut, billingPartyShortcut);
    }

    private static String buyerLabel(Invoice invoice) {
        BillingParty buyer = invoice.buyer();
        if (buyer == null) {
            return "-";
        }
        if (buyer.company() != null && !buyer.company().isBlank()) {
            return buyer.company();
        }
        return (orEmpty(buyer.name()) + " " + orEmpty(buyer.surname())).trim();
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
