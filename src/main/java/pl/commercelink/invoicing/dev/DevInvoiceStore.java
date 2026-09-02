package pl.commercelink.invoicing.dev;

import pl.commercelink.invoicing.api.Invoice;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sale invoices created during this JVM's lifetime, plus the numbering counter. Owned by the
 * descriptor rather than the provider, because {@code ProviderFactory} builds a fresh provider
 * instance on every call — state kept on the provider would not outlive a single request.
 *
 * <p>Deliberately not persisted: the app never reads a sale invoice back (there is no
 * {@code InvoiceDirection.Sale} lookup anywhere) and purchase invoices are synthesised
 * deterministically, so a restart loses nothing worth keeping. The one thing that would matter is
 * numbering, which is why the counter starts from the wall clock instead of zero: this keeps
 * numbers monotonic across a restart within the same day, but a restart after midnight starts
 * from a lower value again and can re-issue numbers already sitting on the previous day's orders.
 */
class DevInvoiceStore {

    private final Map<String, Invoice> invoices = new ConcurrentHashMap<>();
    private final AtomicInteger sequence;

    DevInvoiceStore() {
        this(LocalTime.now().toSecondOfDay());
    }

    DevInvoiceStore(int startSequence) {
        this.sequence = new AtomicInteger(startSequence);
    }

    int nextSequence() {
        return sequence.incrementAndGet();
    }

    void save(Invoice invoice) {
        invoices.put(invoice.id(), invoice);
    }

    Invoice findById(String invoiceId) {
        return invoiceId == null ? null : invoices.get(invoiceId);
    }

    List<Invoice> findByOrderId(String orderId) {
        return invoices.values().stream()
                .filter(invoice -> invoice.hasOrderId(orderId))
                .toList();
    }
}
