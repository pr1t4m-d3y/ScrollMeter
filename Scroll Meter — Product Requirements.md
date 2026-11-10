# Scroll Meter — Product Requirements

Oct 5, 2026 · @Rika

## Overview

Scroll Meter is a private Android app that shows one person how far they scroll on Facebook and Instagram, measured in strokes (swipes) and real-world metres.

**The problem.** The user (Dad) spends long stretches scrolling the Facebook feed, Reels and Instagram. Screen-time numbers like "3 h 20 m" are easy to ignore. A physical distance ("you scrolled 2.6 km today") is concrete and harder to shrug off.

**The product.** Two parts:

1. **Floating HUD.** While Facebook or Instagram is open, two small green seven-segment counters appear in the top-left and top-right corners, styled like Android's "Show refresh rate" overlay. Left = strokes, right = metres. They update live, never block a tap, and disappear when he leaves the app.
2. **Dashboard app.** A dark, card-based app in the style of Samsung Digital Wellbeing. It shows today's distance, a weekly bar chart, a day view with hourly bars and a session list, a weekly report with a peak-hours heatmap, and settings.

**One line:** a reality check measured in metres, not minutes.

Design reference: the Scroll Meter Mockups canvas (screens 1 to 5).

## Goals, non-goals and success measures

The app succeeds if Dad notices his scrolling distance and it nudges his habits, without the app ever breaking his phone or his trust.

**Goals**

- Make scrolling visible in the moment (HUD) and over time (dashboard).
- Measure strokes and distance accurately enough to be believable (within about 10% of a manual check).
- Keep running reliably on a Realme phone with aggressive battery management.
- Keep all data on the phone, with no internet access at all.
- Use almost no battery and never slow down Facebook or Instagram.

**Non-goals (v1)**

- Blocking apps, time limits or locking the phone.
- Tracking any app other than Facebook, Facebook Lite and Instagram.
- Reading or storing any content he sees (posts, names, messages).
- iOS support (not technically possible).
- Play Store publishing (sideloaded APK only).
- Cloud sync, accounts or sharing.

**Success measures**

| Measure | Target | How we check |
| --- | --- | --- |
| Tracking uptime | Counts every day for 14 days straight | No zero days in the weekly chart while he used Facebook |
| Distance accuracy | Within 10% | Scroll a known number of screens and compare |
| Stroke accuracy | Within 5% | Swipe 50 reels and compare |
| Battery cost | Under 2% per day | Realme battery stats for the app |
| HUD tap-through | 100% of taps reach the app below | Tap camera/search icons under the HUD |
| Behaviour signal | Weekly distance trends down after 4 weeks | Weekly report vs previous weeks |

## Users and context

There are two users: Dad, who sees the counters and the dashboard, and the son, who builds, installs and maintains the app.

| User | Role | Needs |
| --- | --- | --- |
| Dad | Daily user | Glanceable numbers, nothing to configure, never gets in the way of Facebook |
| Son | Builder and admin | One-time setup on the phone, a way to see if tracking stopped, easy updates |

**Device**

- Realme phone running Realme UI (built on ColorOS / Android). Exact model and Android version to be confirmed.
- Apps to track: Facebook (`com.facebook.katana`), Facebook Lite (`com.facebook.lite`), Instagram (`com.instagram.android`).
- Install method: sideloaded APK built in Android Studio and installed over USB or file transfer.

**Usage pattern assumed**

- Several sessions a day, the longest at night (around 9 PM to midnight).
- Mix of feed scrolling and Reels swiping.
- Phone often left on Facebook when locked.

## Feature scope

v1 ships the tracker, the HUD and the four app screens; fun extras like milestones and taglines come after it runs reliably.

