# Power button that sleeps instead of shutting down

Some boxes shut down fully on one press of the remote's power button. A Bluetooth remote cannot start them
again, so someone has to unplug the box. This opt-in setup makes that button put the box to sleep and wake it.
It needs no root.

Tested on one box: Boxput iATV Q5 H313 (Android 10) with a Xiaomi RC remote. Sleep for about a minute and waking
with the same button are confirmed. The transaction numbers below are for Android 10 on that box; read them
back before you trust them on anything else.

## How it works

1. `res/raw/power_sleep.kcm` maps scancodes 116 and 152 to `STB_POWER`, for one remote only, so Android does not
   run its power-off on that button.
2. `HomeKeyService` sees `STB_POWER` with one of those scancodes and calls `GLOBAL_ACTION_LOCK_SCREEN`, which is
   sleep. It acts on the press, because when the button wakes the box Android swallows the press and delivers
   only the release. Acting on the release would put the box straight back to sleep.

On the Q5 that was not enough. A native vendor program, `HotkeyAm`, reads the raw key and starts
`com.qstar.powerui`, whatever the layout says. With that app enabled, the box slept and then shut down 15 seconds
later. With it disabled, the shutdown stopped. Which component asks for the shutdown was not traced further.

## Set up

`HomeKeyService` must be on. `<vendor>` and `<product>` are the remote's IDs in decimal, from `dumpsys input`
(`Identifier: vendor=0x2717, product=0x32b9` is 10007 and 12985). The `q5` argument is only a label: Android
stores the choice by vendor and product.

```
service call input 15 i32 1 s16 q5 i32 <vendor> i32 <product> s16 'com.gathin.tvhome/com.gathin.tvhome.PowerKeyLayouts/power_sleep'
service call input 14 i32 1 s16 q5 i32 <vendor> i32 <product>
pm disable-user --user 0 com.qstar.powerui
```

The second line reads the choice back: it should print the layout name. Disable `powerui` only on a box that has
it and only if the shutdown still happens. The setting survives a power cut.

## What it costs

- The long-press menu (Sleep, Shutdown, Restart) is gone from this remote. To turn the box off fully, unplug it.
- Any other remote still has the firmware's power-off, unless it has the same vendor and product IDs.
- A factory reset removes all of it.
- Not yet checked: whether the layout stays chosen after Glass TV is updated. Read it back with the line above.

## Undo

```
pm enable com.qstar.powerui
service call input 18 i32 1 s16 q5 i32 <vendor> i32 <product> s16 'com.gathin.tvhome/com.gathin.tvhome.PowerKeyLayouts/power_sleep'
```

The second line has not been run yet. Read the layout back afterwards to confirm it is gone.
