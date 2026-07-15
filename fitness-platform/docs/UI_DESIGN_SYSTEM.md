# Momentum UI design system

## Brand

Momentum uses a calm premium palette: deep navy for structure, soft silver/white for surfaces and
text, and muted amber for deliberate emphasis. Saturation stays low and glass-like treatment is
subtle. The primary mark is a self-authored geometric `M` with a restrained upward arrow. The
launcher foreground has a clear small-size silhouette and a separate monochrome path for themed
icons.

The untracked root directory `icon_and_image_ideas/` is reference material only. It is ignored by
Git, is not an Android source set and must never be packaged in the APK or source archive. Detailed
3D feature art may inspire large dashboard cards, empty states or future achievements, but never
bottom navigation, compact controls or the launcher mark.

## Tokens

`android/core/designsystem` owns `MomentumTheme` and its light/dark schemes. Semantic colors are
used by components instead of feature-specific literals. Spacing follows a 4, 8, 12, 16, 24, 32,
48 dp scale. Typography uses Material roles with restrained hierarchy; shapes, elevation and
150/220/300 ms motion durations are centralized. Reduced-motion preference is exposed through a
composition local and motion must remain optional decoration rather than state communication.

## Components

The shared foundation provides the bounded responsive screen container, section heading, premium
card, empty state and loading skeleton. Product screens keep business state and navigation in their
feature layer. Status is communicated by text and semantics as well as color. Controls use at least
48 dp touch targets, resource-backed labels and selected/error semantics.

Compact multi-choice controls use the responsive segmented selector: options wrap without
horizontal clipping, expose selected semantics and use localized labels. Status chips remain
state-only and are not presented as actions. Technical slugs and enum constants never serve as
visible labels.

## Interaction contracts

- Persistence dialogs retain entered values and stay open while an operation is busy or fails.
- Conflict confirmation text describes keep-local, take-server and merge consequences separately;
  the dialog closes only after the repository reports success.
- Workout detail renders loading, not-found and unresolved-exercise states explicitly. Start and
  Repeat remain disabled while an operation is active.
- The dashboard planned-workout hero says `View workout` until real workout execution is in scope.
- Up navigation and root selection use validated route families; blank IDs and IDs containing `/`
  are rejected before route construction.

## Responsive behavior

Compact widths use the four-root bottom navigation and modal sheets. Expanded widths use a branded
navigation rail and bounded content width. Layouts use start/end-safe padding, system insets and
horizontal overflow where 200% text cannot fit safely. Required previews cover compact phone,
large phone, 600 dp, 840 dp/landscape and large-font configurations.

## Accessibility

Visible headings carry heading semantics; navigation exposes selected state; images and controls
have meaningful descriptions; errors are rendered as text and announced by the platform. Plural
resources are mandatory for counts. Raw slugs, enum names and technical recovery codes are not
user-facing when a localized label exists. Light and dark schemes maintain readable contrast.

## Preview and screenshot policy

Previews use conspicuously synthetic state and must never invent product metrics such as XP,
streaks, calories, steps or weekly progress. Screenshots are accepted only from a built app or a
named synthetic preview and must state which source was used. Connected visual acceptance remains
separate from compile-only verification when no suitable API-36 AVD exists. API-37 preview
execution is diagnostic only and cannot close the gate.
