# Expense Tracker

A native Android app for tracking monthly income and expenses. Reads bank/UPI SMS
automatically, accepts manual entries, and keeps everything on the phone until you
choose to export it.

**Install:** `dist/ExpenseTracker-1.0.apk` (6.7 MB, signed, Android 8.0+)

---

## Installing on your phone

1. Copy `dist/ExpenseTracker-1.0.apk` to the phone (USB, Drive, or WhatsApp to yourself).
2. Tap it. Android will ask to allow installing from that source — allow it.
3. Open **Expense Tracker**.

Or over USB with debugging on:

```bash
adb install -r dist/ExpenseTracker-1.0.apk
```

The APK is signed with `keystore/expensetracker.jks` (password `expense2026`).
**Keep that keystore** — updates must be signed with the same key or Android will
refuse to install over the existing app.

---

## What it does

### Recording money

- **Expense or Income**, each with its own set of categories.
- **Category** — 22 expense and 9 income categories seeded, including
  *Shopping – Personal* and *Shopping – Home* as separate buckets. Fully editable:
  add, rename, recolour, change the emoji, delete.
- **Person** — tag who the money was spent on or received from. Add people inline
  while entering a transaction.
- **Account** — Cash, Bank Account, Credit Card, Debit Card, UPI/Wallet, each with an
  opening balance. Editable.
- **Merchant/shop**, **note**, and **date** on every entry.

### Automatic SMS reading

The **SMS** tab reads bank and UPI alerts already on the phone and as they arrive:

- Pulls out amount, direction (debit/credit), merchant, and the masked account/card.
- Guesses the category from the merchant (Swiggy → Food & Dining, BigBasket →
  Groceries, Amazon → Shopping – Personal, and so on).
- Everything lands in a **review queue** first — approve or ignore each one, or
  "Add all". Flip *Record without reviewing* to skip the queue entirely.
- Deliberately conservative: OTPs, payment reminders, failed transactions, collect
  requests, promotional offers, and balance-only alerts are ignored. A message with no
  clear direction and amount is never guessed at.
- Handles the classic trap where a bank quotes both the amount and the remaining
  balance in one message — the balance figure is never mistaken for the amount.
- Duplicate protection via the bank reference number, so a rescan never double-records.

SMS reading is entirely optional and off until you grant permission. Messages are
parsed on-device and never leave the phone.

### Reports and graphs

- **Home** — balance, earned, spent, optional monthly budget meter, category donut,
  6-month income-vs-spending bars, 30-day spending trend line, spend-per-person bars,
  recent transactions.
- **Reports** — summary (net saved, savings rate, largest category, average per day),
  breakdown donut switchable between **Category / Person / Account**, ranked category
  bars, top merchants, 6-month table, account balances, and all-time totals.
- **Ledger** — every transaction grouped by day with running day totals, full-text
  search, and filters by type, category, person, account, and date range.
- Period selector throughout: Week / Month / Year / All, with back-and-forward stepping.

### Saving your data

Data lives in a local SQLite database on the phone. Nothing is uploaded anywhere.

| Action | What it does |
| --- | --- |
| **Save backup to Drive or files** | Opens Android's file picker — choose Google Drive, Downloads, or any location. Writes a complete JSON backup. |
| **Share backup…** | Opens the share sheet with the backup attached — Drive, Gmail, WhatsApp, Quick Share. |
| **Link a backup folder** | Pick a folder once (a Google Drive folder works). The app then writes a fresh backup there automatically, once a day, whenever you open it. |
| **Restore from a backup file** | **Merge** adds the backup on top of what you have; **Replace** clears current transactions first. Categories, people, and accounts are matched by name, so restoring never creates duplicates. |
| **Export CSV** | Spreadsheet of the selected period — date, time, type, amount, category, person, account, merchant, note, source. |

### Preferences

Currency symbol (₹ default, or any symbol), monthly budget, and Light / Dark / System
theme.

