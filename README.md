# DeAlgofy

An Android app that puts a deliberate pause between you and the apps that
were engineered to capture your attention.

## The Idea

Algorithmic apps — Instagram, TikTok, X, YouTube — aren't open because you
chose to open them. Most of the time, your thumb finds the icon before your
brain does. By the time you notice, you've already scrolled for ten minutes.

DeAlgofy interrupts that loop. The moment you tap a "guarded" app, an
accessibility service slides a full-screen intercept on top of it. The
intercept gives you three options that don't exist in the original muscle
memory:

1. Do something **productive** instead (open a different app you've chosen).
2. Start a **focus session** (DND on, brightness down, alarm scheduled).
3. **Lock the screen** and walk away.

The path back to the app you originally opened is still there — but it's
small, dim, and it appears last. You can always take it. You just have to
look at the alternatives first.

## How a Single Intercept Works

When you launch a guarded app, the service fires an intercept screen that
reveals itself slowly:

1. *"hey, wait a second!"*
2. *"have you worked towards your goals today?"*
3. Three circles plop in. They drift gently around the screen, bouncing
   off each other and the walls — slow, ambient, like flotsam on water.
   Each circle is a configurable action: a productive app, a focus
   session, or screen lock.
4. Today's stats appear: *"Instagram opened today: 7"*, *"Time spent on
   Instagram today: 47m"*. The numbers are bold so you can't miss them.
5. The home-screen button appears alongside the stats.
6. Last and faint, *"I want to use Instagram right now"* — the deflected
   path. Always available, never the first option offered.

The first ten intercepts use longer pauses between beats; after that the
reveal speeds up because you've internalised the cadence.

If you tap "I want to use [App] right now," the intercept approves a
session for that package and won't fire again until you've left the app
for 30 seconds. No nagging — once you've made the conscious choice, you
get to follow through.

## Features

- **Guarded-app intercept** via `AccessibilityService`. Per-app opt-in;
  configurable from the home screen.
- **Three configurable goal circles**, each one of:
  - **Productive app** — launches a chosen app (e.g. Kindle, Anki) and
    closes the intercept.
  - **Focus mode** — opens a duration picker, then enables Do Not
    Disturb, dims the screen, and schedules an alarm to bring you back.
  - **Lock screen** — locks the device immediately.
- **Per-circle counter** under each name:
  - Tap-counted circles show today's tap count.
  - Focus-mode circles show today's *committed* focus minutes (a tap
    that's backed out of the duration picker doesn't count).
- **Today's exposure stats** for the guarded app — open count from the
  app's own intercept history, screen time from `UsageStatsManager`.
- **Approved sessions** — once you choose to enter, you aren't re-prompted
  until 30 seconds after you leave.
- **App usage leaderboard** (right-edge tab on the intercept) — see how
  much time you've sunk into each guarded app today.

## Who It's For

People who already know they spend too much time in algorithmic apps,
have tried "just use it less" and noticed it doesn't work, and want a
small piece of friction between their thumb and the dopamine machine.

Not a willpower app. Not a blocker that punishes you. A pause, a choice,
and three concrete alternatives every time.

## Permissions

DeAlgofy requests:

- **Accessibility service** — to detect when a guarded app comes to the
  foreground and overlay the intercept.
- **Usage access** — to compute screen-time stats per app per day.
- **Notification policy + write settings** — for focus-mode DND and
  brightness control.
- **Schedule exact alarm** — for the focus-mode end alarm.
- **Query all packages** — for the in-app picker that lets you choose
  which apps to guard and which apps the productive-circle launches.
- **Post notifications** — for the focus-mode end notification.

Nothing leaves the device. All counts and events live in a local Room
database (`dealgofy.db`).

## Tech

- Kotlin, single-module Gradle project, minSdk reasonable for modern
  Android.
- Room for persistence (`InterceptEvent`, `CircleTapCount`).
- `AccessibilityService` + `SharedPreferences` for guarded-app config.
- Intercept UI built with Fragments + ViewPager2; circle physics driven
  by a `Choreographer.FrameCallback` running an integrate → resolve
  collisions → write transforms loop at refresh rate.