# Custom features during upstream updates

This checkout combines upstream PennyWise with local Thai slip scanning. After
merging upstream, run `bash scripts/check-custom-integration.sh` and
`./gradlew :app:testStandardDebugUnitTest`. The CI app unit-test job runs the
integration check too. A green check proves the entry points remain wired; it
does not replace reviewing behavior on a device.

## Integration points to review after every merge

| Custom capability | Entry points |
|---|---|
| Automatic slip discovery | `MainActivity`, `SlipMediaObserver`, `SlipForegroundScanner`, `SlipAutoScanWorker` |
| SMS buttons also scan slips | `HomeViewModel.scanSmsMessages`, `SmsScanManager.startSmsLoggingScan` (onboarding) |
| Slip parsing and storage | `SlipOcrEngine`, `SlipParser`, `ProcessSlipUseCase`, `SlipScanHistoryDao`, `ScanCorrectionDao`, Room migrations |
| Merchant name learning and rollback | `SlipRecipientResolver`, `SlipMerchantLearning`, `TransactionDetailViewModel.saveChanges`, `TransactionDetailScreen` |
| Rescan and review | `TransactionDetailViewModel.rescanSlip`, `TransactionDetailScreen`, `SlipScanBottomSheet` |
| Reset learned names | `SettingsScreen`, `SettingsViewModel`, `ScanCorrectionDao` |
| Permissions and resources | `AndroidManifest.xml`, `SlipScanAccess`, Thai and default strings, OCR assets |

Keep the custom feature code in commits separate from upstream sync commits.
When resolving a conflict, inspect both sides of each entry point above and
retain upstream API changes while reconnecting the custom call. Learned-name
provenance is stored in `scan_corrections` rows named `merchantBackfill:<ruleId>`.
Deleting a learned rule restores only rows still carrying its applied name;
later manual edits are preserved. Merchant aliases and category mappings are
separate settings and are not cleared by learned-name reset.
Rows renamed by older app versions before provenance was recorded cannot be
reliably identified for automatic rollback; reset removes those old rules but
leaves such rows for manual review.

Before shipping an upstream merge, manually check one slip scan, an SMS scan
from Home and onboarding, merchant suggestion and rescan in transaction edit,
and the Settings reset dialog. The integration script checks source wiring but
cannot establish that OCR, permissions, or UI layout work on a device.
