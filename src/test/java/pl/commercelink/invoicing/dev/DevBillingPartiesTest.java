package pl.commercelink.invoicing.dev;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.BillingParty;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DevBillingPartiesTest {

    private static final int[] NIP_WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};

    @Test
    void costCenterMirrorsTheDemoSeedCompany() {
        BillingParty costCenter = DevBillingParties.costCenter();

        assertThat(costCenter.hasCompanyDetails()).isTrue();
        assertThat(costCenter.company()).isEqualTo("Demo Store sp. z o.o.");
        assertThat(costCenter.streetAndNumber()).isEqualTo("ul. Testowa 1");
        assertThat(costCenter.postalCode()).isEqualTo("00-001");
        assertThat(costCenter.city()).isEqualTo("Warszawa");
        assertThat(costCenter.country()).isEqualTo("PL");
        assertThat(costCenter.taxNo()).isEqualTo("1234567890");
        assertThat(costCenter.id()).isEqualTo("dev-cost-center");
    }

    @Test
    void acmeMirrorsTheDemoSeedCounterparty() {
        BillingParty acme = DevBillingParties.fromKey("Acme", "Acme");

        assertThat(acme.hasCompanyDetails()).isTrue();
        assertThat(acme.company()).isEqualTo("Acme sp. z o.o.");
        assertThat(acme.streetAndNumber()).isEqualTo("ul. Dystrybucyjna 10");
        assertThat(acme.postalCode()).isEqualTo("02-100");
        assertThat(acme.city()).isEqualTo("Warszawa");
        assertThat(acme.taxNo()).isEqualTo("5213000001");
        assertThat(acme.shortcut()).isEqualTo("Acme");
    }

    @Test
    void acmeBMirrorsTheDemoSeedCounterparty() {
        BillingParty acmeB = DevBillingParties.fromKey("AcmeB", "AcmeB");

        assertThat(acmeB.company()).isEqualTo("AcmeB sp. z o.o.");
        assertThat(acmeB.streetAndNumber()).isEqualTo("ul. Hurtowa 7");
        assertThat(acmeB.postalCode()).isEqualTo("26-600");
        assertThat(acmeB.city()).isEqualTo("Radom");
        assertThat(acmeB.taxNo()).isEqualTo("9482000002");
    }

    @Test
    void knownCompaniesMatchRegardlessOfLetterCase() {
        assertThat(DevBillingParties.fromKey("acme", "acme").company()).isEqualTo("Acme sp. z o.o.");
        assertThat(DevBillingParties.fromKey("ACMEB", "ACMEB").company()).isEqualTo("AcmeB sp. z o.o.");
    }

    @Test
    void knownCompanyCarriesTheRequestedShortcut() {
        assertThat(DevBillingParties.fromKey("Acme", "").hasShortcut()).isFalse();
        assertThat(DevBillingParties.fromKey("Acme", "Acme").shortcut()).isEqualTo("Acme");
    }

    @Test
    void unknownKeyFallsBackToACompleteSynthesisedCompany() {
        BillingParty party = DevBillingParties.fromKey("Kosatec", "Kosatec");

        assertThat(party.hasCompanyDetails()).isTrue();
        assertThat(party.company()).isEqualTo("Dostawca Kosatec (invoicing-dev)");
        assertThat(party.country()).isEqualTo("PL");
        assertThat(party.postalCode()).matches("\\d{2}-\\d{3}");
        assertThat(party.shortcut()).isEqualTo("Kosatec");
        assertThat(isValidNip(party.taxNo())).isTrue();
    }

    @Test
    void fallbackCompanyNameIsMarkedAsAMockEvenForAnOrderNumberKey() {
        // The purchase-invoice seller is keyed by the supplier's order number, so "<key> Sp. z o.o."
        // would put a garbage company name on a document.
        assertThat(DevBillingParties.fromKey("ZS/104520/2026", "").company())
                .isEqualTo("Dostawca ZS/104520/2026 (invoicing-dev)");
    }

    @Test
    void fallbackCompanyIsDeterministic() {
        assertThat(DevBillingParties.fromKey("Kosatec", "Kosatec"))
                .isEqualTo(DevBillingParties.fromKey("Kosatec", "Kosatec"));
    }

    @Test
    void partyIdRoundTripsThroughNormalize() {
        BillingParty known = DevBillingParties.fromKey("Acme", "");
        BillingParty fallback = DevBillingParties.fromKey("Kosatec", "");

        assertThat(known.id()).isEqualTo("dev-party-Acme");
        assertThat(DevBillingParties.fromKey(known.id(), "")).isEqualTo(known);
        assertThat(DevBillingParties.fromKey(fallback.id(), "")).isEqualTo(fallback);
    }

    @Test
    void normalizeStripsPartyIdPrefixOnlyWhenPresent() {
        assertThat(DevBillingParties.normalize("dev-party-Acme")).isEqualTo("Acme");
        assertThat(DevBillingParties.normalize("Acme")).isEqualTo("Acme");
    }

    @Test
    void generatedNipNeverEndsWithAnInvalidCheckDigit() {
        for (int seed = 0; seed < 2000; seed++) {
            assertThat(isValidNip(DevBillingParties.nip(seed)))
                    .as("seed %d produced %s", seed, DevBillingParties.nip(seed))
                    .isTrue();
        }
    }

    @Test
    void generatesValidPostalCodeAndNipUnderNonLatinDigitLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            BillingParty party = DevBillingParties.fromKey("TestSupplier", "");

            assertThat(party.postalCode()).matches("\\d{2}-\\d{3}");
            assertThat(party.taxNo()).matches("\\d{10}");
            assertThat(isValidNip(party.taxNo())).isTrue();
        } finally {
            Locale.setDefault(original);
        }
    }

    private static boolean isValidNip(String nip) {
        if (nip == null || !nip.matches("\\d{10}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < NIP_WEIGHTS.length; i++) {
            sum += NIP_WEIGHTS[i] * (nip.charAt(i) - '0');
        }
        return sum % 11 == nip.charAt(9) - '0';
    }
}
