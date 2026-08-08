-- ================================================================
-- 测试用户（实际项目中从用户服务获取，这里直接插）
-- ================================================================

-- ================================================================
-- 知识库初始化
-- ================================================================
INSERT INTO kb_knowledge_base (name, description, department_id, is_public, created_by)
VALUES
    ('HR知识库',        '公司人事制度、员工手册、入职离职流程等文档',   'HR',   FALSE, 1),
    ('技术知识库',      '技术规范、架构设计文档、开发指南等',           'TECH', FALSE, 2),
    ('产品知识库',      '产品手册、功能说明、FAQ等面向内部的产品文档',   'PROD', TRUE,  3),
    ('公司公共知识库',  '公司介绍、组织架构、通用制度等所有人可查的文档', 'ALL',  TRUE,  1);

-- ================================================================
-- 权限配置
-- ================================================================
-- HR 知识库：HR 部门有写权限，其他部门有读权限
INSERT INTO kb_permission (kb_id, subject_type, subject_id, permission, granted_by)
VALUES
    (1, 'DEPARTMENT', 'HR',   'WRITE', 1),
    (1, 'DEPARTMENT', 'TECH', 'READ',  1),
    (1, 'DEPARTMENT', 'PROD', 'READ',  1);

-- 技术知识库：技术部门有写权限
INSERT INTO kb_permission (kb_id, subject_type, subject_id, permission, granted_by)
VALUES
    (2, 'DEPARTMENT', 'TECH', 'WRITE', 2),
    (2, 'DEPARTMENT', 'PROD', 'READ',  2);

-- ================================================================
-- 评估数据集（用于衡量 RAG 检索质量）
-- ================================================================
INSERT INTO kb_eval_dataset (kb_id, question, expected_answer, expected_chunk_ids, created_by)
VALUES
    (1, '新员工入职第一天需要做什么？',
     '领取工牌和电脑，配置 VPN 和开发环境，与直属 Leader 完成对齐会，阅读代码规范。',
     NULL, 1),
    (1, '年假是怎么规定的？',
     '工作满 1 年未满 10 年享有 5 天年假，工作满 10 年以上享有 10 天年假。',
     NULL, 1),
    (2, 'API 限流策略是什么？',
     '单用户每分钟最多 100 次调用，采用滑动窗口算法，超出后返回 429 状态码。',
     NULL, 2),
    (2, '代码提交规范有哪些？',
     'Commit message 格式为 type(scope): message，type 包括 feat/fix/docs/refactor。',
     NULL, 2),
    (3, '如何申请 API 访问权限？',
     '在开发者控制台创建 API Key，免费配额为每日 1000 次调用，付费套餐按量计费。',
     NULL, 3);

-- expected_chunk_ids 初始为 NULL，文档上传切分后再回填。在后面rag效果评估会讲
-- 查询实际 chunk ID：
--   SELECT id, LEFT(content, 50) FROM kb_doc_chunk WHERE kb_id = 1 ORDER BY id;
-- 然后回填：
--   UPDATE kb_eval_dataset SET expected_chunk_ids = ARRAY[1, 2] WHERE id = 1;
--   UPDATE kb_eval_dataset SET expected_chunk_ids = ARRAY[3]    WHERE id = 2;
-- ================================================================
-- Sales Agent demo data
-- ================================================================
INSERT INTO sa_sales_region (id, name)
VALUES
    (1, '华东区'),
    (2, '华南区'),
    (3, '华北区'),
    (4, '西南区')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sa_sales_rep (id, name, region_id, role, email)
VALUES
    (1, '李明', 1, 'SALES_MANAGER', 'liming@lsn.com'),
    (2, '张伟', 1, 'SALES_REP', 'zhangwei@lsn.com'),
    (3, '王芳', 1, 'SALES_REP', 'wangfang@lsn.com'),
    (4, '陈强', 2, 'SALES_MANAGER', 'chenqiang@lsn.com'),
    (5, '刘洋', 2, 'SALES_REP', 'liuyang@lsn.com'),
    (6, '赵雪', 2, 'SALES_REP', 'zhaoxue@lsn.com'),
    (7, '孙磊', 3, 'SALES_MANAGER', 'sunlei@lsn.com'),
    (8, '张磊', 3, 'SALES_REP', 'zhanglei@lsn.com'),
    (9, '周丽', 3, 'SALES_REP', 'zhouli@lsn.com'),
    (10, '吴刚', 4, 'SALES_MANAGER', 'wugang@lsn.com'),
    (11, '郑华', 4, 'SALES_REP', 'zhenghua@lsn.com'),
    (12, '林敏', 4, 'SALES_REP', 'linmin@lsn.com'),
    (13, '黄总', 1, 'SALES_DIRECTOR', 'huang@lsn.com')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sa_product (id, sku_code, name, category, unit_price, cost, status)
