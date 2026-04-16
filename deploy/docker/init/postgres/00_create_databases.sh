#!/usr/bin/env bash
# =============================================================
# PostgreSQL 多库初始化脚本
# 在 docker-entrypoint-initdb.d 中以 postgres 超级用户执行
# =============================================================
set -e

echo ">>> 创建业务数据库和用户..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- ── 数据库 ──────────────────────────────────────────────
    CREATE DATABASE tianjing_prod
        ENCODING 'UTF8'
        LC_COLLATE 'en_US.utf8'
        LC_CTYPE 'en_US.utf8'
        TEMPLATE template0;

    CREATE DATABASE tianjing_sandbox
        ENCODING 'UTF8'
        LC_COLLATE 'en_US.utf8'
        LC_CTYPE 'en_US.utf8'
        TEMPLATE template0;

    CREATE DATABASE tianjing_train
        ENCODING 'UTF8'
        LC_COLLATE 'en_US.utf8'
        LC_CTYPE 'en_US.utf8'
        TEMPLATE template0;

    CREATE DATABASE nacos_config
        ENCODING 'UTF8'
        LC_COLLATE 'en_US.utf8'
        LC_CTYPE 'en_US.utf8'
        TEMPLATE template0;

    -- ── 用户 ────────────────────────────────────────────────
    CREATE USER tianjing_prod_user    WITH ENCRYPTED PASSWORD '${TIANJING_PROD_DB_PASSWORD}';
    CREATE USER tianjing_sandbox_user WITH ENCRYPTED PASSWORD '${TIANJING_SANDBOX_DB_PASSWORD}';
    CREATE USER tianjing_train_user   WITH ENCRYPTED PASSWORD '${TIANJING_TRAIN_DB_PASSWORD}';
    CREATE USER nacos                 WITH ENCRYPTED PASSWORD '${NACOS_DB_PASSWORD}';

    -- ── 授权（数据库级）────────────────────────────────────
    GRANT ALL PRIVILEGES ON DATABASE tianjing_prod    TO tianjing_prod_user;
    GRANT ALL PRIVILEGES ON DATABASE tianjing_sandbox TO tianjing_sandbox_user;
    GRANT ALL PRIVILEGES ON DATABASE tianjing_train   TO tianjing_train_user;
    GRANT ALL PRIVILEGES ON DATABASE nacos_config     TO nacos;
EOSQL

# PostgreSQL 16 默认撤销了 PUBLIC 对 public schema 的 CREATE 权限，需逐库补授
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname tianjing_prod    -c "GRANT ALL ON SCHEMA public TO tianjing_prod_user;"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname tianjing_sandbox -c "GRANT ALL ON SCHEMA public TO tianjing_sandbox_user;"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname tianjing_train   -c "GRANT ALL ON SCHEMA public TO tianjing_train_user;"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname nacos_config     -c "GRANT ALL ON SCHEMA public TO nacos;"

echo ">>> 导入生产库 Schema（Flyway V1-V4）..."
psql -v ON_ERROR_STOP=1 --username tianjing_prod_user --dbname tianjing_prod \
    -f /flyway/prod/V1__init_tianjing_prod.sql
psql -v ON_ERROR_STOP=1 --username tianjing_prod_user --dbname tianjing_prod \
    -f /flyway/prod/V2__init_data_tianjing_prod.sql
psql -v ON_ERROR_STOP=1 --username tianjing_prod_user --dbname tianjing_prod \
    -f /flyway/prod/V3__add_workflow_def.sql
psql -v ON_ERROR_STOP=1 --username tianjing_prod_user --dbname tianjing_prod \
    -f /flyway/prod/V4__test_data_full.sql

echo ">>> 导入 Sandbox 库 Schema（Flyway V1-V2）..."
psql -v ON_ERROR_STOP=1 --username tianjing_sandbox_user --dbname tianjing_sandbox \
    -f /flyway/sandbox/V1__init_tianjing_sandbox.sql
psql -v ON_ERROR_STOP=1 --username tianjing_sandbox_user --dbname tianjing_sandbox \
    -f /flyway/sandbox/V2__test_data_sandbox.sql

echo ">>> 导入训练库 Schema（Flyway V1-V2）..."
psql -v ON_ERROR_STOP=1 --username tianjing_train_user --dbname tianjing_train \
    -f /flyway/train/V1__init_tianjing_train.sql
psql -v ON_ERROR_STOP=1 --username tianjing_train_user --dbname tianjing_train \
    -f /flyway/train/V2__test_data_train.sql

echo ">>> PostgreSQL 初始化完成 ✓"
