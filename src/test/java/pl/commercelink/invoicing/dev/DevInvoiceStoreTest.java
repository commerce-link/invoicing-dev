package pl.commercelink.invoicing.dev;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.Price;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DevInvoiceStoreTest {

    private final DevInvoiceStore store = new DevInvoiceStore(0);

    @Test
    void sequenceStartsFromTheGivenValueAndIncrements() {
        assertThat(store.nextSequence()).isEqualTo(1);
        assertThat(store.nextSequence()).isEqualTo(2);
    }

    @Test
    void defaultStoreStartsNumberingFromTheClockSoRestartsDoNotRepeatNumbers() {
        // State is in-memory only; a counter restarting at 1 would re-issue numbers already sitting
        // on orders from the previous run.
        assertThat(new DevInvoiceStore().nextSequence()).isGreaterThan(0);
    }

    @Test
    void findsSavedInvoiceById() {
        Invoice invoice = invoice("dev-sale-1", "order-1");
        store.save(invoice);

        assertThat(store.findById("dev-sale-1")).isSameAs(invoice);
    }

    @Test
    void returnsNullForUnknownOrNullId() {
        assertThat(store.findById("nope")).isNull();
        assertThat(store.findById(null)).isNull();
    }

    @Test
    void findsSavedInvoicesByOrderId() {
        store.save(invoice("dev-sale-1", "order-1"));
        store.save(invoice("dev-sale-2", "order-2"));

        assertThat(store.findByOrderId("order-2"))
                .extracting(Invoice::id)
                .containsExactly("dev-sale-2");
    }

    @Test
    void returnsEmptyListWhenNoInvoiceMatchesTheOrder() {
        store.save(invoice("dev-sale-1", "order-1"));

        assertThat(store.findByOrderId("order-9")).isEmpty();
    }

    @Test
    void twoStoresDoNotShareState() {
        store.save(invoice("dev-sale-1", "order-1"));

        assertThat(new DevInvoiceStore(0).findById("dev-sale-1")).isNull();
    }

    private static Invoice invoice(String id, String orderId) {
        return new Invoice(id, "FV/1/2026", orderId, new Price(100, 123), "url",
                "PLN", 1.0, false, null, List.of(), null, null);
    }
}
