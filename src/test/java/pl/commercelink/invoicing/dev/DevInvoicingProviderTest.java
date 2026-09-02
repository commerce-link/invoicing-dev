package pl.commercelink.invoicing.dev;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoiceKind;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoiceRequest;
import pl.commercelink.invoicing.api.Price;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DevInvoicingProviderTest {

    private final DevInvoicingProvider provider = new DevInvoicingProvider(new DevInvoiceStore(0));

    @Test
    void createInvoiceReturnsInvoiceWithSequentialNumber() {
        Invoice first = provider.createInvoice(standardRequest("order-1"));
        Invoice second = provider.createInvoice(standardRequest("order-2"));

        assertThat(first.id()).isEqualTo("dev-sale-1");
        assertThat(first.number()).isEqualTo("FV/1/2026");
        assertThat(second.number()).isEqualTo("FV/2/2026");
    }

    @Test
    void createInvoiceSumsPositionsIntoAmount() {
        Invoice invoice = provider.createInvoice(InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Standard)
                .orderId("order-1")
                .sellDate(LocalDate.of(2026, 9, 2))
                .billingParty(buyer())
                .positions(List.of(
                        new InvoicePosition("1", "Laptop", 2, Price.fromNet(1000)),
                        new InvoicePosition("2", "Mysz", 1, Price.fromNet(100))))
                .build());

        assertThat(invoice.amount().netValue()).isEqualTo(2100.00);
        assertThat(invoice.amount().grossValue()).isEqualTo(2583.00);
        assertThat(invoice.currency()).isEqualTo("PLN");
        assertThat(invoice.exchangeRate()).isEqualTo(1.0);
    }

    @Test
    void createInvoiceUsesKindSpecificNumberPrefix() {
        assertThat(DevInvoicingProvider.numberPrefix(InvoiceKind.Proforma)).isEqualTo("FP");
        assertThat(DevInvoicingProvider.numberPrefix(InvoiceKind.Estimate)).isEqualTo("OF");
        assertThat(DevInvoicingProvider.numberPrefix(InvoiceKind.Standard)).isEqualTo("FV");
        assertThat(DevInvoicingProvider.numberPrefix(InvoiceKind.Advance)).isEqualTo("FZ");
        assertThat(DevInvoicingProvider.numberPrefix(InvoiceKind.Final)).isEqualTo("FK");
        assertThat(DevInvoicingProvider.numberPrefix(InvoiceKind.Receipt)).isEqualTo("PA");
        assertThat(DevInvoicingProvider.numberPrefix(null)).isEqualTo("FV");
    }

    @Test
    void createInvoiceMarksInvoicePaidOnlyWhenPaidAmountCoversGross() {
        Invoice unpaid = provider.createInvoice(standardRequest("order-1"));
        Invoice paid = provider.createInvoice(InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Standard)
                .orderId("order-2")
                .sellDate(LocalDate.of(2026, 9, 2))
                .billingParty(buyer())
                .positions(List.of(new InvoicePosition("1", "Laptop", 1, Price.fromNet(100))))
                .paidAmount(123.00)
                .build());

        assertThat(unpaid.paid()).isFalse();
        assertThat(paid.paid()).isTrue();
    }

    @Test
    void createInvoiceWithoutPositionsIsNeverMarkedPaid() {
        Invoice invoice = provider.createInvoice(InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Proforma)
                .orderId("order-1")
                .sellDate(LocalDate.of(2026, 9, 2))
                .billingParty(buyer())
                .build());

        assertThat(invoice.positions()).isEmpty();
        assertThat(invoice.paid()).isFalse();
        assertThat(invoice.amount().grossValue()).isEqualTo(0.00);
    }

    @Test
    void createInvoiceDerivesPaymentDateFromSellDateAndTerms() {
        Invoice invoice = provider.createInvoice(InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Standard)
                .orderId("order-1")
                .sellDate(LocalDate.of(2026, 9, 2))
                .billingParty(buyer())
                .positions(List.of(new InvoicePosition("1", "Laptop", 1, Price.fromNet(100))))
                .paymentTerms(14)
                .build());

        assertThat(invoice.paymentToDate()).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void createInvoiceUsesStoreAsSellerAndRequestPartyAsBuyer() {
        Invoice invoice = provider.createInvoice(standardRequest("order-1"));

        assertThat(invoice.seller()).isEqualTo(DevBillingParties.costCenter());
        assertThat(invoice.buyer()).isEqualTo(buyer());
    }

    @Test
    void findsCreatedSaleInvoiceByIdAndByOrderId() {
        Invoice invoice = provider.createInvoice(standardRequest("order-1"));

        assertThat(provider.fetchInvoiceById(invoice.id(), InvoiceDirection.Sale)).isEqualTo(invoice);
        assertThat(provider.fetchInvoicesByOrderId("order-1", InvoiceDirection.Sale))
                .containsExactly(invoice);
    }

    @Test
    void returnsNothingForBlankLookups() {
        assertThat(provider.fetchInvoiceById("", InvoiceDirection.Sale)).isNull();
        assertThat(provider.fetchInvoiceById(null, InvoiceDirection.Purchase)).isNull();
        assertThat(provider.fetchInvoicesByOrderId("", InvoiceDirection.Sale)).isEmpty();
        assertThat(provider.fetchInvoicesByOrderId(null, InvoiceDirection.Purchase)).isEmpty();
    }

    @Test
    void costCenterIsReturnedForAnyIdIncludingBlank() {
        assertThat(provider.fetchCostCenterById("KC-uma2dqukxr")).isEqualTo(DevBillingParties.costCenter());
        assertThat(provider.fetchCostCenterById(null)).isEqualTo(DevBillingParties.costCenter());
        assertThat(provider.fetchCostCenterById("")).isEqualTo(DevBillingParties.costCenter());
    }

    @Test
    void billingPartyByShortcutMirrorsTheSeedForKnownSuppliers() {
        BillingParty party = provider.fetchBillingPartyByShortcut("Acme");

        assertThat(party.company()).isEqualTo("Acme sp. z o.o.");
        assertThat(party.shortcut()).isEqualTo("Acme");
        assertThat(party.hasCompanyDetails()).isTrue();
    }

    @Test
    void billingPartyByIdNeverCarriesAShortcut() {
        BillingParty party = provider.fetchBillingPartyById("dev-party-Acme");

        assertThat(party).isNotNull();
        assertThat(party.hasShortcut()).isFalse();
        assertThat(party.hasCompanyDetails()).isTrue();
    }

    @Test
    void billingPartyLookupsRejectBlankInput() {
        assertThat(provider.fetchBillingPartyById(null)).isNull();
        assertThat(provider.fetchBillingPartyById("")).isNull();
        assertThat(provider.fetchBillingPartyByShortcut(null)).isNull();
        assertThat(provider.fetchBillingPartyByShortcut(" ")).isNull();
    }

    @Test
    void pdfForKnownInvoiceCarriesItsNumber() {
        Invoice invoice = provider.createInvoice(standardRequest("order-1"));

        String pdf = new String(provider.fetchInvoicePdf(invoice.id()), StandardCharsets.ISO_8859_1);

        assertThat(pdf).startsWith("%PDF-").contains(invoice.number());
    }

    @Test
    void pdfForUnknownInvoiceIsStillAValidDocument() {
        String pdf = new String(provider.fetchInvoicePdf("nope"), StandardCharsets.ISO_8859_1);

        assertThat(pdf).startsWith("%PDF-").contains("nope");
    }

    @Test
    void purchaseLookupByIdReturnsSynthesisedSupplierInvoice() {
        Invoice invoice = provider.fetchInvoiceById("dev-pur-ZS/104520/2026", InvoiceDirection.Purchase);

        assertThat(invoice).isNotNull();
        assertThat(invoice.number()).startsWith("FZ/");
        assertThat(invoice.positions()).hasSize(3);
    }

    @Test
    void purchaseLookupByOrderIdReturnsExactlyOneInvoice() {
        assertThat(provider.fetchInvoicesByOrderId("ZS/104520/2026", InvoiceDirection.Purchase))
                .hasSize(1);
    }

    @Test
    void purchaseLookupsHonourTheNoInvoiceMarker() {
        assertThat(provider.fetchInvoiceById("ZS/NOINV/1/2026", InvoiceDirection.Purchase)).isNull();
        assertThat(provider.fetchInvoicesByOrderId("ZS/NOINV/1/2026", InvoiceDirection.Purchase)).isEmpty();
    }

    @Test
    void purchaseAndSaleLookupsDoNotLeakIntoEachOther() {
        Invoice sale = provider.createInvoice(standardRequest("order-1"));

        assertThat(provider.fetchInvoiceById(sale.id(), InvoiceDirection.Purchase).id())
                .isNotEqualTo(sale.id());
        assertThat(provider.fetchInvoiceById("dev-pur-ZS/104520/2026", InvoiceDirection.Sale)).isNull();
    }

    @Test
    void sellerShortcutStaysBlankSoDeliveryProviderIsNeverOverwritten() {
        Invoice invoice = provider.fetchInvoiceById("dev-pur-ZS/104520/2026", InvoiceDirection.Purchase);

        assertThat(invoice.seller().hasShortcut()).isFalse();
        assertThat(provider.fetchBillingPartyById(invoice.seller().id()).hasShortcut()).isFalse();
    }

    private static InvoiceRequest standardRequest(String orderId) {
        return InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Standard)
                .orderId(orderId)
                .sellDate(LocalDate.of(2026, 9, 2))
                .billingParty(buyer())
                .positions(List.of(new InvoicePosition("1", "Laptop", 1, Price.fromNet(1000))))
                .build();
    }

    private static BillingParty buyer() {
        return BillingParty.company("buyer-1", "Nabywca Sp. z o.o.", "ul. Kliencka 2",
                "31-000", "Kraków", "PL", "1234563218", null);
    }
}
