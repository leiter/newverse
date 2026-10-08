# CAS CT100 Scale Integration — Feasibility Assessment

**Status:** Research only. No hardware bought, no code written, nothing verified
against a physical device.
**Date of findings:** 2026-10-08
**Scope:** Seller app (`sell` flavor, Android). Connecting a CAS CT100
receipt-printing retail scale to the app so weight and/or sales data can be used
while selling.
**Verdict:** The Android USB-serial layer is routine and well-trodden. The
project risk sits almost entirely outside our code, in two places: whether the
CT100 emits usable data on its serial port at all, and German legal metrology
(Eichrecht). Do not buy hardware or plan a sprint before the two cheap checks at
the end of this document have been run.

> **Evidence quality warning.** Everything below is inference from a two-page
> marketing infocard (`cas-waagen.de/pdf/cas-ct100-infocard-web.pdf`), the CAS
> product page, and the interface manual for a *different* model (CI-100A). No
> CT100 protocol documentation could be found anywhere public. The probability
> estimates are calibrated guesses, not measurements, and they should be thrown
> away the moment real information arrives.

---

## What the datasheet establishes

Extracted from the infocard PDF:

| Thing | Value |
|-------|-------|
| Anschlüsse | **RS-232 zur PC-Anbindung; Kassenschublade; optional: USB** |
| Stromversorgung | AC 230V (interner Anschluss); optional 12V 7Ah Blei-Gel-Akku |
| Höchstlast / Teilung | 3/6 kg @ 1/2 g · 6/15 kg @ 2/5 g · 15/30 kg @ 5/10 g |
| Approval | "M eichfähig" (verifiable, class III) |
| Capacity | up to 1.000 PLU, 99 Warengruppen, 30 article quick-keys, 8 seller keys |
| Printer | Hi-Speed thermal receipt printer, 75 mm/s, 56 mm width |
| Keyboard | 60-key Embo-Druckpunkt, German legends |

Three consequences follow directly:

1. **USB is an option, not standard equipment.** It would be an add-on whose
   implementation CAS does not document publicly. The RS-232 port is the one
   that is always present.
2. **CAS markets the RS-232 as *"zur Datenpflege am PC"*** — uploading PLU and
   article master data from their Windows PC-Kit. Live weight output is nowhere
   advertised.
3. **This is not a bench scale.** It is a 230V mains POS terminal that already
   does weighing, pricing, receipt printing and cash-drawer control by itself.
   The optional lead-gel battery is a separate purchase and is mandatory for a
   market stall without mains power.

---

## The blocking unknown

Scales in this class have two very different serial personalities:

1. **Scale / ECR mode** — polled or continuous ASCII weight frames. CAS's own
   CI-100A (nearest documented relative, *not* the same model) does exactly
   this: single-character commands (`Z`+CR to zero, `T`+CR to tare), weight
   frames terminated with CR/LF/ETX plus status and polarity bytes, and its USB
   port described as a *"virtual RS232"*.
2. **PC data-maintenance only** — a proprietary PLU up/download protocol and
   nothing else.

If the CT100 is (1), live weight is a 1–2 week feature. If it is (2), live
weight is effectively impossible without CAS engineering support and no amount
of Android work changes that.

### Calibrated guesses

Capability and accessibility are separate gates. The joint column is the only
one worth planning against — a protocol that exists but lives only inside the
PC-Kit binary is, for a week of unfunded work, nearly the same as a protocol
that does not exist.

| Capability | Hardware can | Spec obtainable | Joint |
|------------|--------------|-----------------|-------|
| **Write** PLU master data down to the scale | ~95% | ~80% | **~75%** |
| **Read** PLU table back up | ~80% | ~80% | ~65% |
| **Pull sales / transaction data out** | ~50% | ~75% | **~40%** |
| Live pollable weight stream | ~50% | — | ~40% |
| Remote PLU recall ("select article 237 now") | ~25–35% | — | low |
| Android USB-serial layer works once bytes flow | ~90%+ | n/a | ~90%+ |

