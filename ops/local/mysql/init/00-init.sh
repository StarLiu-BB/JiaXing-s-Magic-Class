#!/bin/sh
set -eu

mysql --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" <<'SQL'
SOURCE /workspace/zhixue-modules/zhixue-system/src/main/resources/sql/system_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-system/src/main/resources/sql/system_seed.sql;
SOURCE /workspace/zhixue-modules/zhixue-course/src/main/resources/sql/course_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-course/src/main/resources/sql/course_seed.sql;
SOURCE /workspace/zhixue-modules/zhixue-media/src/main/resources/sql/media_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-media/src/main/resources/sql/media_seed.sql;
SOURCE /workspace/zhixue-modules/zhixue-interaction/src/main/resources/sql/interaction_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-interaction/src/main/resources/sql/interaction_seed.sql;
SOURCE /workspace/zhixue-modules/zhixue-order/src/main/resources/sql/order_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-order/src/main/resources/sql/order_seed.sql;
SOURCE /workspace/zhixue-modules/zhixue-marketing/src/main/resources/sql/marketing_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-marketing/src/main/resources/sql/marketing_seed.sql;
SOURCE /workspace/zhixue-modules/zhixue-ai/src/main/resources/sql/ai_schema.sql;
SOURCE /workspace/zhixue-modules/zhixue-ai/src/main/resources/sql/ai_seed.sql;
SQL
