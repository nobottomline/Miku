#!/bin/bash
# Miku Server — Stop all services
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "🛑 Stopping Miku Server..."

# 1. Backend
echo "   Stopping backend (port 8080)..."
lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true

# 2. Frontend
echo "   Stopping frontend (port 3000)..."
lsof -ti:3000 2>/dev/null | xargs kill -9 2>/dev/null || true

# 3. Gradle daemons
echo "   Stopping Gradle daemons..."
pkill -f "GradleDaemon" 2>/dev/null || true

# 4. Docker
echo "   Stopping Docker services..."
docker compose -f docker-compose.dev.yml down 2>&1 | grep -E "Stopped|Removed" || true

echo ""
echo "✅ All services stopped."
