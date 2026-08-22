# HyperBridge final-pass manual acceptance matrix

Automated JVM tests cover call state, message identity/summary policy, visual geometry,
same-ID updates, migration policy, channel contracts, diagnostic bounds, floating-setup
state, and reconciliation planning. The scenarios below require a compatible physical
HyperOS device and real third-party apps; they are release-gate checks, not claimed as
performed by the desktop build environment.

| # | Scenario | Release acceptance criteria | Status |
|---|---|---|---|
| 1 | Fresh install | Onboarding explains required access and optional floating setup; no root, Shizuku, or ADB required. | Device required |
| 2 | Upgrade | Apps, themes, and preferences remain; full onboarding is not restarted; one optional setup card appears when relevant. | Device required |
| 3 | WhatsApp message | One Island, meaningful image plus app badge, original shade alert retained, no bridge sound/vibration, no summary duplicate. | Device required |
| 4 | WhatsApp multi-chat | Separate logical conversations within configured limits; aggregate summary ignored when children exist. | Device required |
| 5 | Instagram message | Correct Island and badge with no HyperBridge audible alert. | Device required |
| 6 | WhatsApp outgoing call | Calling/Ringing has no timer; same Island begins a stable timer only after connection. | Device required |
| 7 | Incoming call | Avatar and app badge, Answer/Decline, no pre-answer timer, stable post-answer timer. | Device required |
| 8 | Other calling app | Telegram/Instagram/compatible CallStyle follows generic call-state behavior. | Device required |
| 9 | Progress/download | Same-ID silent updates, smooth progress, correct completion, no zombie Island. | Device required |
| 10 | Media | Existing controls and artwork remain functional without update re-expansion. | Device required |
| 11 | Navigation | In-place updates and prompt removal when navigation ends. | Device required |
| 12 | Reboot/unlock | Listener reconnects; current selected source notifications rebuild; cached sessions are not blindly restored. | Device required |
| 13 | Process recreation | Orphan bridge entries are removed before active sources are recovered; no occupied zombie slots. | Device required |
| 14 | Notification access revoked | Setup Health and Diagnostics show the loss; service does not crash. | Device required |
| 15 | Featured permission unavailable | Honest unsupported/not-granted state and public settings recovery; no crash loop. | Device required |
| 16 | Rapid messages | Latest state settles, jobs remain bounded, summaries do not produce duplicate Islands. | Device required |
| 17 | Icon consistency | Transparent, full-bleed, tall, wide, adaptive, bitmap, vector, themed, and monochrome actions keep aspect ratio and similar visible size. | Device required |
| 18 | Floating setup | Per-app public notification settings opens with fallback; confirmation is labeled user-confirmed; source shade entry remains after native floating is disabled. | Device required |

For upgrade testing, include one install from upstream, one Pass 1 build, and one Pass 2
build. Inspect Android notification-channel settings before and after upgrading: the four
stable channel IDs must remain, no new duplicate channels should appear, and HyperBridge
channels should retain their existing user choices while their shipped defaults remain
silent, non-vibrating, and badge-free.