---

## Google Drive: how it is wired

The app talks to Drive through Android's own file picker and share sheet rather than
the Drive API. That means it works the moment you install it — no Google Cloud project,
no OAuth client, no sign-in. Drive appears as a destination anywhere you save or share
a backup, and a linked Drive folder gets a fresh backup daily.

If you later want true background sync (silent upload with no picker), that needs a
Google Cloud OAuth client registered against this app's signing fingerprint:

```
Package:  com.expensetracker
SHA-1:    10:31:73:72:31:2E:F0:C7:2E:9F:DF:59:15:29:FB:CA:CA:53:31:FF
SHA-256:  FD:BA:89:AD:0C:12:A7:7F:6D:C9:64:25:D7:18:C4:60:F2:24:A2:9B:27:57:A5:45:8E:CD:2F:76:60:3A:8F:3C
```

That is a separate piece of work and only you can create the client ID, since it lives
in your Google account.

---

## Building from source

Requires JDK 17+ and the Android SDK (compileSdk 36, build-tools 36).

```bash
./gradlew assembleRelease
```

Output lands in `app/build/outputs/apk/release/`. Unit tests:

```bash
./gradlew testDebugUnitTest
```

### Project layout

```
data/           SQLite schema, repository, settings, backup/restore
data/sms/       SMS parser, ingest pipeline, broadcast receiver
ui/             ViewModel, navigation, formatting
ui/charts/      Donut, grouped bars, ranked bars, trend line — drawn on Canvas
ui/screens/     Dashboard, Ledger, Add/Edit, Reports, SMS, Settings, Manage
```

Kotlin + Jetpack Compose + Material 3. No Room, no dependency-injection framework, and
no charting library — the charts are hand-drawn so the app stays small and has no
rendering dependencies to break.

---

## Verified behaviour

Checked on an Android 16 emulator against the signed release build, plus 25 JVM unit
tests covering the SMS parser and amount formatting.

- App launches, and every tab renders, with both an empty database and 218 transactions.
- Manual add flow saves and updates the dashboard immediately.
- Four SMS injected live: an HDFC UPI debit, an ICICI card spend, a Kotak salary-style
  credit, and an OTP. The three real ones were parsed with correct amounts, merchants,
  and masked accounts; the OTP was rejected. Balance figures quoted in the messages were
  correctly ignored.
- Approved SMS became ledger entries with auto-assigned categories and accounts.
- Backup wrote valid JSON (218 transactions, 31 categories, 5 people, 5 accounts).
- Restore with **Replace** returned exactly 218 transactions with no duplicated
  categories and all person tags intact.
- Share sheet offers Drive/Gmail/Quick Share with the file attached and no permission
  warnings.

### Known limits

- Release builds ship **unminified**. R8 shrinking is switched off in
  `app/build.gradle.kts` because it needs more free memory than the build machine had;
  turning `isMinifyEnabled` and `isShrinkResources` back on roughly halves the APK.
- SMS rules are tuned for Indian bank and UPI formats. Other countries will need new
  keyword rules in `SmsParser.kt`.
- Some phone makers (Xiaomi, Oppo, Vivo) restrict background SMS access. If live
  capture stops, use **Scan new** in the SMS tab, which reads the inbox directly.
- Multi-currency and attachments are not implemented.

## Recurring transactions

Set a payment up once and it records itself each time it falls due: EMIs with a fixed
term, monthly and annual subscriptions, rent, a salary credit, or a monthly savings
contribution such as a chit fund or recurring deposit.

Reach it from **Settings -> Recurring transactions**, or the **Coming up** card on the
dashboard once a rule exists.

Each rule carries:

- **Expense, Savings or Income** — the three pills at the top of the editor.
- **Amount, category, account, person and note** — exactly like a normal transaction.
- **How it is paid** — mode, method and bank, from the same editable lists the
  transaction editor uses. Whatever is chosen is copied onto every payment the rule
  creates, so a Netflix debit records itself as Auto-debit / Standing Instruction / HDFC
  without being retyped each month.
