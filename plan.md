Current calling status now:

Feature	Status
1-to-1 audio call	✅ Implemented, needs real-device audio test
1-to-1 video call	✅ Re-enabled, needs real-device video test
ICE server endpoint	✅ Done
STUN fallback	✅ Done
TURN support	⚠️ Code support done, but real TURN credentials not set yet
Background incoming call	⚠️ Partial: process-alive/background notification possible; fully killed-app needs FCM
Group call	✅ Group audio max 4 implemented
Group video	❌ Not implemented
SFU	❌ Not implemented
PC/Tailscale dependency	✅ Removed from calling path
Drive impact	✅ Not touched