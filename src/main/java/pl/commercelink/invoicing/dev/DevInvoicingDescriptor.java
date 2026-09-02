package pl.commercelink.invoicing.dev;

import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;

/**
 * Registers the in-memory invoicing adapter. The descriptor owns the invoice store, because
 * {@code ProviderFactory} creates a fresh provider on every call while loading descriptors once —
 * holding state here keeps it alive for the application's lifetime without a static field, so
 * tests stay isolated.
 */
public class DevInvoicingDescriptor implements InvoicingProviderDescriptor {

    private final DevInvoiceStore store = new DevInvoiceStore();

    @Override
    public String name() {
        return "invoicing-dev";
    }

    @Override
    public String displayName() {
        return "Dev Invoicing (in-memory)";
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of();
    }

    @Override
    public InvoicingProvider create(Map<String, String> configuration) {
        return new DevInvoicingProvider(store);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of("dev", "true");
    }
}