- **How often** — daily, weekly, monthly, quarterly or yearly, with an interval, so
  "every 2 months" or "every 6 months" are both expressible.
- **When it ends** — never (an open-ended subscription), after N payments (a 36-month
  EMI), or on a chosen date.

### Savings rules

A savings rule is a transfer between two of your own accounts rather than money leaving
your hands, so it asks for **Money comes from** and **Money goes to** instead of a
category, and it will not save until both are set and different from each other.

Each run moves the amount out of the funding account and into the pot, changing both
balances while staying out of income, spending and category totals — a ₹10,000 monthly
chit fund never shows up as ₹10,000 of expenses. Savings rows are shown in the app's
primary colour with no +/- sign, since nothing was earned and nothing was spent.

Create the destination first under **Settings -> Accounts** (a "Chit fund" or "RD"
account), then point the rule at it.

The editor previews the next three dates as you type, and an EMI shows a progress bar,
payments remaining, and the total across the whole term.

### How the dates are worked out

Every occurrence is computed as an offset from the start date rather than by stepping
forward from the previous one. That matters for monthly rules late in the month: stepping
would drag the date earlier and never recover, so a payment set for the 31st would slip to
the 28th in February and stay there. Offsetting clamps only where it must:

```
31 Jan -> 28 Feb -> 31 Mar -> 30 Apr -> 31 May
```

A yearly rule starting 29 February behaves the same way, landing on the 28th in non-leap
years and back on the 29th in leap years.

Anything that fell due while the app was closed is filled in on the next launch, oldest
first, so reopening after three months records all three payments rather than only the
latest. Catch-up is capped at 240 occurrences as a runaway guard, and a finished rule
deactivates itself so it drops out of the upcoming list.

Deleting a rule asks whether to keep the transactions it already recorded; keeping them
detaches the history rather than destroying it. Rules travel in backups alongside
everything else.


## Quick entry and access

Added after looking at what Android expense trackers converge on for day-to-day use.

**Home screen widget** — this month's spending and what is left, with an **Add** button
that opens the entry screen directly. It reads the same database the app does, so it is
always current, and it redraws whenever the ledger changes.

**Launcher shortcuts** — long-press the app icon for *Add a transaction* and
*Import from a message*, skipping the dashboard entirely.

**Calculator in the amount field** — type `450+120*2` or `1250/3` and the running result
shows underneath before you commit it. The numeric keypad has no operator keys, so the
field carries its own `+ − × ÷ ⌫` strip. Malformed input evaluates to nothing rather than
a wrong number: `450 120` is rejected instead of silently becoming 450120.

**Merchant suggestions** — names already used appear as chips under the merchant field,
most frequent first.

**Undo** — deleting a transaction offers *Undo* on the snackbar, since it is the one
destructive action reachable in a single tap.

## Transfers between your own accounts

Moving money from Cash to a Bank Account is not income or spending, but until now the only
way to record it was as one of each, which inflated both totals and polluted the category
breakdown.

**Transfer** is now a third transaction type. It takes a *From* and a *To* account — the
destination list excludes whichever account is already the source — and carries no category
or person, because neither applies. A transfer:

- moves both account balances, and
- is excluded from income, spending, category, person and merchant totals, the six-month
  trend, daily spend and the day subtotals in the ledger.

In the ledger it reads `Bank Account -> Credit Card` in a neutral colour, rather than being
coloured like spending.

## Category budgets

