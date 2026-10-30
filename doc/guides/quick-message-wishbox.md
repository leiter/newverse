# The Seller's Message Box in the Buy App

**Status:** Implemented 2026-10-30. Not yet run on a device, and the rules are not yet
deployed — **deploy the rules first**, see *Deploying this is two steps*.
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

## Files

| Where | What |
|---|---|
| `domain/model/SellerUiAction.kt` | `QUICK_MESSAGE`, the two input slots, `active`, `persistence`, `promptFor` |
| `domain/model/SellerInboxMessage.kt` | The message, and what it snapshots |
| `domain/repository/SellerInboxRepository.kt` | The interface |
| `data/repository/GitLiveSellerInboxRepository.kt` | RTDB implementation |
| `ui/state/QuickMessageState.kt` | What the field cannot know by itself |
| `buyMain/.../components/QuickMessageInput.kt` | The field |
| `buyMain/.../BuyAppState.kt` | `titlePromptFor`, `dismissedRows` |
| `buyMain/.../AppScaffold.kt` | The title slot, and who wins it |
| `buyMain/.../BuyAppViewModelUi.kt` | `sendPrompt`, `dismissPrompt`, `rememberDismissed` |
| `sellMain/.../UiActionListEditor.kt` | The slot, the switch, the persistence picker |
| `sellMain/.../SellerInboxScreen.kt` | The seller's end |
| `firebase/tests/seller-inbox.test.js` | 25 rule tests, mostly about who may read |
| `commonTest/.../SellerUiActionTest.kt` | The model's half |
| `testBuy/.../BuyAppViewModelQuickMessageTest.kt` | 20 tests: live, sent, and gone |
