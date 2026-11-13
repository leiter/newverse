# The Seller's Message Box in the Buy App

**Status:** Message box implemented 2026-10-30, rules deployed 2026-11-05. The choice
picker was added 2026-11-13 with its rules deployed to dev and prod the same day.
Neither has been run on a device.
**Scope:** `shared/src/commonMain` (model + repositories), `buyMain` (the field, app
state), `sellMain` (editor slots, inbox screen), `firebase/database.rules.json`
(+ `firebase/tests/seller-inbox.test.js`)

How the seller puts a text field with a send button into the buy app's top bar without
an app release, where what the buyer types goes, and how the buyer gets out of being
asked.

Read `seller-ui-actions.md` first. This is the same mechanism — a row the seller
publishes, rendered by every buyer's app — with an answer coming back, and everything
that guide says about slots, kinds, audiences and the no-scheme rule still holds.

---

## What it does

The seller writes a row like this on their profile screen:

| Field | Example |
|---|---|
| `location` | Top bar, home screen (message box) |
| `value` | `Was fehlt euch im Angebot?` |
| `kind` | `QUICK_MESSAGE` (never the seller's choice — the slot decides) |
| `label` | `Was fehlt euch im` (derived; the seller is not asked) |
| `persistence` | stays until the customer hides it |
| `active` | on |

Every buyer's app replaces that screen's title with the question, a field and a send
button. What a buyer types lands in `seller_inbox/{sellerId}`, which the seller reads
from **Wünsche** on their own profile screen. Switching the row off or deleting it
takes the field away again, on phones that already have the app open.

Its first use is a general wishbox. It is deliberately not a chat: there is nothing to
reply to, and a buyer who wants a conversation has the messaging screen.

## The two nodes run in opposite directions

```
seller_ui_actions/{sellerId}/{actionId}   .read  auth != null          # the question
                                          .write auth.uid === $sellerId

seller_inbox/{sellerId}/{messageId}       .read  auth.uid === $sellerId  # the answers
                                          .write create: own buyerId
                                                 delete: the seller
```

One node cannot be both, for the reason `seller_ui_actions` could not live on
`seller_events`: these are different directions with different readers. The question
is configuration every buyer must read; the answers are a pile of free text only the
seller may read.

**A buyer cannot read the inbox at all** — not even the message they just sent. One
buyer's wishes are not another's business, and no buyer-facing feature needs to read
one back. The rule tests pin it from the attacker's side.

**A buyer cannot edit or delete a sent message.** The create branch requires
`!data.exists()`, so a send is add-only. The failure this prevents is concrete: the
seller may already have ordered the Dinkelmehl, and a buyer rewriting the wish behind
them would make the inbox a record of nothing.

**Whoever writes a row, `buyerId` must be their own uid.** That makes the sender an
identity the seller can act on — approve, block, look up — rather than a name anyone
could type. `buyerName` beside it is unverified by nature and may be empty; a guest has
no name to give.

**The seller may send to their own inbox**, which falls out of the create branch and is
left that way on purpose: a seller installing the buy app on the same account to check
their own wishbox will do exactly that, and a refusal there looks like a broken
feature. There is nothing to protect — it is their own data and nobody else reads it —
and they still cannot edit a row, which is the rule that matters.

## Not gated on approval

A signed-in guest can send. This is the same decision published buttons made, taken
knowingly: a wishbox the seller published to everyone is answerable by everyone, and a
field that appears only after approval reads as broken to a new install. The seller can
narrow a box to `APPROVED_ONLY` per row if inbound noise becomes a problem, and the
`audience` field is how, rather than a rule change.

This is the feature's open risk and it is worth naming: anonymous auth is free and
unlimited, so the inbox is writable by anyone who installs the app. With one seller the
blast radius is a list they can delete from. A rate limit would have to be a Cloud
Function or a per-buyer index; neither is built.

## The message snapshots what it answered

`SellerInboxMessage` carries `buyerName`, `prompt` and `requestKind` — all derivable at
write time, none of them derivable later. The buyer renames themselves, the seller
rewords the question, a later app version retires a kind of box. **A wish filed under a
question the seller no longer asks means nothing**, and a seller reading a list of
answers has to see what was asked. Same reasoning as `OrderedProduct` snapshotting the
price it was charged rather than pointing at a price that moves.

`requestId` sits beside the snapshot for grouping, not for lookup: the row it names is
usually gone by the time anyone reads the message.

`requestKind` is stored although there is exactly one kind today. When a second kind of
box exists — feedback, a rating — the seller reading one list should not have to infer
which box each answer came through from its wording.

## Where the field goes, and who wins the title

`TOPBAR_HOME_INPUT` and `TOPBAR_PROFILE_INPUT`, each holding exactly **one** row.

A text field takes the whole title slot, more sharply than an announcement does: a
field squeezed in beside the app name fits nothing worth sending. One per slot because
two questions in one field cannot both be answered, and a buyer's answer would not say
which it replied to.

Two input slots exist because **the home title is contended**. An announcement and a
question cannot both have it, and the tie is broken in `AppScaffold`, visibly: *the
announcement wins.* A sentence the seller is telling every buyer outranks a question
only some will answer, and the box comes back the moment the announcement is read and
closed. A seller running both puts the announcement on home and the wishbox on profile.

The basket title still carries an item count and a total the buyer is acting on, and
nothing is allowed to replace it.

## A question is text, so the no-colon rule is lifted — for it too

`QUICK_MESSAGE` joins `ANNOUNCE` in the exemption from the `value` rule's ban on `:`
and a leading `/`:

```
"value": "… && (kind === 'ANNOUNCE' || kind === 'QUICK_MESSAGE' || (!contains(':') && !beginsWith('/')))"
```

Safe for the same single reason, restated because it is the reason: **a question has no
path to the platform's URI handler.** `ActionKind.QUICK_MESSAGE` has an empty `scheme`,
an identity `normalize`, and is rendered as a placeholder, never handed to
`LocalUriHandler`. `OPEN_URL` is untouched, and the rule tests pin both halves.

Without it, "Frage: was fehlt euch?" would be refused by the database, and running it
through the URL normalizer would quietly eat the opening words.

## Two new flags on every published row

Both apply to buttons and announcements as well, not only to message boxes.

### `active` — off, not gone

The seller's off switch, beside the delete. Both reach a buyer with the app already
open; they mean different things to the seller. A deleted row is gone and has to be
retyped; a deactivated one is parked. A wishbox that only makes sense in spring, a link
to a list that is out of date for a fortnight: neither is worth losing the wording of.

This revises what `seller-ui-actions.md` says about removal being a delete and not a
flag. That reasoning was about *dangling references* and it still holds — nothing points
at a row by id, so there is nothing to protect by keeping a withdrawn row readable.
What it missed is that the seller sometimes wants the wording back.

A deactivated row **still holds its slot**, deliberately: it is parked *there*, and a
seller who turns the announcement off for a week does not expect to find something else
in its place.

Absent reads as **active**, because rows written before the switch existed were being
shown and must go on being shown — the rule a missing `audience` follows.

#### The one trap this flag set

`isUsable` used to mean both "finished" and "shown", and a deactivated row is finished
but not shown. Filtering the editor's save on it made switching a row off **impossible
to save**. The two notions are now separate:

| | means | asked by |
|---|---|---|
| `isComplete` | the seller has finished filling it in | the editor, the repositories |
| `isUsable` | `isComplete` **and** switched on | every renderer |

### `persistence` — what makes a row go away

| Value | The row goes when |
|---|---|
| `UNTIL_CANCELLED` | the buyer closes it (the default) |
| `HIDE_ON_ACTION` | the buyer acts on it — taps the button, sends the message |

Both exist because the seller's rows are not all the same kind of thing and the app
cannot tell which from outside. A one-off question — "kommt ihr am Samstag?" — is
answered once and should then be gone. A standing wishbox should stay, because a buyer
who sends one wish this week has another next week. Guessing either way is wrong for
half the rows.

Honoured **only on success.** A refused send leaves the box, and the buyer's typed
text, exactly where they were: a buyer whose answer never arrived has not answered.

Not offered for an announcement, which has no action to hide on.
`ActionKind.hasAction` says so, `readyToStore()` settles the value rather than refusing
it — the seller was never shown the picker, so whatever the field defaulted to is not
their choice — and the rules refuse the pairing anyway.

Absent reads as `UNTIL_CANCELLED`, the safe one: the worst it does is leave something on
screen that the buyer can remove themselves.

## Cancelling: the buyer's way out

The close button is as much the point of this feature as the field is. An announcement
can be read and ignored; **a text field in the title of a screen the buyer opens every
day is a standing request.** A buyer who does not want to be asked closes it, and it
stays closed.

It is keyed and stored exactly like a dismissed announcement, through
`SellerUiAction.dismissalKey` — the row id *and* an FNV-1a hash of the text — in
`AnnouncementDismissalStorage`, per install rather than per buyer. That guide's section
on why has not changed. What the key buys here is sharper than for an announcement:
**re-wording the question asks again.** A buyer who answered "Was fehlt euch?" has not
answered "Kommt ihr nächsten Samstag?", and a key on the id alone would have silently
swallowed the second question. That is the silent-stale-configuration failure this
mechanism has now shipped twice.

One writer for all of it: `rememberDismissed`. A closed announcement, a closed box, a
tapped hide-on-action button and a sent-through box all end there, so there is one
answer to "what does this device no longer show" and one place that fails soft — state
is updated whether or not the write lands, because the buyer asked for the row to go
away and it going away is the part they can see.

`BuyAppState.dismissedAnnouncements` is accordingly now `dismissedRows`.

## The draft stays in the field

What the buyer types is `remember`ed in `QuickMessageInput` and leaves it once, when
send is pressed. `QuickMessageState` holds only what the field cannot know by itself:
whether a send is in flight, whether the last one failed, and that one has landed.

Not because state could not hold the text, but because every alternative this mechanism
has tried was worse. The seller's own editor handed each keystroke to a repository and
ate half-typed rows — the first button ever published on dev was labelled `g`. A draft
in app state would recompose the whole top bar per character.

`sentCount` is a counter rather than the id of the last message, because its only job
is to tell the field a send landed so it can clear itself, and a buyer sending a second
wish through the same box would not change a key.

## What the buyer sees fail

A refused send is **said out loud**, unlike a failed dismissal. It is the one failure
the buyer cannot see for themselves and would not retry: a wish that silently went
nowhere. The error is held as a `StringResource` because a ViewModel has no resource
environment to resolve one in — `getString` throws in a plain JVM test, which is the
lesson the change-password dialog's error slots learned — so `AppScaffold` resolves it,
shows it once, and dispatches `ClearPromptError`.

The success confirmation goes through `runCatching { getString(...) }` for the same
reason: the message is already sent, and a label that cannot be resolved must not turn
a success into a reported failure.

## A field in a top bar

`BasicTextField`, not `OutlinedTextField`. A Material text field is taller than a top
bar and brings a container colour of its own, which would put a light box in the middle
of the brown bar. The field, the cursor and both icons take their colour from
`LocalBarColors`, like everything else in there.

The send icon is **dimmed rather than hidden** while there is nothing to send: a button
that appears as you type would move the close button under the finger reaching for it.
The spinner replaces the send button in place rather than sitting beside it, so the row
does not reflow while the write is in flight.

The placeholder **scrolls** when the question is wider than the field
(`basicMarquee`), the same device `SellerAnnouncementTitle` uses and for the same
reason: the seller is writing a question, not a label, and half a question is worse
than none — more so here, where the field shares the bar with two buttons. It animates
only on actual overflow, so a short question sits still, and it is drawn only while the
field is empty, so nothing moves behind what the buyer types.

Accessibility: the question is drawn as a placeholder and a placeholder is not reliably
announced, so the field carries it as its `contentDescription`. Send and close are
labelled separately — they are opposite answers to the same request and must not be
confusable.

## The seller's end

`SellerInboxScreen`, reached from **Wünsche** on the profile screen, beside the editor
that publishes the box: a published question with no visible way to the replies is a
question nobody reads.

A flat, newest-first list, each row showing the question above the answer — the seller
knows what they asked and is reading for what came back, but a list of bare answers to a
question that has since been reworded is unreadable, which is what the snapshot is for.
Deleting is the whole lifecycle; there is no handled flag, because nothing references a
message by id.

## Deploying this is two steps, and the rules are the first

`$actionId` and `$messageId` both end in `"$other": { ".validate": false }`, which
rejects any field the ruleset does not name. **This change adds `active` and
`persistence` to `seller_ui_actions`**, so until the rules are redeployed every write to
that node is denied — not the new fields, the *whole row*. That has bitten this feature
once already, with `audience`.

The order is: rules first, or app and rules together, never app first.

The refusal is at least visible: `SellerProfileViewModel.uiActionError` carries it to
the editor.

## Known limits

- **No rate limit.** See *Not gated on approval*.
- **The inbox has no unread count and no notification.** The seller finds new wishes by
  opening the screen. A badge would need a per-seller read cursor, which is the
  machinery `seller-buyer-sync-channel-design.md` describes and nobody has built.
- **Not localized.** The question is the seller's own words, so a German question
  reaches an English-locale buyer as written — the trade `seller-ui-actions.md` makes
  for labels, for the same reason.
- **One message at a time.** Nothing prevents a buyer sending fifty, and nothing
  deduplicates them.
- **The inbox is not exported.** It is read and deleted in the app; the CSV work in
  `seller-bookkeeping` does not cover it.

## The choice picker

**Built 2026-11-13. Rules deployed to dev and prod the same day. Not yet run on a
device.** The second kind of box: a question with a fixed set of answers.

| Field | Example |
|---|---|
| `location` | Top bar, home screen (question mark) |
| `kind` | `CHOICE` (the slot decides, as always) |
| `value` | `Was sollen wir nächste Woche mitbringen?` |
| `options` | `["Bergkäse", "Dinkelmehl", "Eier", "Honig"]` |
| `selectionMode` | `MULTI` (absent reads as `SINGLE`) |
| `label` | `Was sollen wir` (derived; the seller is not asked) |

### What makes it different from a message box

**The seat.** A message box takes a screen's whole title; a picker is an icon among
the top bar's actions that opens a panel over the content. Two things follow, and
both are the reason the slots are separate rather than the kind being a flag:

- **A picker and an announcement can both be published.** The home title holds one
  thing and the announcement wins it, so a wishbox there is hidden until the
  announcement is read. A picker never contends for it.
- **A picker needs no dismissal.** Nothing is put in front of a buyer who has not
  tapped the icon, so there is nothing to close. The close button collapses the panel
  and the icon stays. The only thing `dismissedRows` can hide is a picker the seller
  set to `HIDE_ON_ACTION` that this buyer has already answered — which is the seller
  running a one-off poll, exactly as they asked.

### Answers are labels, never positions

`SellerInboxMessage.selections` stores the chosen option **strings**, and the message
snapshots the whole `options` list beside them.

Storing a position instead was considered and rejected. It is smaller, and it is
wrong for this node's own stated reasons: `requestId`'s doc says the row a message
names is usually gone by the time anyone reads it, so an index points at something
that may not exist — and worse, at something that *moves*. A seller inserting an
answer at position 2 would silently re-map every reply already filed, with no error
and no way to notice. Same argument as `OrderedProduct` snapshotting the price it was
charged.

The cost is that rewording an answer splits its tally. That is the honest result: a
reworded question is a different question, and `dismissalKey` re-asks everyone when
the options change, so the split is the intended behaviour rather than an accident.

`text` carries the same labels joined with ", ", so a seller's inbox that knew nothing
about pickers would still show a readable line, and `isSendable` — which asks about
text — covers a choice without a second rule.

### `dismissalKey` had to grow

It was `"$id:${value.stableHash()}"`. It now hashes the question **and** the options,
because changing the answers changes the question as the buyer experiences it. A row
with no options hashes exactly what it hashed before, so every announcement and
message box already dismissed on a device stays dismissed — pinned by a test.

### The caps are arithmetic, not taste

8 answers, 48 characters each. Eight joined with ", " is 398 characters, inside the
500 the `text` rule enforces, so **a buyer can never assemble an answer the database
then refuses**. Two distinct answers is the minimum: the rules can require a non-empty
list but cannot count it, so `isComplete` does the counting and an unanswerable picker
never reaches a buyer as an icon that opens an empty panel.

### `options` may come back as an object

RTDB has no list type. A dense array reads back as a list, but a seller deleting a
middle answer leaves `{"0":…, "2":…}`. `stringListFromStored` accepts both and sorts
a map by its numeric key — without it, one seller edit would empty the picker on every
buyer's phone.

### The icon is the seller's, from a set the app ships

`ActionIcon` is a closed enum of 18 names — `HELP`, `CART`, `LEAF`, `BREAD`… — and
the picker is drawn with the one the row names, or with a question mark when it names
none. The same discipline `ActionKind.scheme` follows: the row names an asset the app
**has**, rather than carrying something the app must trust. The top bar is the one
piece of chrome a buyer reads as the app's own, so a seller who could store a URL or
a drawable name could put an arbitrary image there.

**A name, not a number.** A number is a position in a list somebody can renumber:
delete the fifth icon, reuse the five later, and every stored row means something else
with no error and no way to notice. That is the failure `selections` rejected indices
over, and it is no less real for being cosmetic.

**An unknown icon degrades; it does not skip the row.** This is the one place that
differs from `ActionKind` and `ActionLocation`, which drop a row whose stored name
they do not know — unknown *behaviour* cannot be guessed at safely. An icon is
cosmetic, so an app version that meets a newer icon draws the default rather than
taking the picker away.

**The default is per kind, and it is codified too.** `ActionKind.defaultIcon` says
what a row with no icon of its own is drawn with — `HELP` for a picker — and
`SellerUiAction.effectiveIcon` is the one place that resolves *which icon*, leaving
the UI to resolve only *which drawing*. So `ActionIcon.vector()` takes no fallback:
by the time it is called there is nothing left to fall back from.

This matters more than it looks. The default started life hardcoded at the three
places that draw a picker, which is three independent assertions that a picker is a
question mark — exactly the duplication codifying the icon was supposed to remove.
Now one `when` branch in `ActionIcons.kt` is the only place in the app that knows what
a question mark looks like, and a test pins `ActionKind.CHOICE.defaultIcon` to it.

Per kind rather than one global constant, because the obvious global candidate is
worse than generic: `MoreVert` means "overflow menu", so a buyer would tap it
expecting a menu and get a panel.

**A `when`, not a `Map`.** `Icons.Default.*` are lazily built `ImageVector`s, so a
top-level map would construct every icon in it at class-init to answer one lookup. It
is also the shape that survives R8 without a keep rule — this app has already shipped
a bug where R8 removed enum constants it could not see used (`139fc2a`).

The icon is **not** part of `dismissalKey`. Recolouring a question is not asking a new
one, and folding it in would un-dismiss every closed row the moment a seller tidied up
their icons.

### Counting the answers is the seller's problem

The same hole the wishbox has, and sharper here. The inbox is add-only, has no rate
limit, and a buyer cannot read it back — so nothing stops one person answering fifty
times, and a poll is exactly where that stops being theoretical, because the seller
*will* count the rows. **A seller tallying a picker must deduplicate by `buyerId`.**
`HIDE_ON_ACTION` plus the device's dismissal makes repeat answers inconvenient for an
honest buyer; it is not a control.

## Files

| Where | What |
|---|---|
| `domain/model/SellerUiAction.kt` | `QUICK_MESSAGE`, `CHOICE`, the input and picker slots, `SelectionMode`, `options`, `promptFor`, `choiceFor` |
| `domain/model/SellerInboxMessage.kt` | The message, and what it snapshots |
| `domain/repository/SellerInboxRepository.kt` | The interface |
| `data/repository/GitLiveSellerInboxRepository.kt` | RTDB implementation |
| `ui/state/QuickMessageState.kt` | What the field cannot know by itself |
| `buyMain/.../components/QuickMessageInput.kt` | The field |
| `buyMain/.../components/ChoicePicker.kt` | The question mark and the panel |
| `data/repository/RtdbStringLists.kt` | Reads a list RTDB may hand back as a map |
| `domain/model/ActionIcon.kt` | The closed icon set, and why a name not a number |
| `commonMain/.../ui/components/ActionIcons.kt` | `vector()` — the one `when` that names a drawable |
| `buyMain/.../BuyAppState.kt` | `titlePromptFor`, `dismissedRows` |
| `buyMain/.../AppScaffold.kt` | The title slot, and who wins it |
| `buyMain/.../BuyAppViewModelUi.kt` | `sendPrompt`, `dismissPrompt`, `rememberDismissed` |
| `sellMain/.../UiActionListEditor.kt` | The slot, the switch, the persistence picker, the answer fields |
| `sellMain/.../SellerInboxScreen.kt` | The seller's end |
| `firebase/tests/seller-inbox.test.js` | 25 rule tests, mostly about who may read |
| `firebase/tests/choice-picker.test.js` | 21 rule tests; labels-not-indices is enforced there |
| `firebase/fixtures/choice-picker-dev-rows.json` | Seedable dev rows, keyed by RTDB path |
| `commonTest/.../ChoicePickerModelTest.kt` | 20 tests: completeness, storage, the key |
| `commonTest/.../SellerUiActionTest.kt` | The model's half |
| `testBuy/.../BuyAppViewModelQuickMessageTest.kt` | 20 tests: live, sent, and gone |
