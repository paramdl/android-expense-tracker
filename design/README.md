# Expense Tracker — design extraction

Everything here was pulled from the live source at **v1.5.1**
(`ui/theme/Theme.kt`, `ui/components/Common.kt`, `ui/screens/*.kt`).
No values were invented or eyeballed from screenshots.

| File | What it is |
|---|---|
| `design-kit.html` | The current UI as 6 phone frames (360×800 dp) plus a design-system sheet. Open in a browser, or import to Figma. |
| `design-tokens.json` | Colours (light + dark), type scale, radii, spacing — in **Tokens Studio for Figma** format. |
| `README.md` | This file. |

---

## Which direction Figma works in

Worth being clear about, because it changes the order of operations:

**The Figma connector reads *from* Figma. It cannot push this Android app *into* Figma.**
It is a Dev-Mode bridge — it hands me the frames, layers and variables of a design
that already exists in a Figma file so I can implement it in code.

So connecting Figma first would give me an empty file to read. The working order is:

1. **Extract** the current design into something Figma can import ← *done, this folder*
2. **You import it** into Figma and redesign it there
3. **You share it back** (connector, or a link, or exports)
4. **I apply it** to the Compose code — only after you confirm

---

## Step 2 — getting this into Figma

Three routes. The first is the most reliable.

### Route A — html.to.design plugin (recommended)
A known, reliable route.

1. Figma → **Plugins → html.to.design**
2. Choose the **“HTML code”** (paste) tab, not the URL tab — this is a local file
   with no public URL, and the URL tab needs one.
3. Paste the whole contents of `design-kit.html` and import.

You get real Figma layers: frames, auto-layout, editable text, live colours.

### Route B — Tokens Studio, for the variables
Do this *as well as* Route A, so your redesign edits real variables rather than
loose hex values.

1. Figma → **Plugins → Tokens Studio for Figma**
2. **Settings → Import** → paste `design-tokens.json`
3. It creates three sets: `light`, `dark`, `global`. Apply `light` + `global`
   to start, then use **Export to Figma variables**.

### Route C — screenshots as an underlay
If you would rather trace over the real thing, the emulator captures are the
ground truth for layout. Say the word and I will produce a fresh set of every
screen at 1080×2400 and drop them here.

---

## Step 3 — connecting the Figma connector

The connector is listed on this machine but **not authorised**, and I cannot run
the sign-in from here — this session has no interactive prompt. You need to
authorise it yourself:

- **If it is a claude.ai connector:** claude.ai → **Settings → Connectors** →
  find Figma → **Connect**, and complete the Figma sign-in.
- **If it is a local MCP server:** run `claude mcp` (or `/mcp` inside an
  interactive `claude` terminal) and authorise Figma there.

Note: if it refuses to authorise, do not spend time on it — **Route D below works just as well.**

### Route D — no connector needed
Honestly the lowest-friction option. Once your redesign is done, send me either:

- the Figma **share link** (set to “Anyone with the link can view”), or
- a **PNG/PDF export** of each redesigned screen, plus the updated hex values, or
- just an updated `design-tokens.json` if the change is only colour/type

Any of those is enough for me to implement it accurately. A share link plus one
export per screen is the sweet spot.

---

## What I will need to know before applying

Small things that decide how much of the app changes:

- **Scope** — a re-skin (colours, type, radii, spacing) touches `Theme.kt` and
  `Common.kt` only and is quick and low-risk. Re-laying-out screens means editing
  each screen file, which is a bigger, slower change.
- **Dark mode** — if you only redesign light, I will derive a matching dark
  palette and show it to you before it ships.
- **Which screens** — this kit covers Home, Ledger, Add/Edit, Recurring,
  New recurring and Settings. Reports, SMS, Import and Manage are not drawn yet;
  ask and I will add them.

Nothing gets applied to the app until you say so.