| ID | Feature | Release | Notes |
| --- | --- | --- | --- |
| F1 | Detect Facebook, Facebook Lite, Instagram in the foreground | v1 | Accessibility service |
| F2 | Count strokes (feed swipes and reel swipes) | v1 | Downward only |
| F3 | Measure distance in metres from scroll pixels | v1 | Uses real screen DPI |
| F4 | Group activity into sessions | v1 | Start, end, app, strokes, metres |
| F5 | Floating seven-segment HUD, tap-through | v1 | Top-left strokes, top-right metres |
| F6 | Today screen | v1 | Screen 1 |
| F7 | Day detail screen | v1 | Screen 2 |
| F8 | Weekly report with peak-hours heatmap | v1 | Screen 3 |
| F9 | Setup and settings with permission checklist | v1 | Screen 4 |
| F10 | Tracking-stopped warning | v1 | Card on Today screen |
| F11 | Milestones and taglines ("taller than Qutub Minar") | v1.1 | Copy to be written later |
| F12 | Weekly summary notification | v1.1 | Sunday night |
| F13 | Home-screen widget | Later | Today's distance |
| F14 | Export data as CSV | Later | For the son |

## Screens and UX

The app is four dark screens in a Digital Wellbeing style, plus the floating HUD drawn over Facebook and Instagram.

### Visual style

| Token | Value |
| --- | --- |
| Background | Pure black `#000000` |
| Card | `#17181A`, corner radius 28 to 30 dp |
| Primary text | White `#FFFFFF` |
| Secondary text | `#B4B7BC` |
| Grid lines | `#3A3C40` |
| Facebook colour | Blue `#3D8BFF` |
| Instagram colour | Orange `#FF9F45` |
| Selected pill | `#2A2B2E` (dates), `#24262A` (week tabs) |
| HUD digits | Green `#2BEA2B` |
| Type | Plus Jakarta Sans, 800 for big numbers, 600 for labels |
| Touch targets | At least 44 dp |

Dark mode only in v1. Facebook is always blue and Instagram always orange, on every chart.

### Screen 1: Today (home)

- Header: "Scroll Meter" title, settings icon on the right (opens Screen 4).
- Row: "Today" on the left, "Weekly report" link on the right (opens Screen 3).
- Date strip: 7 days of the current week (Sun to Sat). Today sits in a filled grey circle. Future days are dimmed. Tapping a past date switches the whole screen to that day.
- Distance card:
  - Big number in km with two decimals ("1.79 km"), label "Distance scrolled today".
  - Weekly stacked bar chart: one bar per day, Facebook blue at the bottom, Instagram orange on top. Y-axis labels on the right ("2 km", "4 km"), scale rounds up to the next even km.
  - Day letters under the bars; the selected day has a white circle with black letter.
  - Tapping a bar opens Screen 2 for that day.
  - Legend rows: Facebook and Instagram with their km totals for the selected day.
- Strokes card: big stroke count, "Across N sessions today", then the session list (time range, app dot, app name, strokes, distance). "See full day" opens Screen 2.
- Tracking-stopped card (only when needed): red-tinted card at the top saying counting stopped, with a "Fix" button that opens Screen 4.

### Screen 2: Day detail

- Back arrow, title with weekday and date ("Thu, 1 Oct"), overflow menu.
- Summary card: day distance, "Distance scrolled · N strokes", 24-hour stacked bar chart (one bar per hour, empty hours shown as a thin grey stub), x-axis 0 / 6 / 12 / 18 / 24, y-axis on the right in metres, legend with per-app totals.
- Two stat tiles: Longest session (duration and time range) and Peak hour (hour and metres).
- Sessions card: every session newest last, each with time range, app, strokes, distance and a thin 24-hour timeline strip marking where the session falls in the day.

### Screen 3: Weekly report

