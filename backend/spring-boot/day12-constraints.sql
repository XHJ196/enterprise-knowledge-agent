-- Day 12：知识库名称唯一索引与创建人外键验证。
-- 手动通过 psql 执行，不是 Flyway 迁移文件。
-- 使用 ON_ERROR_STOP=1；未预期的结果会导致执行失败。
-- 所有测试数据在同一事务中，末尾回滚；连接异常退出也不会提交。

BEGIN;

-- 仅用于约束验证的临时用户；此标记不是可登录的密码哈希。
INSERT INTO core.users (id, username, password_hash, role)
VALUES (
    '12000000-0000-4000-8000-000000000001',
    'day12_constraint_admin',
    '!day12-constraint-test-only',
    'ADMIN'
);

INSERT INTO core.knowledge_bases (id, name, created_by, lifecycle)
VALUES (
    '22000000-0000-4000-8000-000000000001',
    'Day12 Constraint Check',
    '12000000-0000-4000-8000-000000000001',
    'ACTIVE'
);

-- 可以观察显示名称被保留，而 name_key 由数据库自动生成小写。
SELECT name, name_key
FROM core.knowledge_bases
WHERE id = '22000000-0000-4000-8000-000000000001';

-- 1. 使用不同的 UUID、相同的创建人，只改变名称的英文字母大小写。
DO $$
DECLARE
    violated_constraint text;
BEGIN
    BEGIN
        INSERT INTO core.knowledge_bases (id, name, created_by, lifecycle)
        VALUES (
            '22000000-0000-4000-8000-000000000002',
            'day12 constraint check',
            '12000000-0000-4000-8000-000000000001',
            'ACTIVE'
        );
        RAISE EXCEPTION 'FAIL: duplicate knowledge base name was accepted';
    EXCEPTION WHEN unique_violation THEN
        GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
        IF violated_constraint IS DISTINCT FROM 'uq_live_kb_name' THEN
            RAISE;
        END IF;
        RAISE NOTICE 'PASS: duplicate name rejected by uq_live_kb_name';
    END;
END;
$$;

-- 2. 名称不重复，但创建人 ID 不存在。
DO $$
DECLARE
    violated_constraint text;
BEGIN
    BEGIN
        INSERT INTO core.knowledge_bases (id, name, created_by, lifecycle)
        VALUES (
            '22000000-0000-4000-8000-000000000003',
            'Day12 Missing Creator',
            '12000000-0000-4000-8000-000000000099',
            'ACTIVE'
        );
        RAISE EXCEPTION 'FAIL: missing knowledge base creator was accepted';
    EXCEPTION WHEN foreign_key_violation THEN
        GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
        IF violated_constraint IS DISTINCT FROM 'fk_kb_created_by' THEN
            RAISE;
        END IF;
        RAISE NOTICE 'PASS: missing creator rejected by fk_kb_created_by';
    END;
END;
$$;

ROLLBACK;

-- 重复执行也不会保留本脚本的用户和知识库。
SELECT
    (SELECT count(*) FROM core.users
     WHERE id = '12000000-0000-4000-8000-000000000001') AS test_users_remaining,
    (SELECT count(*) FROM core.knowledge_bases
     WHERE id IN (
         '22000000-0000-4000-8000-000000000001',
         '22000000-0000-4000-8000-000000000002',
         '22000000-0000-4000-8000-000000000003'
     )) AS test_knowledge_bases_remaining;