**Most likely single outcome, if data comes out at all:** a transaction or
receipt record emitted *on print* — completed line items with weight, price and
PLU — rather than a continuous or on-demand weight reading. That is genuinely
useful (it would feed the seller bookkeeping / CSV tax export work) but it is
**not** "read the live weight into the basket". The seller would still weigh and
confirm on the scale, and the app would receive the result a second later.

**Counter-evidence worth taking seriously:** CAS describes the TSE / DSFinV-K
export for the CT100-F/-T as Windows-based. That suggests they consider business
data to exit *through their software*, not through an open protocol — and for a
fiscal data path there are decent reasons not to make it freely pollable.

### Why remote PLU recall is the weakest of the write cases

Pushing the whole price table down offline is normal and legal; every
supermarket does it. Commanding the live article selection over a wire is a
different thing:

- `Z` and `T` are the two commands *every* scale protocol has. Their presence in
  the CI-100A docs says nothing about the rest of the keyboard being exposed.
- PLU recall needs a *parameterised* command (a PLU number argument), a
  different protocol shape from single-char ASCII and often simply absent.
- Unit-price entry and price computation sit **inside the verified function** of
  a price-computing scale. Remote zero/tare is harmless; remotely setting which
  price the scale computes against is exactly what type approvals are cautious
  about. There may be a regulatory reason the command does not exist.

---

## Drivers — the easy part

No kernel driver, no root, no vendor driver, no Windows COM-port handling.