- Back arrow, "Weekly report", overflow menu.
- Date range line ("27 Sep – 3 Oct (Week 40)").
- Week tabs: the last 4 ISO weeks (W37 to W40); selected week in a filled pill.
- Distance by week card: daily average in km, bar chart Sun to Sat with alternating dark column bands, dashed average line with an "Avg." tag, tooltip pill above the selected bar showing date and km, dashed guide line down to the bar. Tapping a bar moves the tooltip; an "Open \<date>" button opens Screen 2.
- Peak scrolling times card: busiest hour as the headline, 7 × 24 heatmap (rows Sun to Sat, columns 0 to 23 h), 5 shades by metres per hour (0, 0–25, 25–50, 50–100, 100+), "Peak" tag and dashed line on the busiest hour.
- Compared with last week card: one sentence plus two horizontal bars (this week vs last week).
- Most scrolled apps: two tiles with app colour, km and strokes for the week.

### Screen 4: Setup and settings

- Permissions card with progress ("3 of 5 done"). Each row: name, one-line reason, green tick when granted or a blue "Allow" button that deep-links to the right system page.
- Apps to watch: switches for Facebook, Facebook Lite, Instagram.
- Floating counter: switch for "Show counters while scrolling", segmented control "Today's total" / "This session".
- Privacy card: "No internet access. Everything stays on this phone."
- First launch opens this screen automatically until all permissions are granted.

### Screen 5: Floating HUD

- Two counters drawn over Facebook / Instagram, just below the status bar: strokes top-left, metres top-right ("12" and "80m").
- Digits are drawn as seven-segment blocks (rectangles), the same technique as Android's "Show refresh rate" overlay; "m" uses the segment glyph that looks like "n".
- Default digit height 26 dp, colour green `#2BEA2B`, no background box. Optional thin black outline for bright videos.
- Values: strokes as an integer; metres as an integer by default, optional one decimal.
- Completely ignores touches: taps pass through to whatever is underneath.
- Appears within 300 ms of the app opening; disappears within 300 ms of leaving it, locking the phone or opening the recents screen.
- Respects the display cutout: never drawn under the camera hole.

## Functional requirements

The tracker listens for scroll events only inside the watched apps, converts downward pixels into strokes and metres, and groups them into sessions.

### FR1 App detection

- FR1.1 Watch `TYPE_WINDOW_STATE_CHANGED` events and track the foreground package.
- FR1.2 Tracking is active only when the foreground package is a watched app that is switched on in settings.
- FR1.3 Ignore events from the keyboard, system UI and our own app (a keyboard popping up must not end a session).
- FR1.4 Screen off (`ACTION_SCREEN_OFF`) ends tracking immediately.

### FR2 Scroll capture

- FR2.1 Listen to `TYPE_VIEW_SCROLLED` events from watched packages only (set `packageNames` on the service).
- FR2.2 Use `scrollDeltaY` (Android 9+) as the primary signal. Positive values = scrolling down the feed.
- FR2.3 Fallback when `scrollDeltaY` is missing or zero: use the change in `scrollY` / `fromIndex` between events for the same view.
- FR2.4 Reels fallback: when the source view is a vertical pager (each page = full screen) and the page index increases, count one stroke and add one screen height of pixels.
- FR2.5 Count only downward movement. Upward scrolling is ignored for both strokes and distance.
- FR2.6 Ignore horizontal scrolling (stories carousels, image galleries).

### FR3 Strokes

- FR3.1 A stroke = one continuous burst of downward scroll events.
- FR3.2 A new stroke starts when the gap since the last downward event is more than 300 ms.
- FR3.3 Each reel page change = exactly one stroke.
- FR3.4 Tiny movements under 24 dp in a burst do not count as a stroke (accidental touches).

### FR4 Distance

- FR4.1 Convert pixels to metres with the physical vertical density of the screen.
- FR4.2 Use `DisplayMetrics.ydpi`; if it looks wrong (outside 120 to 700), fall back to `densityDpi`.
- FR4.3 Formula below. Example: at 400 dpi, one 2,400 px screen = 6 inches = 0.152 m.

```latex
\text{metres} = \frac{\text{pixels}}{\text{ydpi}} \times 0.0254
```

### FR5 Sessions

