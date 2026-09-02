package pl.commercelink.invoicing.dev;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoicePosition;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DevPurchaseInvoicesTest {

    @Test
    void synthesisesInvoiceWithThreePositions() {
        Invoice invoice = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        assertThat(invoice.positions()).extracting(InvoicePosition::id)
                .containsExactly("dev-pos-1", "dev-pos-2", "dev-pos-3");
        assertThat(invoice.positions()).extracting(InvoicePosition::name)
                .containsExactly("Towar wg zamówienia ZS/104520/2026", "Transport", "Obsługa płatności");
    }

    @Test
    void shippingPositionMatchesTheSeededDeliveryShippingCost() {
        // Every seeded delivery has shippingCost 15.00, so InvoicePositionMatcher.matchAuxiliary
        // fills the shipping-cost field on the sync screen without any clicking.
        Invoice invoice = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        assertThat(invoice.positions().get(1).price().netValue()).isEqualTo(15.00);
        assertThat(DevPurchaseInvoices.SHIPPING_POSITION_NET).isEqualTo(15.00);
    }

    @Test
    void amountEqualsTheSumOfPositions() {
        Invoice invoice = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        double expectedNet = invoice.positions().stream()
                .mapToDouble(position -> position.totalPrice().netValue()).sum();

        assertThat(invoice.amount().netValue()).isEqualTo(expectedNet);
        assertThat(invoice.currency()).isEqualTo("PLN");
        assertThat(invoice.exchangeRate()).isEqualTo(1.0);
    }

    @Test
    void sameOrderAlwaysYieldsTheSameInvoice() {
        Invoice first = DevPurchaseInvoices.byOrderId("ZS/104520/2026");
        Invoice second = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.number()).isEqualTo(second.number());
        assertThat(first.amount()).isEqualTo(second.amount());
    }

    @Test
    void differentOrdersYieldDifferentNumbers() {
        assertThat(DevPurchaseInvoices.byOrderId("ZS/104520/2026").number())
                .isNotEqualTo(DevPurchaseInvoices.byOrderId("ZS/104521/2026").number());
    }

    @Test
    void sellerNeverCarriesAShortcut() {
        Invoice invoice = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        assertThat(invoice.seller().hasShortcut()).isFalse();
        assertThat(invoice.seller().hasCompanyDetails()).isTrue();
    }

    @Test
    void sellerNameIsMarkedAsAMockValue() {
        assertThat(DevPurchaseInvoices.byOrderId("ZS/104520/2026").seller().company())
                .isEqualTo("Dostawca ZS/104520/2026 (invoicing-dev)");
    }

    @Test
    void buyerIsTheStoreItself() {
        assertThat(DevPurchaseInvoices.byOrderId("ZS/104520/2026").buyer())
                .isEqualTo(DevBillingParties.costCenter());
    }

    @Test
    void invoiceIdRoundTripsBackToTheSameInvoice() {
        Invoice fromOrder = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        assertThat(DevPurchaseInvoices.byInvoiceId(fromOrder.id()).id()).isEqualTo(fromOrder.id());
        assertThat(DevPurchaseInvoices.byInvoiceId(fromOrder.id()).number()).isEqualTo(fromOrder.number());
    }

    @Test
    void invoiceIdWithoutThePrefixIsTreatedAsAnOrderNumber() {
        assertThat(DevPurchaseInvoices.byInvoiceId("ZS/104520/2026").id())
                .isEqualTo(DevPurchaseInvoices.byOrderId("ZS/104520/2026").id());
    }

    @Test
    void isUnpaidByDefaultAndDueInFourteenDays() {
        Invoice invoice = DevPurchaseInvoices.byOrderId("ZS/104520/2026");

        assertThat(invoice.paid()).isFalse();
        assertThat(invoice.paymentToDate()).isEqualTo(LocalDate.now().plusDays(14));
    }

    @Test
    void noInvoiceMarkerMeansNoInvoiceAtAll() {
        assertThat(DevPurchaseInvoices.byOrderId("ZS/NOINV/104522/2026")).isNull();
        assertThat(DevPurchaseInvoices.byOrderId("zs/noinv/104522/2026")).isNull();
        assertThat(DevPurchaseInvoices.byInvoiceId("dev-pur-ZS/NOINV/104522/2026")).isNull();
    }

    @Test
    void paidMarkerMeansTheInvoiceComesBackPaid() {
        assertThat(DevPurchaseInvoices.byOrderId("ZS/PAID/104521/2026").paid()).isTrue();
        assertThat(DevPurchaseInvoices.byOrderId("zs/paid/104521/2026").paid()).isTrue();
    }
}