- Android USB Host API plus [`mik3y/usb-serial-for-android`](https://github.com/mik3y/usb-serial-for-android)
  (3.11.x) covers CDC/ACM, FTDI FT232R/FT231X, PL2303, CP2102, CH340/CH9102.
  Since 3.5.0 it detects CDC by interface class rather than VID/PID, so generic
  virtual-COM devices need no custom prober.
- **Recommendation: ignore the CT100's optional USB and use the standard RS-232
  port with our own USB↔RS232 adapter.** Then we pick the chip (FT232R or CP2102
  — both solid on Android), it is documented, it costs ~€20, and we are not
  betting on an undocumented vendor USB implementation. Same price, strictly
  less risk.
- Per-chip defaults differ (CDC often 115200, FTDI/PL2303 often 9600). Always
  set parameters explicitly from the scale's own menu setting; mismatched serial
  parameters are the most common cause of "it doesn't work".
- If the USB option turns out to present as a **HID POS scale** (usage page
  0x8D) rather than virtual serial, the kernel `usbhid` driver binds the
  interface first and `claimInterface()` can fail. Workable, but noticeably more
  annoying than serial. One more reason to prefer RS-232.

The library gives a raw byte pipe. Frame parsing is ours:

- Buffer incoming bytes — reads return partial frames.
- Split on the terminator (CR/LF/ETX).
- Parse value and unit.
- **Only accept readings with the stability flag set.** Otherwise we book
  whatever number was on the display mid-wobble.

---

## Field and hardware issues — the part that will actually bite

Market-stall reality beats the code here.

- **USB-OTG host mode and charging.** The tablet must support host mode, and on
  most single-port devices **it cannot charge while hosting**. A full selling
  day on battery while driving a serial adapter is the single most likely
  real-world failure. Needs a powered OTG Y-hub, a second port, or simultaneous
  USB-PD + OTG. Verify on the *exact* device before planning anything.
- **RS-232 is ±12V on a DB9**, not TTL. Needs a real RS-232 adapter and the
  right cable pinout (straight vs null-modem). Buying the CAS PC-Kit cable is
  the safe move even if its software is discarded.
- **Serial parameters live in the scale's setup/service menu.** On a verified
  scale some of those menus sit behind a seal — check whether the needed
  parameter is reachable without breaking the Eichsiegel.
- **Hot-plug.** Connectors get pulled out at a stall. The app must handle
  detach/reattach, auto-reconnect, and show a clear "scale not connected" state
  rather than silently reporting a stale weight.
- **Permission UX.** Register `android.hardware.usb.host` plus a
  `USB_DEVICE_ATTACHED` intent filter with `device_filter.xml` so the grant
  becomes sticky instead of prompting on every plug-in.

---

## Eichrecht — the biggest non-technical risk

Selling goods by weight in Germany is *geschäftlicher Verkehr*, so the scale
must be geeicht under MessEG/MessEV. The CT100 is eichfähig, so that part is
fine.

The problem is the **data interface**. On comparable verified scales the
interface is explicitly marked *"nicht eichfähig"* (e.g. Kern BID-M) — the
output sits *outside* the verification. The moment the app takes that weight,
computes a price, and uses it as the basis of the transaction, the app arguably
becomes part of the measuring chain and would need its own conformity
assessment.

**The design that stays clearly on the safe side:**

> The CT100 remains the customer-facing weighing and pricing display and prints
> its own receipt. The app consumes the weight **only as a convenience input**
> for our own order and stock records. It never replaces the scale's display or
> its receipt.

This must be confirmed with the local Eichamt (or PTB) before going live. It is
a phone call, and it is much cheaper than finding out afterwards.

Parallel track: the CT100-F/-T TSE kit means CAS already treats this device as
the fiscal receipt issuer. If the app starts issuing the receipt instead, that
opens the KassenSichV / TSE question too.

---

## Recommended division of labour

The hardware is pointing at an answer. The CT100 has 30 article quick-keys and
8 seller keys under a physical Embo-Druckpunkt keyboard. At a market stall, with
cold or wet hands, that is a **strictly better input device than tapping a
tablet**. Do not fight it.

```
Newverse  --(push article master data)-->  CT100
          <--(pull transaction records)--

Seller presses the article key. The scale weighs, prices, and prints.
The app receives the result.
```

This also dissolves the Eichrecht problem: price computation never leaves the
verified instrument, and the app is purely a bookkeeping consumer. It is the
same shape as the existing seller/buyer article split — Newverse owns the
catalogue, the scale is a rendering of it.

---

## Fit with the Newverse architecture

Clean fit, no architectural strain. There is currently **zero USB code in the
repo** (confirmed by grep over `shared/src` and `androidApp/src`).

| Layer | Work |
|-------|------|
| `commonMain` | `expect class ScaleConnection` exposing `Flow<WeightReading>` (value, unit, stable, tare), behind a `ScaleRepository` interface in `domain/repository/`, matching existing repository conventions |
| `androidMain` | usb-serial-for-android implementation plus USB permission receiver; connection tied to Activity lifecycle, or a foreground service if it must survive backgrounding |
| `iosMain` | **Permanent no-op.** iOS has no USB host for non-MFi accessories. If iOS ever matters for selling, the only routes are MFi/ExternalAccessory or BLE — not this scale over USB |
| `androidApp` | Manifest `uses-feature android.hardware.usb.host`, `USB_DEVICE_ATTACHED` intent filter, `device_filter.xml` — **sell flavor only** |
| UI | Weight → `OrderedProduct` quantity on a sell screen. Small. No Firebase or Compose impact |

`minSdk 23` is fine; the USB Host API is API 12+.

---

## Effort, conditional on the protocol question

| Phase | Work | Days |
|-------|------|------|
| 0 | **Blocking:** protocol doc from CAS, or bench-capture bytes | 0.5 (+ wait) |
| 1 | Throwaway Android spike: open port, read, parse frames | 1 |
| 2 | KMP boundary, repository, ViewModel, UI, permission/hotplug/error states | 3–5 |
| 3 | Field hardening (power, cable, stability filter) + Eichamt clarification | 2–3 (+ external) |

**~1–2 weeks of development if the scale speaks a documented weight protocol.
Indefinite if it does not.**

---

## If the protocol is unobtainable

Three fallbacks for getting articles into the scale, in order of preference:

1. **Ask CAS** for the protocol description or an SDK/DLL. Dealers sometimes get
   it; there may be a paid integration kit.
2. **Buy the PC-Kit, run it against the scale, capture the serial traffic.** PLU
   download is highly regular data; the framing falls out of a few known-article
   diffs quickly. Reverse engineering for interoperability has specific standing
   in the EU, but the PC-Kit licence terms need reading first.
3. **Generate a PC-Kit import file** (CSV or whatever it ingests) and leave a
   human to press "upload" once a week. Ugly, but a ~30-minute feature that
   removes the entire protocol question from the critical path.

Fallback 3 is worth keeping in the plan regardless: the Newverse-side work
(export articles in their format) is useful however the protocol question
resolves. It makes "get our catalogue into the scale" a near-certain outcome
even if every protocol avenue fails.

---

## Next actions, in order

Both of the first two are hours of effort and collapse most of the uncertainty
in the probability table. Nothing else should be scheduled before them.

1. **Setup-menu check (~30 seconds, decisive).** In the CT100 setup/service
   menu, look for a **Protokoll**, **Kommunikation** or **ECR Type** parameter.
   - A selectable protocol / ECR type → live weight output is near-certain.
   - Only baud rate and nothing else → PC-maintenance only; expect the
     transaction-dump scenario at best.
   (CAS's ER Plus is integrated by third-party POS software via a selectable
   *ECR Type#12*, which is why this menu entry is the tell.)
2. **Phone CAS Deutschland** for the CT100 *Schnittstellen- /
   Kommunikationsbeschreibung*, and ask specifically whether the scale can
   output a weight value on request or continuously. Commercial-only product, so
   dealers are usually forthcoming.
3. **Bench test** (~€20 USB↔RS232 adapter plus a terminal): press a key on the
   scale and watch for bytes. Try 9600/8N1 first — CAS's historic default.
4. **Call the Eichamt** about the division of labour above, before any
   production use.
5. **Only then** decide whether to buy, and what.

### If the hardware has not been bought yet

Weight the protocol answer heavily. A scale whose manufacturer *publishes* the
serial protocol — and ideally offers documented weight output over USB-CDC or
Bluetooth SPP — is worth more to this project than the CT100's receipt printer
and 1.000 PLUs, most of which the app would duplicate anyway. CAS CI-series and
Kern's eichfähige models with documented interfaces are the shape to compare
against. Note that Bluetooth interfaces on verified scales are frequently marked
*nicht eichfähig*, which is survivable under the division of labour above but
should be confirmed per model.

---

## Sources

- [CT100 infocard (datasheet, the primary source here)](https://cas-waagen.de/pdf/cas-ct100-infocard-web.pdf)
- [CAS CT100 product page](https://cas-retail.com/ct100/) — "RS-232 zur Datenpflege am PC", PC kit, TSE kit
- [CAS CI-100A user manual](https://www.cas-usa.com/media/CI-100A%20User%20Manual%20v20170811.pdf) — serial commands, USB as virtual RS232. **Different model.**
- [CT100 Benutzerhandbuch, 2014 (manualslib, partial)](https://www.manualslib.de/manual/5868/Cas-Ct100.html) — not yet read in full; worth checking for a Schnittstelle section
- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android)
- [felhr85/usbserial](https://awesome.ecosyste.ms/projects/github.com%2Ffelhr85%2Fusbserial) — per-chip serial defaults
- [Dymo USB HID scale report format](https://new.antradar.com/blog-dymo-usb-scale-interface-specs) — usage page 0x8D layout
- [Mettler Toledo HIDPOS mode note](https://www.skulabs.com/help/en/articles/6415463-why-doesn-t-my-mettler-toledo-scale-read-package-weights-in-skulabs)
- [Eichgesetz / MessEG Grundlagen](https://blog.hoefelmeyer.de/eichgesetz-waagen)
- [Eichung von Waagen, Konformitätsbewertung](https://waagen-kurz.com/eichung.htm)
- [Kern IOC-M / BID-M](https://www.waagenwelt.com/waagen/Eichfaehige-Industrie-Plattformwaage-Kern-IOC-M) — Bluetooth marked *nicht eichfähig*
