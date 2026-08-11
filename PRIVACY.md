# Privacy Policy

**Last Updated: August 2025**

## Our Commitment to Privacy

PennyWise AI is built with privacy as the core principle. We believe your financial data should remain yours alone.

## 100% On-Device Processing

**All data processing happens locally on your device.** We use MediaPipe's on-device LLM (Qwen 2.5) for AI features, ensuring:

- ✅ **No cloud servers** - Your data never leaves your phone
- ✅ **No data collection** - We don't collect, store, or transmit any user data
- ✅ **No tracking** - No analytics, no telemetry, no user tracking
- ✅ **No ads** - No advertising networks or tracking pixels
- ✅ **Offline AI** - Once downloaded, AI works completely offline

## Data Storage

### What We Store (Locally Only)
- Transaction details extracted from SMS (amount, merchant, date, category)
- Your custom categories and notes
- App preferences and settings

### Where It's Stored
- All data is stored in a local SQLite database on your device
- Database is protected by Android's app sandboxing
- Data is only accessible to PennyWise AI app

### Data Deletion
- Uninstalling the app completely removes all data
- You can delete individual transactions at any time
- Export your data before uninstalling if you want to keep records

## Permissions

### SMS Permission (Read-Only)
- **Purpose**: To read bank transaction SMS messages
- **Scope**: Read-only access, we cannot send or modify messages
- **Processing**: SMS parsing happens entirely on-device
- **Storage**: Only transaction data is extracted and stored, not full messages

### Internet Permission
- **Primary Purpose**: To download the AI model (Qwen 2.5) on first use
- **Model Download**: One-time download of ~1.5GB model file from CloudFront CDN
- **App Updates**: Google Play Store variant uses Play Services for app updates (F-Droid variant does not)
- **After Model Download**: AI works completely offline, no internet required for core features
- **Your Data**: Never transmitted over the internet, all processing remains on-device

### No Other Permissions Required
- No location tracking
- No contact access
- No camera or microphone access

## Third-Party Services

PennyWise AI does **NOT** use:
- ❌ Cloud services or APIs (except CDN for model download)
- ❌ Analytics services (Google Analytics, Firebase, etc.)
- ❌ Crash reporting services
- ❌ Advertising networks
- ❌ Social media SDKs
- ❌ Payment processors

**Note**: The Google Play Store variant includes Play Services for app updates only. The F-Droid variant has no Google services.

## AI Features

### On-Device AI Assistant
- Uses MediaPipe's Qwen 2.5 model (1.5GB download)
- Model runs entirely on your device using MediaPipe LLM Inference
- After initial download, no internet connection required
- Conversations are not stored or transmitted
- AI insights are generated locally from your local transaction data
- Model file stored in app's private storage

## Data Export

When you export your data:
- CSV/PDF files are created locally on your device
- You control where to share or save them
- No automatic uploads or backups

## Open Source Transparency

PennyWise AI is fully open source:
- Review our code at [GitHub](https://github.com/sarim2000/pennywiseai-tracker)
- Verify our privacy claims yourself
- Contribute to make it even better

## Children's Privacy

PennyWise AI is not directed at children under 13. We do not knowingly collect information from children.

## Changes to Privacy Policy

Any changes to this privacy policy will be:
- Updated in the app repository
- Reflected in the "Last Updated" date
- Communicated through release notes

## Contact

For privacy concerns or questions:
- Open an issue on [GitHub](https://github.com/sarim2000/pennywiseai-tracker/issues)
- Join our [Discord community](https://discord.gg/H3xWeMWjKQ)

## Summary

**Your financial data stays on your phone. Period.**

- No servers
- No uploads
- No tracking
- No ads
- Complete privacy

---

*PennyWise AI - Privacy-first expense tracking with on-device AI*