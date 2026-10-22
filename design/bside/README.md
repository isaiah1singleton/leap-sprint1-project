# Bside Trading — identity direction

[Editable Figma board](https://www.figma.com/design/sqFnjVARmNCox2Eu3jfCMr?node-id=2-27)

## Recommendation

**Sidecut**: two rounded B bowls divided by a narrow diagonal seam. The B is the first read; its twin counters suggest the 8 behind the name. Lowercase **bside**, set in Manrope SemiBold, gives the seam a softer counterpart. Use the TRADING descriptor on larger lockups and omit it if it becomes illegible.

The board includes three original concepts: Sidecut (recommended), Stack (two separated bowls), and Loop (one open stroke). All were constructed as editable vectors independently of the Noun Project assets. Figma contains five reusable components including an optical 16 px mark, seven colour variables matching the frontend, and a shared wordmark text style.

## Frontend findings

Reviewed frontend/src/styles.css, the shell, sign-in, overview, markets and transaction screens, and trading data.

The existing warm off-white ground, charcoal ink, white cards, subtle borders, 12 px card corners and pill actions are strong foundations. Keep those and create clearer hierarchy with space and type.

The shell currently displays "Inside Tr8ders" beside an empty outlined square. That is the natural replacement point for the selected identity. This delivery adds design assets and recommendations; it does not change application source.

## UI theme

| Role | Value |
| --- | --- |
| Ground | #F4F3F1 |
| Surface | #FFFFFF |
| Ink | #16181A |
| Muted text | #6D6F73 |
| Border | #E4E2DF |
| Gain | #1D7A4C |
| Loss / error | #C4392C |

- Manrope SemiBold for identity, Inter Regular/Medium for interface text; the existing system font is a suitable fallback.
- Type scale: 12 px metadata, 14 px body, 16 px card titles, 24 px page titles, 32–40 px account values.
- Tabular figures and right-aligned numeric columns. Prefer weight and alignment over decoration.
- Spacing: 4/8/12/16/24/32 px; 24 px card padding; 24–32 px between dashboard sections.
- Retain 12 px card corners, 8 px navigation corners, pill actions and 1 px separators.
- Charcoal primary actions and selected navigation. Keep focus visibly outlined.
- Green/red communicate gains/losses or success/errors, accompanied by signs or text.
- Navigation icons at 18–20 px with an 8–10 px label gap. Match apparent weight across the chosen icons.
- Minimal shadows, clean backgrounds, no decorative gradients.

theme-direction.css is a reference only and is not imported into the frontend.

## Noun Project shortlist

These third-party candidates are linked for evaluation; their SVGs are not bundled here.

| Role | Icon | Rationale |
| --- | --- | --- |
| B monogram reference | [Letter B — Juan Pablo Bravo, #101994](https://thenounproject.com/icon/letter-b-101994/) | A literal B starting point for an off-the-shelf direction. |
| B-side reference | [Vinyl — rivda, #2166640](https://thenounproject.com/icon/vinyl-2166640/) | Connects to the B-side name; creates a stronger music association. |
| Dashboard | [Dashboard — Gregor Cresnar, #6450401](https://thenounproject.com/icon/dashboard-6450401/) | From Soft: UI & Essentials. |
| Portfolio | [Portfolio — Gregor Cresnar, #6136711](https://thenounproject.com/icon/portfolio-6136711/) | From Soft: Crypto. |
| Transact | [Exchange — Gregor Cresnar, #6136741](https://thenounproject.com/icon/exchange-6136741/) | Swap/exchange candidate, also from Soft: Crypto. |

Recommendation: use the original Sidecut identity and evaluate the Gregor Cresnar Soft-family icons for navigation. Preview the downloaded SVGs together at 20 px before adopting them. Noun Project's pages show attribution and paid licensing options; use the terms attached to the selected download.

## Assets

- bside-lockup.svg: transparent primary identity; text outlined so installed fonts are unnecessary.
- bside-lockup-reversed.svg: light lockup for dark surfaces.
- bside-mark.svg / bside-mark-reversed.svg: mark for 24 px and larger.
- bside-mark-currentcolor.svg: inline SVG variant for CSS-controlled colour.
- bside-micro.svg / bside-micro-reversed.svg: optical 16 px version with larger counters.
- bside-stack.svg / bside-loop.svg: alternate concepts.
- bside-primary.png: identity preview.
- bside-identity-board.png: reviewed board, alternatives, palette and product specimens.

Allow at least one counter height of clear space. Scale uniformly and preserve the cut. The full lockup's accessible name should be "Bside Trading"; make a mark decorative when equivalent brand text sits beside it.

## Validation

Visually reviewed the Figma composition and revised 16/24/32/48/64 px strip. Exported transparent vector assets and outlined lockup text. Parsed all delivered SVGs as XML. Product tests are not applicable because no application source changed.
