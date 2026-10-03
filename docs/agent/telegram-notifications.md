# Telegram notification shapes

Observed on the connected Xiaomi 17 Ultra (`nezha_tr`, `BP2A.250605.031.A3`) from `dumpsys notification` and HyperPop logs on 2026-10-03, package `org.telegram.messenger`. Playback layout is from that APK's player markers (`AttachAudio`, `tg://openmessage`, `com.tmessages.openplayer`, `MediaStyle`) together with Telegram's `MusicPlayerService` source. No voice-note MediaSession was active on the device during this pass, so the live player row was not dumped.

## Text and missed-call notifications

Per-chat rows are `CATEGORY_MESSAGE` (`msg`) with template `android.app.Notification$MessagingStyle` and compat template `androidx.core.app.NotificationCompat$MessagingStyle`. On API 28+ a 1:1 chat leaves the conversation title unset and puts the dialog name in `EXTRA_TITLE`. The message person name is that same dialog name.

A dialog named `Me` was posted with `EXTRA_TITLE=Me` and `EXTRA_TEXT=Helo` (then `Gelakdk`). HyperPop suppressed the heads-up and did not log an island post. The generic self-label list treated the contact name `Me` as an outgoing reply. That name is the other party when it equals the conversation/title. It is not an echo by itself.

Missed calls that stay in the shade reuse the same MessagingStyle row (`category=msg`), not CallStyle. One captured row had an empty title, empty sender, and text `Missed Call`. Incoming calls use `CallStyle.forIncomingCall`.

The connected private-call foreground notification is neither CallStyle nor `CATEGORY_CALL`. `VoIPService.showNotification` posts an ongoing notification whose English title is `Ongoing Telegram call` (`VoipOutgoingCall`), whose text is the contact name, and whose action is `End call` (`VoipEndCall`). There is no chronometer. HyperPop treats that pair — ongoing flag, an end-call action, and an ongoing-call title — as a call, and still does not treat the first sight of it as an answered-call timer. An End action without that title stays a normal notification. The island hang-up control is the icon button: the source title `End call` is omitted once a glyph exists, and a missing action icon uses HyperPop's hang-up glyph.

The group summary is notification id `1`, group `messages`, `FLAG_GROUP_SUMMARY`, `InboxStyle`, title `Telegram`, text like `N new messages from M chats`. It is an aggregate. Do not island it just because the package is Telegram.

## Media-backed voice notes

`MusicPlayerService` posts one MediaStyle / `CATEGORY_TRANSPORT` notification for music, voice notes, and round videos.

Voice note:

- title = sender name
- text = `AttachAudio` (English resources: `Voice message`)
- subtext empty
- one play/pause action
- no skip, shuffle, or repeat actions

Music:

- title = track title
- text = artist
- subtext = album when present
- skip / shuffle / repeat actions

Round video uses the same player shell with text `AttachRound` (English: `Video message`). That stays ordinary media.

Do not treat a short duration, a MediaStyle template alone, or the package name as a voice note. Missing or unrecognized label text stays media.

HyperPop classifies a matching row as `VOICE_MESSAGE`, so it uses the existing voice translator, source-focus shade card, and `miui.pkg.name` handoff. Ordinary media stays `MEDIA` and does not take that path. App-exit Better Animations still follows native target geometry; the voice path matters because only source-focus voice islands are tagged with the source package the exit animation matches.
