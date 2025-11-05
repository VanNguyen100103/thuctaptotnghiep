-- Insert Categories for E-commerce Fashion Store
-- Order: Parent categories first, then child categories

-- Parent Categories (no parent_id)
INSERT INTO categories (id, name, slug, description, image_url, active, display_order, parent_id, created_at, updated_at) VALUES
(1, 'Thời Trang Nam', 'thoi-trang-nam', '', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1761847445/categories/thoi-trang-nam/bzdq1k1tu67dlbrijasf.jpg', true, 0, NULL, NOW(), NOW()),
(2, 'Thời Trang Nữ', 'thoi-trang-nu', '', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1761849437/categories/thoi-trang-nu/y8goxp7xij6uhau0wct6.webp', true, 0, NULL, NOW(), NOW()),
(9, 'Thể Thao', 'the-thao', 'Các đồ thể thao phù hợp nhu cầu vận động của quý khách', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1762182361/categories/the-thao/r5hwhf8h2pio1cmbioaw.jpg', true, 0, NULL, NOW(), NOW()),
(10, 'Care And Share', 'care-and-share', 'Care & Share là dự án CSR được xây dựng và phát triển bởi Coolmate, trong đó hoạt động trọng tâm là bán hàng gây quỹ và sử dụng quỹ để tổ chức các hoạt động xã hội trọng tâm hướng tới trẻ em', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1762197479/categories/care-and-share/vvzjvxqosdvovqr0fwh4.png', true, 0, NULL, NOW(), NOW());

-- Child Categories (with parent_id)
INSERT INTO categories (id, name, slug, description, image_url, active, display_order, parent_id, created_at, updated_at) VALUES
(3, 'Áo Nam', 'ao-nam', '', NULL, true, 0, 1, NOW(), NOW()),
(4, 'Quần Nam', 'quan-nam', NULL, NULL, true, 0, 1, NOW(), NOW()),
(7, 'Giày Nam', 'giay-nam', 'Giày nam nâng tầm bước chân nam giới', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1761847978/categories/giay-nam/vn5ejr5rhkssbqcund3y.jpg', true, 1, 1, NOW(), NOW()),
(5, 'Áo Nữ', 'ao-nu', '', NULL, true, 0, 2, NOW(), NOW()),
(6, 'Quần Nữ', 'quan-nu', NULL, NULL, true, 0, 2, NOW(), NOW()),
(8, 'Giày Nữ', 'giay-nu', NULL, NULL, true, 0, 2, NOW(), NOW());

-- Reset sequence to continue from ID 11
SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));

-- Verify inserted data
SELECT id, name, slug, parent_id, active, display_order FROM categories ORDER BY COALESCE(parent_id, 0), display_order, id;
