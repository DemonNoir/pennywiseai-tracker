#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

check() {
  local description="$1" file="$2" pattern="$3"
  if ! rg -q -- "$pattern" "$file"; then
    echo "Missing custom integration: $description ($file)" >&2
    exit 1
  fi
}

check 'slip scanner on app resume' app/src/main/java/com/pennywiseai/tracker/MainActivity.kt 'startCatchUpScan'
check 'SMS scan launches slip scan on Home' app/src/main/java/com/pennywiseai/tracker/presentation/home/HomeViewModel.kt 'SlipAutoScanWorker.WORK_NAME'
check 'SMS scan launches slip scan on onboarding' app/src/main/java/com/pennywiseai/tracker/data/manager/SmsScanManager.kt 'SlipAutoScanWorker.WORK_NAME'
check 'automatic slip retains receipt' app/src/main/java/com/pennywiseai/tracker/slip/worker/SlipAutoScanWorker.kt 'imageUriString = uri.toString\(\)'
check 'slip merchant resolver' app/src/main/java/com/pennywiseai/tracker/domain/usecase/ProcessSlipUseCase.kt 'findLearnedMerchant'
check 'edit screen shows merchant suggestions' app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt 'merchantSuggestions.forEach'
check 'edit screen has visible rescan action' app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt 'onClick = viewModel::rescanSlip'
check 'merchant edits save learned corrections' app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailViewModel.kt 'slipMerchantLearning.learn\('
check 'learned names retain rollback provenance' app/src/main/java/com/pennywiseai/tracker/slip/recipient/SlipMerchantLearning.kt 'merchantBackfill:'
check 'settings exposes correction reset' app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsScreen.kt 'settings_reset_slip_names_title'
check 'reset restores automatically changed names' app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsViewModel.kt 'slipMerchantLearning.forgetAll\('
echo 'Custom integration entry points present.'
