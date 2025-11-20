#!/bin/bash

echo "🧹 NUCLEAR CLEAN - Forcing complete rebuild"
echo "==========================================="

# 1. Stop all Gradle daemons
echo "1. Stopping Gradle daemons..."
./gradlew --stop
killall -9 java 2>/dev/null || true

# 2. Delete ALL build artifacts
echo "2. Deleting all build folders..."
rm -rf .gradle
rm -rf build
rm -rf app/build
rm -rf core/data/build
rm -rf core/database/build
rm -rf core/datastore/build
rm -rf core/design/build
rm -rf core/domain/build
rm -rf feature/feed/build
rm -rf feature/details/build
rm -rf feature/submit/build
rm -rf feature/archive/build
rm -rf feature/report/build
find . -type d -name "build" -exec rm -rf {} + 2>/dev/null || true

# 3. Delete Android build cache
echo "3. Deleting Android build cache..."
rm -rf ~/.gradle/caches/
rm -rf ~/.android/build-cache/

# 4. Verify we're on the right commit
echo "4. Verifying current commit..."
CURRENT_COMMIT=$(git rev-parse --short HEAD)
echo "   Current commit: $CURRENT_COMMIT"
echo "   Expected: 4e65123"

if [ "$CURRENT_COMMIT" != "4e65123" ]; then
    echo "   ⚠️  WARNING: Not on the expected commit!"
    echo "   Checking out correct commit..."
    git checkout 4e65123
fi

# 5. Verify the code is correct
echo "5. Verifying source code..."
if grep -q "🗳️ Optimistic vote" core/data/src/main/java/qa/deals/doha/repository/DealRepository.kt; then
    echo "   ✅ Repository code is CORRECT (has emoji log)"
else
    echo "   ❌ Repository code is WRONG!"
    exit 1
fi

if grep -q "user_id: String" core/data/src/main/java/qa/deals/doha/network/VoteRequest.kt; then
    echo "   ✅ VoteRequest code is CORRECT (has user_id)"
else
    echo "   ❌ VoteRequest code is WRONG!"
    exit 1
fi

echo ""
echo "==========================================="
echo "✅ Clean complete! Now rebuild in Android Studio:"
echo "   1. File → Invalidate Caches → Invalidate and Restart"
echo "   2. Build → Rebuild Project"
echo "   3. Uninstall app from phone (Settings → Apps → Doha Deals → Uninstall)"
echo "   4. Run app (green play button)"
echo ""
echo "Expected logs after rebuild:"
echo "   Repository: 🗳️ Optimistic vote: cold for deal... by user 79230ad0..."
echo "   HTTP: {\"user_id\":\"79230ad0-...\"}"
echo "==========================================="