- FR5.1 A session starts on the first counted scroll in a watched app.
- FR5.2 A session ends when the user leaves the app, the screen turns off, or there is no scroll for 5 minutes.
- FR5.3 Coming back to the same app within 60 seconds continues the same session.
- FR5.4 Sessions shorter than 3 strokes are kept in totals but hidden from session lists.
- FR5.5 A session crossing midnight is split at 00:00 into two sessions, one per day.

### FR6 Aggregation

- FR6.1 Hourly buckets per app: strokes and metres.
- FR6.2 Day, week and peak-hour figures are computed from the hourly buckets.
- FR6.3 Weeks run Sunday to Saturday to match the date strip; the week label (W40) is the ISO week number of that week's Monday.
- FR6.4 Daily average = week total / days with data so far (a partly finished week is not diluted by future days).

### FR7 HUD behaviour

- FR7.1 Show the HUD when tracking becomes active and the overlay switch is on.
- FR7.2 Update at most every 100 ms (smooth but cheap).
- FR7.3 Mode "Today's total" shows today's numbers for all watched apps; "This session" resets at each new session.
- FR7.4 Metres show as an integer until 9,999, then as km with one decimal ("12.4" with no unit change in v1 — to confirm).
- FR7.5 Window flags make it untouchable and unfocusable so it never steals taps or the keyboard.

### FR8 Health check

- FR8.1 Every time the app opens, check that the accessibility service and overlay permission are on.
- FR8.2 If a watched app was used today (via UsageStatsManager, optional) but no scrolls were recorded, show the tracking-stopped card.

## Technical architecture

A native Kotlin app: one accessibility service feeds one in-memory tracker, which drives the HUD live and saves to a local database the screens read from.

&#91;embedded content: Scroll Meter architecture · one service, one tracker, two outputs\]

The tracker is the only place that turns events into numbers, so the HUD and the dashboard always agree.

**Stack**

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin | Native APIs needed for accessibility and overlays |
| Min / target SDK | 28 (Android 9) / 35 | `scrollDeltaY` exists from Android 9 |
| UI | Jetpack Compose, Material 3, dark theme | Matches the card-based mockups |
| Charts | Hand-drawn with Compose `Canvas` | Exact look from the mockups, no library |
| Database | Room | Simple local SQL with Flow queries |
| Settings | DataStore (Preferences) | Small key-value settings |
| Async | Kotlin coroutines + Flow | Background writes, live UI |
| Dependency injection | None (manual) | Small app |

**Key classes**

- `ScrollAccessibilityService` — receives events, filters packages, forwards deltas to the tracker, owns the HUD window.
- `ScrollTracker` — stroke detection, pixel-to-metre conversion, session state, 10-second flush.
- `HudController` — adds and removes the overlay window, positions counters under the status bar and cutout.
- `SevenSegmentView` — custom `View` that draws each character as rectangles with `Canvas.drawRect`. Digit width = 0.6 × height, segment thickness = 0.17 × height. Segment map: 0 = abcdef, 1 = bc, 2 = abged, 3 = abgcd, 4 = fgbc, 5 = afgcd, 6 = afgedc, 7 = abc, 8 = all, 9 = abcdfg, m = ceg.
- `ScrollRepository` — queries for days, weeks and heatmap.
- `TodayViewModel`, `DayViewModel`, `WeeklyViewModel`, `SettingsViewModel`.

**HUD window**

