# Expense Tracker — Logo & App Icon Brief

**For:** logo designer
**Date:** 27 October 2026
**Product version at time of writing:** 1.5.1 (Android)
**Prepared from:** the live app source — colours, type and the existing icon are extracted values, not estimates.

---

## 1. The product in one paragraph

A private, offline personal-finance app for Android, built for Indian households.
It reads your bank and UPI SMS messages on the phone itself and turns them into
transactions — no account, no login, no server. It handles the money events Indian
families actually run: EMIs with a visible term, chit-fund and RD contributions,
subscription auto-debits, cash and UPI side by side. Everything stays in a local
database; backups are files the user writes wherever they choose.

**Tagline:** *Tracks your spending. Can't track you.*

The tagline is literal, not a boast: the app declares no internet permission and
ships with no analytics, ad or network libraries, so it is technically incapable of
transmitting data. **Privacy is the product, not a feature.** That is the single
most important thing for the logo to carry.

---

## 2. Who it is for

Indian salaried adults, roughly 25–45, who already use a banking app and get SMS
alerts for every debit. They are mildly distrustful of finance apps that ask to read
their messages and then upload them. They are not hobbyists — they want their
numbers right, not gamified.

Secondary audience: anyone who sideloads the app because they refuse to grant SMS
access to a cloud service. A second, SMS-free edition exists for exactly them.

---

## 3. Brand personality

| It should feel | It should not feel |
|---|---|
| Trustworthy, discreet, quietly competent | Loud, playful, cartoonish |
| Grown-up and calm | Startup-ish, neon, crypto |
| Clear and legible before it is clever | Decorative, ornate, illustrative |
| Indian without being folkloric | Novelty-ethnic, rangoli, temple motifs |
| Precise, like a well-kept ledger | Aggressive "wealth", "hustle", gold bars |

Three words to hold onto: **private, exact, calm.**

Worth knowing: this is not a bank and not an investment app. It records what already
happened. The tone is a notebook, not a trading floor.

---

## 4. What exists today

The current icon is a developer placeholder, drawn in code rather than designed.
It is included so you can see the starting point and the colour temperature —
**please treat it as replaceable in full, not as a layout to refine.**

- A square filled with a diagonal linear gradient, `#12805C` (top-left) to
  `#0B5D6E` (bottom-right)
- A white rupee mark centred in the upper half
- Three ascending white bars below it, suggesting an upward trend

Honest notes on its weaknesses, so they are not repeated:

- The rupee glyph and the bars compete; at 48 px the bars read as noise
- A full-bleed gradient square is generic and already crowded in this category
- The "ascending bars" metaphor implies growth and investing, which is the wrong
  promise — this app measures outgoings
- Nothing in it says private or local, which is the whole proposition

---

## 5. Colour

These are the app's real interface colours. The logo does not have to use them, but
it has to sit beside them without clashing, and the primary is the established
brand colour.

**Light theme**

| Role | Hex | Notes |
|---|---|---|
| Primary | `#12805C` | Deep teal-green. The brand colour. Money in, primary actions. |
| Primary container | `#C9F2E1` | Pale mint |
| Negative / expense | `#C2553F` | Warm clay. All expense amounts. |
| Background & surface | `#FBFAF8` | Warm off-white — deliberately never pure white |
| Surface variant | `#EDEFEB` | Card fills |
| Outline variant | `#DCDFD9` | Every card border |
| Text | `#16191C` | Near-black |
| Muted text | `#474A46` | Captions and meta |

**Dark theme** (the app ships both, so the mark must survive both)

| Role | Hex |
|---|---|
| Primary | `#6FD3AC` |
| Negative / expense | `#FF9E85` |
| Background & surface | `#101312` |
| Text | `#E2E3DF` |

The palette is a muted teal-green against warm off-white, with clay for anything
negative. No blue, no purple, no gold anywhere in the product.

---

## 6. Typography

