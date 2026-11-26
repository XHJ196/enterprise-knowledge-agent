-- Day 13：本地知识库持久化演示所需的创建人。
-- 手动执行的演示数据脚本，不属于 Flyway 表结构迁移。
-- UUID 与当前 KnowledgeBaseController 中的 demoUserId 一致。
-- 当前没有认证功能；password_hash 中是禁用登录标记，不是真实密码哈希。
-- 接入认证时，应通过认证模块设置合规哈希并核对演示账号初始化方式。

BEGIN;

INSERT INTO core.users (id, username, password_hash, role)
VALUES (
    '10000000-0000-4000-8000-000000000002',
    'day13_demo_admin',
    '!day13-demo-login-disabled',
    'ADMIN'
)
ON CONFLICT (id) DO NOTHING;

-- 已有相同 ID 时保留原记录，但必须确认它确实是预期的演示创建人。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM core.users
        WHERE id = '10000000-0000-4000-8000-000000000002'
          AND username = 'day13_demo_admin'
          AND role = 'ADMIN'
    ) THEN
        RAISE EXCEPTION 'Demo user ID does not match the expected username and role';
    END IF;
END;
$$;

SELECT id, username, role
FROM core.users
WHERE id = '10000000-0000-4000-8000-000000000002';

COMMIT;