- Added from the accessibility service as `TYPE_ACCESSIBILITY_OVERLAY`. This type needs no "Display over other apps" permission; keep `TYPE_APPLICATION_OVERLAY` as a fallback if it misbehaves on Realme.
- Flags: `FLAG_NOT_TOUCHABLE`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_IN_SCREEN`.
- Two small windows (top-left, top-right), `WRAP_CONTENT`, transparent background.
- Top offset = status bar height from window insets + 6 dp.

**Package layout**

- `service/` service, tracker, HUD
- `data/` Room entities, DAO, repository, DataStore
- `ui/today`, `ui/day`, `ui/weekly`, `ui/settings`, `ui/components` (charts, cards)

## Data model and storage

All data lives in one local Room (SQLite) database with three tables; raw scroll events are never stored.

**`session`** — one row per session

| Column | Type | Notes |
| --- | --- | --- |
| id | Long, primary key | Auto |
| app | String | `facebook`, `facebook_lite`, `instagram` |
| startedAt | Long (epoch ms) |  |
| endedAt | Long (epoch ms) | Null while open |
| strokes | Int |  |
| pixels | Long | Raw downward pixels |
| metres | Double | Pixels converted at write time |
| day | String `YYYY-MM-DD` | Local date, for fast queries |

**`hourly_bucket`** — one row per app per hour

| Column | Type | Notes |
| --- | --- | --- |
| day | String `YYYY-MM-DD` | Part of primary key |
| hour | Int 0–23 | Part of primary key |
| app | String | Part of primary key |
| strokes | Int |  |
| metres | Double |  |

**`settings`** — stored in DataStore, not Room

| Key | Default |
| --- | --- |
| watch\_facebook / watch\_facebook\_lite / watch\_instagram | true |
| overlay\_enabled | true |
| overlay\_mode | `today` |
| hud\_digit\_height\_dp | 26 |
| hud\_colour | `#2BEA2B` |
| hud\_decimals | false |
| hud\_outline | false |

**Write strategy**

- Counters live in memory in the service; flushed to the database every 10 seconds and on session end.
- At most 10 seconds of data lost if Realme kills the process.
- Retention: keep everything (a year of hourly buckets is under 1 MB). Optional "clear data" in settings later.

## Permissions and Realme setup

The app needs five things on the phone, and the Realme-specific battery settings matter most for reliability.

| Permission / setting | Why | Where on Realme | Detected in app |
| --- | --- | --- | --- |
| Allow restricted settings | Sideloaded apps can't enable accessibility on Android 13+ without it | App info → ⋮ → Allow restricted settings | Indirectly (accessibility toggle works) |
| Accessibility service | Foreground app + scroll events | Settings → Additional settings → Accessibility → Downloaded apps | Yes |
| Display over other apps | Draw the HUD (only if the accessibility overlay window type misbehaves; see Technical architecture) | App info → Display over other apps / Floating windows | Yes (`Settings.canDrawOverlays`) |
| Auto launch | Restart after reboot or kill | App info → Auto launch, or Phone Manager | No (guide only) |
| Battery: allow background / don't optimise | Stop Realme killing the service | App info → Battery usage → Allow background activity | Partly (`isIgnoringBatteryOptimizations`) |

**Not needed:** device admin, internet, location, contacts, storage, notifications access.

**Optional:** Usage access (`PACKAGE_USAGE_STATS`), only for the tracking-stopped health check in FR8.2.

**Manual one-time steps for the son**

1. Install the APK.
2. App info → ⋮ → Allow restricted settings.
3. Open Scroll Meter; follow the Setup screen top to bottom.
4. Lock the app in the recents screen (pull down on its card).
5. Open Facebook, swipe 10 reels, confirm the HUD counts 10.

Menu paths differ between Realme UI versions; confirm each on Dad's phone and update this table.

## Privacy, security, performance and battery

The app stores only numbers, never content, and is built so it physically cannot send anything off the phone.

**Privacy and security**

- No `INTERNET` permission in the manifest, so no network access is possible.
- The accessibility service reads only event type, package name, scroll deltas and view class. It never reads text, `getSource()` contents or the view tree.
- Service config limits events to the three watched packages (`packageNames`) and to `typeWindowStateChanged|typeViewScrolled`.
- `canRetrieveWindowContent` set to false.
- Database is in app-private storage; `allowBackup=false` so it isn't copied to cloud backups.
- No analytics, crash reporting or third-party SDKs.
- Being upfront with Dad about what the app does is part of the plan, not an afterthought.

**Performance and battery**