The interface uses **Roboto** throughout (Android's default) — Regular, Medium and
SemiBold only. Section titles and amounts are SemiBold 16; the balance headline is
Medium 34 with -0.5 letter-spacing; field labels are Medium 11, all-caps, +0.4
tracking.

You are free to choose a different typeface for a wordmark — Roboto is a system
default, not a brand decision. If you do, something with a clear, humanist,
slightly condensed character would suit; please avoid geometric-tech
(Montserrat / Poppins territory) and anything with personality in the numerals.

---

## 7. What we need the logo to do

In priority order:

1. **Read at 48 px.** This is a launcher icon first and everything else second.
   If it only works at 512 px it has failed.
2. **Say private or local, somehow.** The strongest available idea and the one
   nobody in this category owns.
3. **Say money or record-keeping**, without defaulting to a rupee sign if there is a
   better route.
4. **Survive the monochrome treatment.** Android 13+ renders a single-colour
   silhouette of the icon on themed home screens. If the concept depends on colour
   or gradient to be recognisable, it breaks there.
5. **Work in one colour, at one size, with no gradient.** Gradients are permitted in
   the final art but the idea underneath must not need one.

---

## 8. Starting territories (prompts, not instructions)

Ignore any of these if you have something better — they exist to show the kind of
thinking that fits, and your own direction is welcome.

- **The closed thing.** A ledger, envelope, pouch or vault that is clearly *shut*.
  Privacy expressed as containment rather than as a padlock cliché.
- **The device boundary.** Something that reads as "this stays here" — a mark held
  inside a rounded square that echoes a phone, data with no arrow leaving it.
- **The ledger line.** The app's visual signature in use is the 1 px `#DCDFD9` card
  border and the ruled row. A mark built from ruled lines or a column edge could tie
  the icon to the interface in a way a glyph never would.
- **A quieter rupee.** If the rupee stays, it should be the whole mark rather than one
  element among several — constructed, confident, not typed.

Explicitly off the table: padlocks, shields, piggy banks, wallets with notes fanning
out, coin stacks, pie charts, upward arrows, dollar signs, anything AI-looking.

---

## 9. Deliverables

### 9.1 Android adaptive icon — the critical one

Android composes the launcher icon from separate layers and then masks them to a
shape the user's phone chooses (circle, squircle, rounded square, teardrop). Getting
the geometry wrong is the usual failure, so:

- **Canvas: 108 × 108 dp per layer**, every layer the same full size
- **Only the central 72 × 72 dp is ever visible** after masking
- **The guaranteed-safe area is a central circle 66 dp across** — the mark must sit
  inside it
- **The outer 18 dp on each edge is bleed**, consumed by the mask and by the
  parallax wobble some launchers apply. Put background there, never content.

Three layers, as separate files:

| Layer | Requirement |
|---|---|
| **Background** | Full-bleed 108 × 108, **fully opaque**, no transparency anywhere |
| **Foreground** | The mark, transparent everywhere else, inside the 66 dp circle |
| **Monochrome** | Flat silhouette of the mark, alpha only — the system supplies the colour. Must still be recognisable as pure shape. |

Supply these as **SVG** (we convert to Android vector drawables) plus 432 × 432 px
PNG references of each layer.

### 9.2 Google Play store icon

- **512 × 512 px, 32-bit PNG, under 1 MB**
- Full-bleed square artwork. **Do not** round the corners or add a drop shadow —
  Play applies its own mask, and a pre-rounded icon ends up double-rounded.

### 9.3 Notification icon

Notifications are not built yet, but the permission is in place and they are coming
(budget warnings, recurring postings). Android requires a dedicated flat icon for
these — the launcher icon renders badly when reused — so it is worth drawing now
rather than retrofitting.

- **24 × 24 dp vector, white on transparent, flat silhouette, no detail**
- This is the most reductive version of the mark — often just its outer shape.

### 9.4 App shortcut icons (2)

Long-pressing the launcher icon offers two actions, both currently iconless:

- **Add expense** — the quick-entry screen
- **Import message** — paste or share a bank SMS

Needed as **24 × 24 dp vector, single colour**, in the same drawing style as each
other. These are UI glyphs rather than logo variants.

### 9.5 Wordmark and lockups

The app is currently named **"Expense Tracker"**. See the open question in §11 —
please confirm the name before investing time in a wordmark.

Assuming the name holds, we need:

- Horizontal lockup — mark left, name right (store listing header, docs, README)
- Stacked lockup — mark above name (splash or about screen)
- Mark alone
- Each in full colour, single-colour black, and single-colour white (knockout)

### 9.6 Optional but useful

- **Play feature graphic, 1024 × 500 px**, PNG or JPEG, no transparency
- **Home-screen widget preview**, 2 × 2 cells — the widget exists and currently
  previews as a live render
- A short usage sheet: clear space, minimum sizes, what not to do

### 9.7 Source files

Editable vector source (`.ai`, `.svg` or `.fig`) for everything, with live text left
unflattened or supplied alongside an outlined copy, plus the font name if a licensed
face is used.

---

## 10. Two editions — a question for you

The app ships in two flavours:

- **Full** — includes automatic SMS capture
- **Lite** — SMS permissions stripped out entirely, installable anywhere, for people
  who will not grant message access

Both are the same product. We would like your view on whether they should be visually
distinguishable on the home screen when both are installed — a subtle variant, a
different background tone, or deliberately identical. No strong opinion here; your
call is welcome.

---

## 11. Open questions to resolve before you start

1. **Is "Expense Tracker" the final name?** It is descriptive and unregistrable, and
   it makes a distinctive wordmark close to impossible. If a rename is on the table it
   should happen before the logo, not after. Flagging it early so nothing is wasted.
2. **Is a Play Store release planned, or is this sideload only?** It decides whether
   the store assets in §9.2 and §9.6 are needed at all.
3. **Light or dark primary?** We lean toward the mark living on the `#12805C` teal,
   but a light mark on the warm off-white is also open.

---

## 12. Reference material in this folder

| File | What it is |
|---|---|
| `design-kit.html` | The current UI as six phone frames plus a design-system sheet. Open in any browser — the most useful thing to look at for context on where the icon will live. |
| `design-tokens.json` | Every colour, type style, radius and spacing value, in Tokens Studio format |
| `README.md` | How the design was extracted and how to get it into Figma |

Opening `design-kit.html` is worth five minutes before sketching — the icon has to
sit on top of this interface, and the interface is quiet.
