package pl.commercelink.invoicing.dev;

import pl.commercelink.invoicing.api.BillingParty;

import java.util.Locale;
import java.util.Map;

/**
 * Complete, deterministic company data. Every party must satisfy
 * {@link BillingParty#hasCompanyDetails()}, because the app refuses to issue a document otherwise.
 *
 * <p>The companies the app's demo seed already knows are mirrored here verbatim from
 * {@code DemoStoreSeeder} ({@code demoIssuer}, {@code acmeCounterparty}, {@code acmeBCounterparty}),
 * so a document generated through this adapter is indistinguishable from a seeded one. That
 * duplication across repositories is deliberate: this module has no access to the store —
 * {@code ProviderFactory.buildContext} hands a descriptor an empty context. The same seam exists in
 * {@code pim-dev}, whose bundled index mirrors the app's catalog seed. The seed is the source of
 * truth; keep these values in step with it.
 *
 * <p>The NIPs of the mirrored companies do not satisfy the NIP checksum. That is intentional —
 * matching the seed matters more than a valid check digit. Only the fallback generates valid ones.
 */
final class DevBillingParties {

    static final String PARTY_ID_PREFIX = "dev-party-";

    private static final String[] CITIES = {"Warszawa", "Kraków", "Poznań", "Gdańsk", "Wrocław"};
    private static final int[] NIP_WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};

    /** Keyed by lower-cased supplier shortcut; the shortcut itself is applied by the caller. */
    private static final Map<String, BillingParty> MIRRORED_SUPPLIERS = Map.of(
            "acme", BillingParty.company(PARTY_ID_PREFIX + "Acme", "Acme sp. z o.o.",
                    "ul. Dystrybucyjna 10", "02-100", "Warszawa", "PL", "5213000001", null),
            "acmeb", BillingParty.company(PARTY_ID_PREFIX + "AcmeB", "AcmeB sp. z o.o.",
                    "ul. Hurtowa 7", "26-600", "Radom", "PL", "9482000002", null));

    private DevBillingParties() {
    }

    /** The store itself — the issuer of every warehouse document. */
    static BillingParty costCenter() {
        return BillingParty.company(
                "dev-cost-center",
                "Demo Store sp. z o.o.",
                "ul. Testowa 1",
                "00-001",
                "Warszawa",
                "PL",
                "1234567890",
                "DEMO-STORE");
    }

    /**
     * The company for {@code key}, carrying {@code shortcut} as its alias. Callers pass a blank
     * shortcut when the value would overwrite real data in the app.
     */
    static BillingParty fromKey(String key, String shortcut) {
        String normalized = normalize(key);
        BillingParty mirrored = MIRRORED_SUPPLIERS.get(normalized.toLowerCase(Locale.ROOT));
        if (mirrored != null) {
            return withShortcut(mirrored, shortcut);
        }
        int hash = hash(normalized);
        return BillingParty.company(
                PARTY_ID_PREFIX + normalized,
                "Dostawca " + normalized + " (invoicing-dev)",
                "ul. Dostawcza " + (1 + hash % 99),
                String.format(Locale.ROOT, "%02d-%03d", hash % 100, hash % 1000),
                CITIES[hash % CITIES.length],
                "PL",
                nip(hash),
                shortcut);
    }

    /** Strips the id prefix, so party id -> party lookups return the same company. */
    static String normalize(String key) {
        return key.startsWith(PARTY_ID_PREFIX) ? key.substring(PARTY_ID_PREFIX.length()) : key;
    }

    /** Ten digits ending with the NIP check digit, so a synthesised number looks real. */
    static String nip(int seed) {
        for (int attempt = 0; ; attempt++) {
            String nineDigits = String.format(Locale.ROOT, "%09d", Math.floorMod(seed + attempt, 1_000_000_000));
            int checkDigit = checkDigit(nineDigits);
            if (checkDigit != 10) {
                return nineDigits + checkDigit;
            }
        }
    }

    private static BillingParty withShortcut(BillingParty party, String shortcut) {
        return BillingParty.company(party.id(), party.company(), party.streetAndNumber(),
                party.postalCode(), party.city(), party.country(), party.taxNo(), shortcut);
    }

    private static int hash(String key) {
        int hashCode = key.hashCode();
        return hashCode == Integer.MIN_VALUE ? 0 : Math.abs(hashCode);
    }

    private static int checkDigit(String nineDigits) {
        int sum = 0;
        for (int i = 0; i < NIP_WEIGHTS.length; i++) {
            sum += NIP_WEIGHTS[i] * (nineDigits.charAt(i) - '0');
        }
        return sum % 11;
    }
}