| Area | Requirement |
| --- | --- |
| Event handling | Under 1 ms per scroll event; no allocations in the hot path |
| HUD redraw | Max 10 per second; redraw only when a value changes |
| Database writes | Batched every 10 s, on a background dispatcher |
| Idle cost | No timers or wake locks while no watched app is open |
| Memory | Service under 30 MB |
| Battery | Under 2% per day in Realme battery stats |
| App launch | Today screen visible in under 1 s |

## Edge cases

Each case below has a defined behaviour; the test cases in the next section check them.

| # | Situation | Expected behaviour |
| --- | --- | --- |
| E1 | Phone locked while Facebook is open | Session ends, HUD hidden; resumes as a new session on unlock if Facebook is still on top |
| E2 | Quick switch Facebook → WhatsApp → Facebook (under 60 s) | HUD hides on WhatsApp; same session continues |
| E3 | Notification shade pulled down over Facebook | HUD stays; no scrolls counted from the shade |
| E4 | Keyboard open while commenting | Session continues; HUD stays at top, keyboard unaffected |
| E5 | Facebook in-app browser (link opened inside Facebook) | Counts as Facebook if the package is still Facebook |
| E6 | Video rotated to landscape | HUD moves to the new top corners; scrolling in landscape still counted |
| E7 | Split screen or picture-in-picture | Count only when a watched app is the focused window; HUD hidden in PiP |
| E8 | Scrolling up to reread | Not counted |
| E9 | Horizontal swipes (stories, carousels) | Not counted |
| E10 | Stories (taps) | Not counted in v1 |
| E11 | Session crossing midnight | Split at 00:00 into two days |
| E12 | Phone reboot | Service restarts automatically (accessibility services rebind); data before reboot kept |
| E13 | Realme kills the app | Up to 10 s of data lost; health check flags it if counting stays stopped |
| E14 | Facebook / Instagram app update changes views | Scroll events may change; debug log lets us spot it fast |
| E15 | Facebook Lite sends no `scrollDeltaY` | Fallback FR2.3 / FR2.4; if still nothing, show "Facebook Lite not supported" in settings |
| E16 | Display size or font size changed | Distance unaffected (uses physical dpi) |
| E17 | Time zone change or manual clock change | Use local date at the time of the event; never rewrite past days |
| E18 | Very long session (2+ hours) | No drift; counters keep updating |
| E19 | Overlay permission revoked while running | HUD stops silently, tracking continues, Setup shows the missing permission |
| E20 | Accessibility turned off | Tracking stops; tracking-stopped card on next app open |
| E21 | No data yet (first day) | Empty-state charts with "Start scrolling Facebook to see your first numbers" |
| E22 | Our own app or the HUD itself scrolling | Never counted |

## Test cases

Testing has three layers: unit tests for the maths, an emulator test with a fake scrolling app, and a manual checklist on Dad's Realme phone.

### Unit tests (JVM, no device)

| ID | Test | Expected |
| --- | --- | --- |
| U1 | 2,400 px at 400 dpi | 0.1524 m |
| U2 | Bursts 100 ms apart | 1 stroke |
| U3 | Two bursts 400 ms apart | 2 strokes |
| U4 | Negative deltas only | 0 strokes, 0 m |
| U5 | Burst under 24 dp | 0 strokes |
| U6 | Leave app, return after 45 s | Same session |
| U7 | Leave app, return after 90 s | New session |
| U8 | No scroll for 5 min | Session ends at last scroll time |
| U9 | Session 23:50 to 00:20 | Two sessions, split at 00:00, metres split by time of events |
| U10 | Hourly bucket sums | Day total = sum of 24 buckets |
| U11 | Week average mid-week (3 days of data) | Total / 3 |
| U12 | Seven-segment glyph map | Each of 0–9 and "m" lights the right segments |
| U13 | HUD number format | 80 → "80m", 9,999 → "9999m", 12,400 → "12.4" |

### Instrumented tests (emulator)

