#!/bin/bash

# This script is called by the release workflow to update version in build.gradle.kts
# Usage: ./scripts/update-version.sh <version>

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Error: Version not provided"
    echo "Usage: $0 <version>"
    exit 1
fi

echo "Updating version to $VERSION"

# Update versionName
sed -i "s/versionName = \".*\"/versionName = \"$VERSION\"/" app/build.gradle.kts

# Increment versionCode
CURRENT_CODE=$(grep "versionCode = " app/build.gradle.kts | sed 's/[^0-9]*//g')
NEW_CODE=$((CURRENT_CODE + 1))
sed -i "s/versionCode = .*/versionCode = $NEW_CODE/" app/build.gradle.kts

echo "✅ Updated to version $VERSION (code: $NEW_CODE)"

# Show the changes
echo "Changes made:"
grep -E "versionName|versionCode" app/build.gradle.kts