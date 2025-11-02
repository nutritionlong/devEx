#!/bin/bash
set -e

# --- Remote and Local Configs ---
REMOTE_DB_HOST="sit-db.readonly.company.com"
REMOTE_DB_USER="readonly_user"
REMOTE_DB_PASS="...password..."
REMOTE_DB_NAME="sit_database"

LOCAL_DB_CONTAINER="mysql-db" # The service name from docker-compose.yml
LOCAL_DB_USER="root"
LOCAL_DB_PASS="123456"       # Password from your .env file
LOCAL_DB_NAME="devex"

echo "🔄 Preparing to pull latest data from [${REMOTE_DB_HOST}]..."

# --- 1. Export data from remote SIT database ---
echo "1/3 Exporting remote database (mysqldump)..."
mysqldump -h ${REMOTE_DB_HOST} -u ${REMOTE_DB_USER} -p${REMOTE_DB_PASS} \
    --single-transaction --no-tablespaces ${REMOTE_DB_NAME} > latest_dump.sql

echo "✅ Remote data exported to latest_dump.sql"

# --- 2. Clean up local database ---
echo "2/3 Cleaning local Docker database..."
docker exec ${LOCAL_DB_CONTAINER} mysql -u ${LOCAL_DB_USER} -p${LOCAL_DB_PASS} -e "DROP DATABASE IF EXISTS ${LOCAL_DB_NAME}; CREATE DATABASE ${LOCAL_DB_NAME};"

# --- 3. Import latest data into local database ---
echo "3/3 Importing data into local Docker database..."
cat latest_dump.sql | docker exec -i ${LOCAL_DB_CONTAINER} mysql -u ${LOCAL_DB_USER} -p${LOCAL_DB_PASS} ${LOCAL_DB_NAME}

rm latest_dump.sql
echo "✅ Data sync complete! Your local database now has the latest SIT data."