| ID | Test | Expected |
| --- | --- | --- |
| I1 | Test app with a scrolling list, package added to the watch list in debug | Strokes and metres recorded |
| I2 | Test app with a vertical ViewPager2 | One stroke per page |
| I3 | Switch to another app | HUD removed within 300 ms |
| I4 | Turn screen off | Session closed in database |
| I5 | Kill and restart process | Data up to the last flush still present |
| I6 | Rotate device | HUD re-laid out in corners |
| I7 | Today, Day, Weekly screens with seeded data | Correct numbers and bar heights |

### Manual checklist on the Realme phone

- [ ] All five permissions granted; Setup shows 5 of 5
- [ ] Facebook feed: scroll 20 times → HUD shows +20 strokes (±1)
- [ ] Facebook Reels: swipe 10 reels → +10 strokes
- [ ] Instagram Reels: swipe 10 reels → +10 strokes
- [ ] Facebook Lite: scroll feed → counts, or setting shows "not supported"
- [ ] Distance check: scroll exactly 10 full screens → metres within 10% of 10 × screen height
- [ ] Tap camera, search, profile icons under the HUD → they open
- [ ] Comment with keyboard open → keyboard works, HUD not over typing area
- [ ] Pull notification shade → nothing counted
- [ ] Lock phone on Facebook → HUD gone; Day screen shows the session ended
- [ ] WhatsApp → HUD not shown
- [ ] Landscape video → HUD in the corners, readable
- [ ] Bright white video → digits readable (try outline setting)
- [ ] Reboot phone → counting works without opening the app
- [ ] Leave phone 24 h → still counting next evening
- [ ] Battery stats after 2 days → under 2% per day
- [ ] Session across midnight → split correctly on both days
- [ ] Turn off accessibility → tracking-stopped card appears
- [ ] Weekly report after 7 days → bars, average and heatmap look right
- [ ] Day detail → session times match when he actually scrolled

## Build plan and milestones

Build the riskiest part first: prove scroll events arrive from Facebook on the Realme phone before any UI work.

1. **M0 — Spike (1–2 days).** Empty Kotlin project, accessibility service that logs package + `scrollDeltaY` to Logcat. Test on Dad's phone with Facebook, Facebook Lite, Instagram, feed and Reels. Gate: events arrive for each app, or a fallback is chosen.
2. **M1 — Tracker core (2–3 days).** Stroke and distance logic, sessions, Room database, unit tests U1–U11.
3. **M2 — HUD (2 days).** Seven-segment view, overlay window, show/hide rules, tap-through, cutout handling. Gate: manual HUD checks pass.
4. **M3 — Setup screen (1–2 days).** Permission checks and deep links, app toggles, overlay settings.
5. **M4 — Today and Day screens (3 days).** Compose UI, charts, session lists, date strip.
6. **M5 — Weekly report (2–3 days).** Week tabs, average line, tooltip, heatmap, comparison, app tiles.
7. **M6 — Hardening (3–7 days on the phone).** Full manual checklist, battery check, two weeks of real use, fixes.
8. **M7 — v1.1.** Milestones, taglines, weekly summary notification.

Rough total for v1: about 3 weeks of evenings.

## Open questions and future ideas

**Open questions**

- [ ] Which Realme model and Android version does Dad have?
- [ ] Does he use regular Facebook, Facebook Lite, or both?
- [ ] HUD default: today's total or this session?
- [ ] Above 9,999 m, should the HUD switch to km ("12.4") or keep metres?
- [ ] Will Dad know about the app from day one, or is it a surprise?
- [ ] App name and icon: keep "Scroll Meter"?

**Future ideas**

- Milestone pops on the HUD (landmark heights, e.g. Qutub Minar 73 m, Eiffel Tower 330 m).
- Taglines in the weekly report.
- Weekly summary notification on Sunday night.
- Home-screen widget with today's distance.
- Light theme.
- More apps (YouTube Shorts) if he moves there.
