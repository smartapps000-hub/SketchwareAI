# Sketchware AI design reference: Obsidian Liquid Glass

The owner's design files, saved here on 13 Nov 2026 so every screen follows them. Read this file before changing any
layout, drawable or colour of the IDE. (The apps users *build* keep their own colours: section 4.)

| File | What it is |
|---|---|
| `liquid-glass/Sketchware_AI_Liquid_Glass.pdf` | Mockups: main screen (projects, Restore card, project card actions), design editor (palette, canvas, Run bar), compile log + Diagnostic sheet, Project Configuration, and the token sheet |
| `liquid-glass/Tokens.dc.html` | The token sheet as exported from the design tool: the values live in its inline styles and `<style>` block. Its viewer scripts (React runtime) are not kept here, so open it as text; `EXPORT-README.md` is the export's own note |

## 1. Tokens and where they live in the app

| Token (design) | Value | Android resource |
|---|---|---|
| Window / page | `#0B0B0D` | `@color/ob_bg` (`res/values/colors_ob.xml`) |
| Text / secondary text | `#F5F5F7` / `#A1A1AA` | `ob_text` / `ob_text_2`; styles `Ob.Text`, `Ob.Text.Caption` (`styles_ob.xml`) |
| Primary | `#F4511E` | `ob_orange`; tiles `ob_tile_orange` (`#F4511E → #FF7428`), FAB, toolbar |
| Status bar | `#E64A19` | window status bar of the glass screens |
| Amber (dark) / accent | `#FF7A29`, `#FFB877`, `#FF9A4D` | `ob_amber`, `ob_accent`, `ob_accent_2` |
| Solar gradient | `#FF7428 → #FFB619` | ambient window light (`ob_window_bg`), AI tile |
| Obsidian glass surface | fill `rgba(38,38,44,.72) → rgba(24,24,28,.58)`, blur 22px, rim `#4DFFFFFF` top → `#0DFFFFFF`, shadow `#1A000000`, radius 20–22dp | `ob_glass` (`GlassDrawable`, `drawable-v24`; plain shape below Android 7) |
| Glass pill 16dp | 1px `rgba(255,255,255,.12)`, fill `rgba(255,255,255,.10) → .03`, inner top highlight `.28`, label `#F5F5F7` bold 15 | `ob_pill16` / `ob_pill16_state` |
| Light glass / palette pill 14dp | `#F2FFFFFF → #D9F8F9FA`, 1.2dp `#33F4511E` rim, label `#9A3412` | light theme surfaces (`ThemeMode`) |
| Primary action | white bold label on orange; design file: `#E8501C → #F97316` with an outer glow `0 8px 22px rgba(255,116,40,.5)` | `ob_cta`, `ob_cta_pill` — **deepened to `#D34115 → #C64F14` in v1.9.7.1** (owner's choice, 11 Nov 2026: white label, WCAG AA) |
| Fonts | Sora (titles), Manrope (text), JetBrains Mono (code) | `res/font/sora_bold`, `manrope_*`; code views use the monospace face |
| Corner radii | cards 22dp, pills 16dp, palette pills 14dp, CTA pill 26dp | `GlassDrawable` `swRadius` |

## 2. Rules (from CLAUDE.md sections 1 and 8, kept here so a design change sees them)

- The dark + orange glass look of the main screen is the owner's original theme: don't change it. A visual change with
  more than one route is rendered both ways and put to the owner before it touches every screen.
- Labels on orange stay pure white; the orange is deepened for contrast, never the label darkened (a near-black label
  was rejected on 11 Nov 2026).
- Reuse the existing resources: glass card `ob_glass`, pill `ob_pill16_state`, accent pill `ob_amber_pill`, card
  `ob_accent_box`, CTA `ob_cta_state` / `ob_cta_pill_state`, press animator `sw_press`, toggle rows `ob_toggle_row`.
- Sheets and dialogs are fully opaque; every label fully visible; no button that does nothing.
- The design canvas and block editor are not darkened (previews stay true); project colours are never recoloured.

## 3. Deviations from the files, with the reason

| Where | Deviation | Why |
|---|---|---|
| CTA buttons (`ob_cta*`, `ob_safe_cta*`, `ob_seg_pill_on`) | `#D34115 → #C64F14` instead of `#E8501C → #F97316` | v1.9.7.1, owner: white label must reach WCAG AA (4.6:1) |
| GitHub sign-in and account cards (v2.0.0) | the orange pill without the outer glow (`ob_cta_pill_soft*`), account card on `ob_glass` with a neutral glass tile | owner, 13 Nov 2026: "remove the heavy orange glow" on the login card and buttons, match the drawer's "Sketchware AI / Android 17 ready" card; other screens keep the glow |
| Back up / Restore sheets and the sign-in window (v2.0.1) | glass action cards (`GitHubViews.actionCard`: `ob_pill16_state`, a 40dp `ob_tile` with a line icon, bold Sora title and a grey line, both wrapping to two lines instead of being cut), classic check boxes (`GitHubViews.checkBox`: `ob_orange` when ticked, `ob_text` label), a 3dp `ob_orange` loading line on an `ob_divider` track; new line icons `ob_ic_refresh`, `ob_ic_open_browser`, `ob_ic_phone` (own drawings in the `ob_ic_*` style: 2dp round white strokes) | owner, 13 Nov 2026: "glass cards (vector icon + bold title + 1-line subtitle), no plain text lists", "two-row non-clipped header", "thin animated orange progress bar" |

## 4. Not covered by these files

The apps built with Sketchware AI use Material 3 from the project's own colour (`M3Palette`), not these tokens.
