# invoicing-dev

In-memory implementation of `invoicing-api` for development and demo environments. It lets the main
application generate warehouse documents and invoices without any real invoicing system — the
counterpart of `invoicing-fakturownia` and `invoicing-saldeosmart`, which talk to live services.

## What it serves

- **Company data** (`fetchCostCenterById`, `fetchBillingPartyByShortcut`, `fetchBillingPartyById`):
  complete, deterministic companies. Every party satisfies `BillingParty.hasCompanyDetails()`,
  which the app requires before it will issue a document. `fetchCostCenterById` ignores its
  argument, the way SaldeoSmart does — a "cost center" is a Fakturownia department reference, not a
  domain concept.
- **Sale invoices** (`createInvoice`, plus `InvoiceDirection.Sale` lookups): created on demand and
  kept for this JVM's lifetime. Numbers run `FV|FP|FZ|FK|OF|PA / <sequence> / <year>` by invoice
  kind. The counter starts from the wall clock, so a restart does not re-issue numbers that are
  already sitting on orders.
- **Purchase invoices** (`InvoiceDirection.Purchase`): synthesised from the supplier's order number,
  because no application action creates them. Three positions — goods, transport, payment handling —
  and the same input always yields the same invoice. The transport position is exactly `15.00`,
  matching the shipping cost of every delivery the app's demo seed creates, so the sync screen
  auto-matches it. Goods amounts cannot line up with a delivery's items and are picked by hand.
- **PDF** (`fetchInvoicePdf`): a minimal but valid single-page PDF, so a mailed attachment opens in
  a viewer.

The seller of a synthesised purchase invoice deliberately carries **no shortcut**: the app writes a
seller's shortcut into a delivery's supplier field, and an invented one would rename the supplier.

`viewUrl` is a placeholder under `https://invoicing-dev.local/` and does not open anything.

## Mirrored seed data

The companies the app's demo seed already knows are **copied verbatim** from its `DemoStoreSeeder`
(`demoIssuer`, `acmeCounterparty`, `acmeBCounterparty`), so a document generated through this
adapter is indistinguishable from a seeded one:

| lookup | company | tax id |
|---|---|---|
| cost center (issuer) | `Demo Store sp. z o.o.` | `1234567890` |
| shortcut `Acme` | `Acme sp. z o.o.` | `5213000001` |
| shortcut `AcmeB` | `AcmeB sp. z o.o.` | `9482000002` |

Any other key falls back to a synthesised company named `Dostawca <key> (invoicing-dev)`, so a mock
value is never mistaken for real data.

This duplicates data across repositories on purpose: the adapter has no access to the store —
`ProviderFactory` hands a descriptor an empty context. The same seam exists in `pim-dev`, whose
bundled index mirrors the app's catalog seed. **The app's seed is the source of truth; keep these
values in step with it.** Their tax ids do not satisfy the NIP checksum — matching the seed matters
more than a valid check digit.

## Scenario markers

Put either marker anywhere in the supplier order number (case-insensitive):

| Marker | Effect |
|---|---|
| `NOINV` | no purchase invoice exists for that order |
| `PAID` | the purchase invoice comes back settled |

## Discovery

Registered via `META-INF/services/pl.commercelink.invoicing.api.InvoicingProviderDescriptor` as
descriptor `invoicing-dev` with metadata `dev=true`. The app resolves an invoicing provider by the
name stored on the store, so the adapter stays dormant until a store selects it.

## Enabling it

Add it to the app's `dev` profile:

```xml
<dependency>
    <groupId>pl.commercelink</groupId>
    <artifactId>invoicing-dev</artifactId>
    <version>0.1.0</version>
</dependency>
```

The app's demo store seeder then selects it automatically, as long as the store has no invoicing
integration yet.

**Never ship this adapter to production.** It belongs to the `dev` profile only and must not appear
in the deployment version manifest.

## License

MIT
