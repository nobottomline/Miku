#!/bin/bash
# Miku Server — Start all services
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting Miku Server..."

# 1. Docker
echo ""
echo "📦 Starting Docker services..."
if ! docker info &>/dev/null; then
    echo "   Opening Docker Desktop..."
    open -a Docker 2>/dev/null || true
    for i in {1..30}; do
        docker info &>/dev/null && break
        sleep 2
    done
fi

docker compose -f docker-compose.dev.yml up -d 2>&1 | grep -E "Started|Running|Created" || true

# 2. Wait for PostgreSQL
echo ""
echo "⏳ Waiting for PostgreSQL..."
for i in {1..20}; do
    nc -z localhost 5432 2>/dev/null && echo "   PostgreSQL: ready" && break
    sleep 2
    [ $i -eq 20 ] && echo "   ⚠️  PostgreSQL timeout — server will retry"
done

# 3. Backend
echo ""
echo "⚙️  Starting backend server..."
lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true
sleep 1
nohup ./gradlew :apps:server:run > /tmp/miku-server.log 2>&1 &
BACKEND_PID=$!
echo "   PID: $BACKEND_PID"

for i in {1..40}; do
    H=$(curl -s --max-time 2 http://localhost:8080/health 2>/dev/null)
    if [ -n "$H" ]; then
        echo "   Backend: ready"
        break
    fi
    sleep 3
    [ $i -eq 40 ] && echo "   ⚠️  Backend timeout — check /tmp/miku-server.log"
done

# 4. Frontend
echo ""
echo "🌐 Starting frontend..."
lsof -ti:3000 2>/dev/null | xargs kill -9 2>/dev/null || true
sleep 1
cd apps/web
npm install --silent 2>/dev/null
nohup npm run dev > /tmp/miku-web.log 2>&1 &
FRONTEND_PID=$!
cd "$SCRIPT_DIR"
echo "   PID: $FRONTEND_PID"
sleep 3

# 5. Summary
echo ""
echo "═══════════════════════════════════════════"
echo "  Miku Server — All services running"
echo "═══════════════════════════════════════════"
echo ""
echo "  Frontend:     http://localhost:3000"
echo "  Backend API:  http://localhost:8080"
echo "  Swagger Docs: http://localhost:8080/api/docs"
echo "  GraphiQL:     http://localhost:8080/graphiql"
echo "  Grafana:      http://localhost:3001  (admin/miku)"
echo "  Prometheus:   http://localhost:9090"
echo "  MinIO:        http://localhost:9001  (miku-admin/miku-secret-key)"
echo ""
echo "  Logs: /tmp/miku-server.log"
echo "        /tmp/miku-web.log"
echo ""
echo "  Stop: ./stop.sh"
echo "═══════════════════════════════════════════"
