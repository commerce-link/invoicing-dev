package pl.commercelink.invoicing.dev;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoiceKind;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoiceRequest;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.invoicing.api.Price;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class DevInvoicingDescriptorTest {

    private final DevInvoicingDescriptor descriptor = new DevInvoicingDescriptor();

    @Test
    void describesItselfAsADevAdapterWithoutConfiguration() {
        assertThat(descriptor.name()).isEqualTo("invoicing-dev");
        assertThat(descriptor.displayName()).isEqualTo("Dev Invoicing (in-memory)");
        assertThat(descriptor.configurationFields()).isEmpty();
        assertThat(descriptor.metadata()).isEqualTo(Map.of("dev", "true"));
    }

    @Test
    void isDiscoverableThroughServiceLoader() {
        Iterable<InvoicingProviderDescriptor> loaded = ServiceLoader.load(InvoicingProviderDescriptor.class);

        assertThat(StreamSupport.stream(loaded.spliterator(), false).map(InvoicingProviderDescriptor::name))
                .contains("invoicing-dev");
    }

    @Test
    void createsAWorkingProviderFromAnEmptyConfiguration() {
        InvoicingProvider provider = descriptor.create(Map.of());

        assertThat(provider).isNotNull();
        assertThat(provider.fetchCostCenterById("anything").hasCompanyDetails()).isTrue();
    }

    @Test
    void everyProviderItCreatesSharesTheSameInvoiceState() {
        // ProviderFactory.get(store) builds a fresh provider on every call, so state that lived on
        // the provider would vanish between requests.
        InvoicingProvider first = descriptor.create(Map.of());
        InvoicingProvider second = descriptor.create(Map.of());

        Invoice created = first.createInvoice(request("order-1"));

        assertThat(second.fetchInvoiceById(created.id(), InvoiceDirection.Sale)).isEqualTo(created);
    }

    @Test
    void numberingContinuesAcrossProviderInstances() {
        InvoicingProvider first = descriptor.create(Map.of());
        InvoicingProvider second = descriptor.create(Map.of());

        int firstSequence = sequenceOf(first.createInvoice(request("order-1")));
        int secondSequence = sequenceOf(second.createInvoice(request("order-2")));

        assertThat(secondSequence).isEqualTo(firstSequence + 1);
    }

    @Test
    void twoDescriptorsDoNotShareState() {
        Invoice created = descriptor.create(Map.of()).createInvoice(request("order-1"));

        assertThat(new DevInvoicingDescriptor().create(Map.of())
                .fetchInvoiceById(created.id(), InvoiceDirection.Sale)).isNull();
    }

    private static int sequenceOf(Invoice invoice) {
        return Integer.parseInt(invoice.id().substring("dev-sale-".length()));
    }

    private static InvoiceRequest request(String orderId) {
        return InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Standard)
                .orderId(orderId)
                .sellDate(LocalDate.of(2026, 9, 2))
                .positions(List.of(new InvoicePosition("1", "Laptop", 1, Price.fromNet(1000))))
                .build();
    }
}
