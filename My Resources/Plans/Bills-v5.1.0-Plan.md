# Finance Tracker v5.1.0 Plan: Bills and credit scores

Continues from `Overhaul-v5-Plan.md` (v5.0.0). Later patch releases are covered by the changelog and
the git history, not by a plan file.

## 5.1.0: Bills and credit scores (2026-11-20)

Schema 12 (`Migrations.M11_12`, kept for good; `MigrationTest` builds a schema 11 file from
`app/schemas/…/11.json`
and lets Room migrate and validate it). Four tables: `bills`, `bill_payments`, `credit_agencies`
(CIBIL seeded),
`credit_scores`.

- **A bill is a standing commitment, a payment is a ledger row.** `Bill`: name, kind (EXPENSE or
  TRANSFER), default
  from/to account and category, expected amount, particulars pattern with `{period}`, schedule,
  ended-on date.
  `BillPayment`: bill, the period covered (start, end), its due date, and the transaction that paid
  it (null = skipped).
  The transaction FK cascades, so deleting the row anywhere unsettles the period; balances and
  statistics see only
  ordinary rows.
- **Schedules** (`data/bills/Bills`): CALENDAR = every N months on the first due date's day (clamped
  to short
  months), optionally covering the month (s) before the due month (rent, maids); DURATION = the
  validity typed at each
  payment runs from the payment date, or from the end of the running validity when paid early
  (recharges). A card
  bill (a transfer into a CREDIT_CARD account) takes the due day from the account and shows the
  statement amount:
  the card's balance wound back to the statement date.
- **Unsettled periods** = every period already due without a payment or skip, plus the next one; an
  ended bill owes
  nothing due after its end date. Status reads "Due in 4 days · for October 2026", with "(n unpaid)"
  when behind.
- **Screens** (`ui/bills`): Bills in the drawer after Transactions, outside the unlock: credit-score
  card, sort chips (Due date, Name, Amount, Last paid; remembered), ended bills folded. Bill detail:
  next due, expected, schedule,
  history; "Record payment" opens the transaction editor filled in (kind fixed, accounts, category,
  particulars for
  the period, expected amount; a duration bill asks the validity) and saves row + payment in one
  write; "Skip";
  "Link a transaction" (rows of the kind from the last six months that pay nothing yet);
  press-and-hold a history row
  to open, unlink or delete it. Ending keeps the history; deleting needs an empty history. Credit
  score screen:
  agencies with readings and a line of them; press-and-hold to edit.
- **Ledger**: a row that pays a bill says so in its long-press menu and in the delete confirmation;
  editing it in
  the ledger is fine (no redirect, unlike investments). The editor shows the bill line read-only on
  such rows.
- **Alerts**: bills due within their own run-up (Settings › Alerts › Bills, default 3 days) or
  overdue join Home's
  Alerts card and open the bill; they stay tappable while the investments are locked.
- Home's Alerts list is capped at about four rows and scrolls with a drawn thumb.
- Not done: Statistics stay as they are; backfill of the existing rent/Mudupu/recharge rows is a
  later script;
  tying a template to a bill (so a payment entered through the template records itself) is a
  possible follow-up.

### Linking and backfill (2026-11-21)

The first "Link a transaction" filed whichever row was picked under the bill's *next* period, so a
backfill of six rows
marched a year into the future. Replaced:

- **Link transactions** (from the bill's "+", which also offers Record payment and Skip): a
  multi-select sheet with a
  search box (preset to the fixed start of the particulars pattern, or the bill's name; empty for a
  card, which then
  shows the last six months of transfers into it). Rows written from the pattern (`{period}` as a
  wildcard) come
  ticked; each row shows the period it will settle: the one its particulars name
  (`Bills.parsePeriodLabel`), else the
  calendar period whose due date is nearest its date (`Bills.nearestPeriod`). Duration bills chain
  the ticked rows
  oldest first (`Bills.assignPeriods`). `BillsRepository.linkAll` writes every link in one
  transaction.
- A link before the bill's first period moves the start of its history back (`Bills.startingFrom`),
  so the form asks
  for the **Next due date**, not when the bill began. Editing it moves the due day (and the anchor
  month of a
  quarterly/yearly bill), never the start (`Bills.firstDueFor`). A card bill has no date field at
  all: it follows the
  card's due day and is named "<Card> bill" unless a name is typed.
- "Covers the month before" became the two-way choice **Each payment is for**: the current period /
  the previous
  period. An **Insert {period}** chip puts the token at the cursor.
- The bill sheet's account dropdowns are grouped under Wallets / Banks / Credit Cards like the
  editor's picker.
- Backfill is therefore done in the app, not by a script: checked on the emulator with the Mudupu
  bill (30 rows back
  to June 2024 linked in one go, next due 1 Dec 2026 for November).
- Follow-up the same day: a backfill of the older "Stipend" rows (which do not match the "Salary"
  pattern) went by
  date and put two rows paid on the same day under one period. Now a month and year written anywhere
  in the
  particulars names the period (`Bills.periodLabelIn`), the date is the last resort, and the sheet
  shows in red any
  period with more than one ticked row or one already paid. "Tick all" / "Untick all" for long
  backfills.
- Later the same day: the "+" reads **Record a payment**, **Skip a period**, **Link transactions**,
  **Unlink
  transactions**. Record opens the editor for the upcoming period with a "Change period" menu (every
  unsettled period,
  then three ahead: `Bills.periodChoices`); the particulars and amount follow the chosen period
  while they are still
  the bill's own. Skip is a sheet of the unsettled periods, the upcoming one ticked, with a note and
  Tick all, for old
  gaps too (`BillsRepository.skipAll`). Unlink is the same shape over the history. A typed search in
  the link sheet
  reaches every expense and transfer row, so the 2017–2021 "Salary"/"Stipend" expense rows can be
  linked; "Paid so
  far" counts months, not rows.
- Smoothness (2026-11-21): the ledger-wide combines of Transactions, Bills and Home's alerts run on
  `Dispatchers.Default` (`flowOn`) so a screen entry no longer blocks the main thread while it
  slides in. The
  Income/Expense/Transfer bar slides in and out with the screen change instead of appearing at once
  (which shrank the
  content area in one jump and read as the screen "collapsing"). The link sheet's red warning line
  is always present
  so ticking a row never shifts the layout. Phone frame stats (debug build, Pixel 8a at 120 Hz): p50
  8 ms, p90 14 ms,
  3.3 % janky; a release build with R8 is the next thing to compare.
- Back gesture (2026-11-21): navigation-compose 2.10 animates the predictive back gesture with its
  own
  `predictivePopEnter/ExitTransition` (Material default: scale to 70 %), separate from the pop
  transitions the back
  arrow uses. Both now slide. The release build type is signed with the debug key (R8 still off) so
  a release
  variant can be installed from Android Studio for a smoothness comparison.
- Schema 13 (2026-11-21): `bills.alert_when_overdue` (`Migrations.M12_13`, ALTER TABLE ADD COLUMN).
  A bill set to
  "Only once overdue" stays out of Home's alerts until its due date has passed, so bills that can
  wait do not crowd
  the card. The form offers it under "Alert on Home"; the bill screen says "Alerted only once
  overdue". The
  migration test now also builds a schema 12 file and opens it on the current app.
- Slides (2026-11-21, later): every destination is now drawn on an opaque background (`page` in
  HomeScreen).
  Most screens paint nothing behind their lists, so the one underneath showed through at a third of
  the width
  while they slid; the sub-screens with their own Scaffold never had the problem, which is why
  Bill → Bills looked
  right. "Loading…" texts replaced by a centred spinner (`LoadingOrMessage`) on every screen that
  waits for data.
- Schema 14 (2026-11-21): `bills.display_order` (`Migrations.M13_14`, seeded with the id). Bills
  list sorts gain
  "Added" (by id) and "Custom" (by display order); under Custom a "Reorder" button in the chip row
  switches the list
  to drag handles (three lines in place of amount and date), with Cancel / Save. Drag is a
  hand-rolled
  `detectDragGestures` over `LazyListState.layoutInfo`, items keyed by bill id. New bills go last.
- Schema 15 (2026-11-21): `bills.alert_when_overdue` renamed `alert_mode` (`Migrations.M14_15`), now
  a three-way
  `BillAlert` (BEFORE_DUE / WHEN_OVERDUE / NEVER, stored 0/1/2). Income bills: kind INCOME allowed (
  "Received
  into" + income category), listed under an "Income" section of the Bills screen with their own
  wording (expected /
  late / not received, "Record the receipt", "Is the income … · period" in the ledger). The link
  sheet's typed
  search lists income rows for an income. Statement and card logic stay transfer-only.
- 2026-11-21, later: "End this bill" asks for the date (past dates allowed, so a card that stopped
  last year ends
  then); the edit sheet shows "History starts (first due date)" so a start moved back by a stray
  link can be moved
  forward again; an empty link search lists every transfer into a bill's fixed "into" account (the
  search still
  starts from the particulars pattern); date pickers use an en-IN locale (`PICKER_LOCALE`) so typed
  dates read
  dd/MM/yyyy and headlines read "21 Nov 2026" (`pickerFormatter`).
- 2026-11-21, later: one ledger row may pay several bills (rent and maintenance paid together); a
  bill still takes
  a row once. The link sheet hides only rows already in this bill's history and marks ones that pay
  another bill.
  A history row's menu has "Change the period" (`BillsRepository.changePeriod`) to refile a payment
  under another
  month. The ledger label reads "Pays the bills A · P and B · P" when a row pays two.
- 2026-11-21, later: validity bills (schema 16). A validity is a count in days, months or years
  (`bills.validity`, `bills.validity_unit`; `Validity.endFrom` ends the day before the same date n
  months on, so
  6 months from 13 Mar ends 12 Sep). A payment's period is a start and an end date: the editor shows
  "covers …"
  with Change, and a history row's "Change the period" opens the same `ValidityPeriodDialog` (start,
  validity
  shortcuts from `Bills.validityChoices` = the usual one plus lengths in the history, end). The
  default stays
  queue-behind. "Re-chain the later payments" (`BillsRepository.rechainAfter`) recomputes every
  later row from its
  own date after one is corrected. No per-bill early-payment switch.
- 2026-11-21, later: the Bills list has a search field (every typed word must appear in the name,
  particulars,
  notes, accounts, category, schedule text or status) and a single-choice filter chip (`BillFilter`:
  All, Overdue,
  Due in 30 days, Expenses, Transfers, Incomes, Card bills, By validity, Ended). "Show ended" became
  the Ended
  filter; ended bills also surface when a search matches them. Reorder always arranges every bill,
  filter or not.