Any expense category can carry a monthly budget (**Settings -> Categories**, or *Edit* on
the dashboard's budget card). The dashboard then shows spend against budget per category,
with the bar turning amber past 80% and red once it is over, plus the amount overspent.
This sits alongside the single overall monthly budget, which still works as before.


## Importing a message without any permission

Google's pilot inspects four permissions: `RECEIVE_SMS`, `READ_SMS`, the notification
listener, and accessibility. Swapping SMS reading for a notification listener therefore
does not help — that route is blocked too.

Both editions instead carry a permission-free import path, which is the primary way the
lite build gets bank data in:

- **Share** — in your messaging app, open a bank alert, tap Share, pick Expense Tracker.
- **Paste** — copy the message, then **Settings -> Import from a message -> Paste**.

The message runs through exactly the same parser as automatic capture, so the amount,
merchant, masked account, category and account assignment all come out the same. The
duplicate guard is shared too: importing the same message twice is refused.

Nothing is read from the inbox — the user hands over one message at a time — so no
permission is declared, requested or granted.


## Importing a CSV or Excel file

**Settings -> Import -> Choose a CSV or Excel file.**

Columns are matched by name rather than position, so exports from other money
apps generally work untouched. The reader handles:

- **Excel serial dates** (`46266.69` = 1 Sep 2026), converted through UTC and rebuilt in
  the device's zone so a date never slips a day.
- **Emoji written as XML entities** (`&#x1f968; Snacks`), decoded and lifted out of the
  category name to become its icon rather than appearing twice.
- **Quoted CSV** with embedded commas, escaped quotes and newlines.
- **Missing cells** in .xlsx, which are absent from the XML entirely — column positions
  come from each cell's reference, so values cannot shift left.

Categories, accounts and payment methods that do not exist yet are created as the rows are
read. A preview shows how many rows are readable and the first few amounts before anything
is written, and re-importing the same export is refused: rows are fingerprinted on date,
amount and payee.

.xlsx is read by unzipping the file and parsing its XML directly — no spreadsheet library,
so the APK stays at 6.8 MB.

Verified against a 1,185-row Money Manager export: all 1,185 rows read, 1,185 imported,
28 categories and 4 accounts created automatically.

## Payment mode, method and bank

Every transaction can record **how** it was paid: a mode (Cash, Online, Card, Cheque,
Auto-debit), a method (UPI, Credit Card, Net Banking, NEFT/IMPS, Wallet…) and a bank. Each
list is seeded with common Indian values and each has a **+ Add** for your own. The ledger
shows the method and bank on the transaction row, and imports map the source file's account
column onto the method automatically.

## Layout and navigation

- **No horizontal scrolling anywhere.** Every chip row — categories, accounts, people,
  payment details, schedules, colours, icons — now wraps onto multiple lines instead of
  scrolling sideways. The app scrolls vertically only.
- **Nothing scrolls under the status bar.** An opaque band sits behind the clock, so list
  content no longer bleeds into the system area.
- **Instant tab switching.** The default slide transitions are disabled, so moving between
  Home, Ledger, Reports and Settings is immediate rather than animated.


## Two editions

Google's enhanced fraud protection (piloted in India) blocks sideloading of any app that
declares SMS permissions — the dialog reads "App blocked to protect your device" and offers
no "install anyway". That is triggered by the permission itself, not by anything about this
build, so the project ships two editions from the same code:

| File | SMS auto-capture | Installs by tapping the APK |
|---|---|---|
| `ExpenseTracker-1.4-full-sms.apk` | yes | no — blocked by Play Protect; install over USB |
| `ExpenseTracker-1.4-lite-no-sms.apk` | no | yes |

Everything else is identical: manual entry, categories, people, accounts, charts, reports,
CSV export, backup and restore. The lite build declares no SMS permissions, registers no
SMS receiver, and hides the SMS tab, so it offers nothing that cannot work.

Both share the applicationId `com.expensetracker` and the same signing key, so either
installs straight over the other and keeps existing data. Install the lite build now, and
if you later enable USB debugging you can install the full build on top without losing
anything.

Installing the full edition over USB:

```bash
adb install -r "dist\ExpenseTracker-1.4-full-sms.apk"
```

Building either edition:

```bash
gradlew assembleLiteRelease assembleFullRelease
```


### Why SMS permissions are declared up front

Android requires every permission to be listed in `AndroidManifest.xml`; there is no way
to request one at runtime that was not declared at build time. The app already asks the
user for SMS access at runtime — nothing is granted by installing it, and the prompt only
appears when the SMS tab is opened and the user taps **Allow SMS access**. What the
install-time scanner reads is the manifest declaration, which cannot be deferred. That is
why the lite edition exists: it is the only way to not declare the permission at all.


## Performance

The first build refreshed everything on every change: one `reload()` recomputed all
15 dashboard queries plus the ledger list, and it ran once **per database write**.
Measured on the emulator against a seeded 20,000-transaction ledger:

| | before | after |
|---|---|---|
| Cold start, data on screen | 9,371 ms | 3,611 ms |
| — of which database work | ~880 ms (all queries) | 788 ms |
| Change month / week / year | full 15-query refresh | 29–69 ms |
| Type "amazon" in search (6 keys) | 6 queries, ~1,270 ms | 2 queries, ~14 ms |
| Accept 2 SMS (4 writes) | 4 full refreshes | 1 refresh |

At a realistic size (1,200 transactions) the dashboard is ready in ~200 ms and period
navigation costs 3–6 ms of query time. The residual cold-start time is Compose's first
composition on a software-rendered emulator, not data work — it is far smaller on real
hardware.

### What changed

**Database** (`Db.kt`) — schema v2. Added a composite `(type, date)` index, which every
dashboard aggregate filters on, plus indices on the three foreign keys used by the
`GROUP BY` breakdowns. `EXPLAIN QUERY PLAN` confirms the aggregates now run
`SEARCH tx USING INDEX idx_tx_type_date` instead of scanning. Write-ahead logging is on,
so the dashboard keeps reading while an SMS import writes.

**Queries** (`Repo.kt`) — the six-month trend was 12 queries in a loop (one income and one
expense total per month); it is now a single grouped scan, so its cost no longer grows
with the number of months shown. Cursor-backed lists are pre-sized from `Cursor.count`.

**Batching** (`Repo.batch`) — writes made inside a batch emit one change notification at
the end instead of one per row. A restore of 200 transactions used to trigger 200 full
dashboard refreshes; it now triggers one.

**Refresh pipelines** (`AppViewModel`) — the single `reload()` is split into three flows
that each re-run only when their own inputs change: whole-ledger figures on data change,
dashboard aggregates on data or period change, and ledger rows on data, period or filter
change. `collectLatest` cancels a pass already in flight, so a burst of writes settles
into one refresh. The duplicate refresh that fired on every `onStart` is gone.

**Scoped work** — the ledger query is skipped entirely while that tab is off screen, and
search input is debounced by 220 ms so typing runs one query on pause rather than one per
keystroke. The ledger is capped at 1,000 rows, with a visible note when more exist, so
"All time" on a very large ledger cannot stall the list.

**Dates** (`DateRanges.kt`) — `Calendar` and `SimpleDateFormat` instances are now
per-thread and reused rather than built on every call. `startOfDay`, which runs once per
row when bucketing daily spend, answers from a cached day window. This also fixes a real
thread-safety bug: the formatters were shared singletons called from both the IO
dispatcher and the UI thread.

**Compose** — the state holders are marked `@Immutable` so Compose can skip subtrees whose
data has not changed, and the expense/income category split is computed once when lookups
load instead of on every recomposition.

`Perf` (`data/Perf.kt`) logs these timings under the `ExpensePerf` tag in debug builds and
compiles away in release:

```bash
adb logcat -s ExpensePerf
```

### Upgrade safety

The v1 → v2 migration was tested by launching the new build on a database written by the
first release (750 transactions): `user_version` moved 1 → 2, every transaction, category
and person survived, the stale single-column index was replaced, and no data was rewritten.