VALUES
    (1, 'SKU-1001', '旗舰智能手机 Pro', '数码产品', 6999.00, 4200.00, 'ACTIVE'),
    (2, 'SKU-1002', '高端商务笔记本 X1', '数码产品', 9999.00, 6800.00, 'ACTIVE'),
    (3, 'SKU-2001', '智能扫地机器人 Max', '家用电器', 3999.00, 2100.00, 'ACTIVE'),
    (4, 'SKU-2002', '空气净化器 Plus', '家用电器', 2199.00, 1200.00, 'ACTIVE'),
    (5, 'SKU-3001', '城市通勤双肩包', '服装配饰', 699.00, 280.00, 'ACTIVE'),
    (6, 'SKU-8821', '智能手表 Pro', '数码产品', 1299.00, 650.00, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sa_sales_order (order_no, rep_id, product_id, region_id, customer_name, quantity, unit_price, amount, cost, profit, status, order_date)
VALUES
    ('LSN-B01-001', 2, 1, 1, '上海启明科技有限公司', 4, 6999.00, 27996.00, 16800.00, 11196.00, 'COMPLETED', CURRENT_DATE - INTERVAL '95 days'),
    ('LSN-B01-002', 3, 5, 1, '南京城市零售集团', 30, 699.00, 20970.00, 8400.00, 12570.00, 'COMPLETED', CURRENT_DATE - INTERVAL '88 days'),
    ('LSN-B01-003', 5, 3, 2, '广州家电渠道商', 8, 3999.00, 31992.00, 16800.00, 15192.00, 'COMPLETED', CURRENT_DATE - INTERVAL '82 days'),
    ('LSN-B01-004', 8, 2, 3, '北京教育采购中心', 5, 9999.00, 49995.00, 34000.00, 15995.00, 'COMPLETED', CURRENT_DATE - INTERVAL '76 days'),
    ('LSN-B01-005', 11, 4, 4, '成都健康空间门店', 10, 2199.00, 21990.00, 12000.00, 9990.00, 'COMPLETED', CURRENT_DATE - INTERVAL '72 days'),
    ('LSN-B02-001', 2, 2, 1, '杭州数字化服务商', 7, 9999.00, 69993.00, 47600.00, 22393.00, 'COMPLETED', CURRENT_DATE - INTERVAL '54 days'),
    ('LSN-B02-002', 5, 1, 2, '深圳消费电子连锁', 6, 6999.00, 41994.00, 25200.00, 16794.00, 'COMPLETED', CURRENT_DATE - INTERVAL '47 days'),
    ('LSN-B02-003', 8, 6, 3, '天津数码体验店', 10, 1299.00, 12990.00, 6500.00, 6490.00, 'COMPLETED', CURRENT_DATE - INTERVAL '39 days'),
    ('LSN-B02-004', 9, 2, 3, '石家庄企业采购部', 3, 9999.00, 29997.00, 20400.00, 9597.00, 'COMPLETED', CURRENT_DATE - INTERVAL '35 days'),
    ('LSN-B02-005', 12, 3, 4, '重庆智慧家居门店', 5, 3999.00, 19995.00, 10500.00, 9495.00, 'COMPLETED', CURRENT_DATE - INTERVAL '31 days'),
    ('LSN-B03-001', 2, 1, 1, '苏州新零售有限公司', 5, 6999.00, 34995.00, 21000.00, 13995.00, 'COMPLETED', CURRENT_DATE - INTERVAL '14 days'),
    ('LSN-B03-002', 5, 3, 2, '广州家居生态渠道', 4, 3999.00, 15996.00, 8400.00, 7596.00, 'COMPLETED', CURRENT_DATE - INTERVAL '11 days'),
    ('LSN-B03-003', 6, 4, 2, '珠海健康家电门店', 6, 2199.00, 13194.00, 7200.00, 5994.00, 'COMPLETED', CURRENT_DATE - INTERVAL '8 days'),
    ('LSN-B03-004', 11, 3, 4, '成都家庭智能体验馆', 4, 3999.00, 15996.00, 8400.00, 7596.00, 'COMPLETED', CURRENT_DATE - INTERVAL '5 days'),
    ('LSN-B03-005', 12, 5, 4, '昆明户外用品连锁', 20, 699.00, 13980.00, 5600.00, 8380.00, 'COMPLETED', CURRENT_DATE - INTERVAL '2 days'),
    ('LSN-R01-001', 3, 5, 1, '上海精品生活门店', 8, 699.00, 5592.00, 2240.00, 3352.00, 'REFUNDED', CURRENT_DATE - INTERVAL '24 days'),
    ('LSN-R01-002', 3, 5, 1, '南京生活方式集合店', 6, 699.00, 4194.00, 1680.00, 2514.00, 'REFUNDED', CURRENT_DATE - INTERVAL '18 days'),
    ('LSN-R01-003', 3, 1, 1, '无锡数码渠道', 2, 6999.00, 13998.00, 8400.00, 5598.00, 'REFUNDED', CURRENT_DATE - INTERVAL '9 days')
ON CONFLICT (order_no) DO NOTHING;
