# Cinematic Home (Mowadi fork)

This fork replaces Home's presentation with an Apple TV-inspired interface: a scenic full-screen background, floating navigation, a large selected-app title, and two independent horizontal shelves. The original `MainActivity` component and package identity are retained. The manifest, install scripts, Home selection, accessibility helpers and device safety rules are unchanged.

## Design and behavior

- **Featured apps:** installed pinned apps in configured dock order, followed by other TV apps. Apps hidden by the existing config are excluded from the TV portion. If there are no featured apps, the shelf falls back to launchable apps.
- **All apps:** both TV and ordinary launchable apps, including apps hidden from Featured. Packages without an exported launcher activity cannot be opened from a launcher and are managed through Android Settings.
- Real app banners and icons load on a bounded background worker. Configured `app_labels`, bundled `app_banners`, `dock_apps`, and `launch_actions` remain supported. Content opens inside each app; no fabricated program catalog or online recommendation feed is added.
- The floating menu opens Home, App library, Inputs, Wi-Fi and Android Settings. Inputs reuses the existing HDMI/AV/tuner menu and vendor fallbacks. Wi-Fi uses the existing vendor-aware settings resolver.
- A native clock respects the system's 12/24-hour preference. Date is localized. RAM and internal data-volume storage usage refresh every five seconds while visible and stop when paused.
- The bundled background is original vector landscape art. Existing local wallpaper configuration and rotation can replace it; no network permission, blur, video, dependency, or service is added.
- White outlines and a small scale change mark focused cards. Left/Right scroll within a shelf and stop at its edges. Up/Down move between shelves and the menu. OK opens the selected app or shortcut. Back returns to Featured. Home keeps its existing activity routing. App selection survives ordinary app transitions and list updates; removed items clamp to the remaining range.
- Setting `cinematic_redesign` to `false` in `launcher/app/src/main/res/values/config.xml` restores the original interface at build time. This fork deliberately enables the redesign by default, as requested. This is a fork restyle, not a proposal to change the upstream defaults.

## Validation

Build locally without installing or connecting to a TV:

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The navigation unit tests cover empty shelves, one-item shelves, both horizontal boundaries, ordinary moves, and selection after removal. The repository suite uses fake TV transports; it does not need a physical TV.

Real TV validation is pending explicit owner permission. No physical TV was accessed as part of this redesign. Before deployment, check all four D-pad directions at each edge, horizontal scrolling beyond the first viewport, OK/Back and app return, app installation/removal refresh, empty library, Wi-Fi and all HDMI inputs, clock/date updates, and foreground/background capacity updates. Confirm appearance on the actual display and test the physical remote. No claim of device validation or performance measurements is made.

The legacy layout controls (dock dimensions, grid columns, glass effects, reorder UI and module-card presentation) apply to the original interface. They do not change the cinematic shelves. Existing theme wallpaper and the app configuration listed above apply to this interface.
