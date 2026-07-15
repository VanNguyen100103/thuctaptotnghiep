-- =====================================================================
-- SEED DATA - E-commerce Fashion Store
-- Chay trong Neon SQL Editor (hoac psql), MOT LAN tren DB da co schema
-- (Flyway tao bang o lan khoi dong dau tien - xem backend/src/main/resources/db/migration)
-- Thu tu: categories -> products -> lien ket -> images -> coupons -> setval
-- =====================================================================

-- ============================ CATEGORIES =============================
INSERT INTO categories (id, name, slug, description, image_url, active, display_order, parent_id, created_at, updated_at) VALUES
(1, 'Thời Trang Nam', 'thoi-trang-nam', '', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1761847445/categories/thoi-trang-nam/bzdq1k1tu67dlbrijasf.jpg', true, 0, NULL, NOW(), NOW()),
(2, 'Thời Trang Nữ', 'thoi-trang-nu', '', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1761849437/categories/thoi-trang-nu/y8goxp7xij6uhau0wct6.webp', true, 0, NULL, NOW(), NOW()),
(9, 'Thể Thao', 'the-thao', 'Các đồ thể thao phù hợp nhu cầu vận động của quý khách', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1762182361/categories/the-thao/r5hwhf8h2pio1cmbioaw.jpg', true, 0, NULL, NOW(), NOW()),
(10, 'Care And Share', 'care-and-share', 'Dự án bán hàng gây quỹ hướng tới trẻ em', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1762197479/categories/care-and-share/vvzjvxqosdvovqr0fwh4.png', true, 0, NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO categories (id, name, slug, description, image_url, active, display_order, parent_id, created_at, updated_at) VALUES
(3, 'Áo Nam', 'ao-nam', '', NULL, true, 0, 1, NOW(), NOW()),
(4, 'Quần Nam', 'quan-nam', NULL, NULL, true, 0, 1, NOW(), NOW()),
(7, 'Giày Nam', 'giay-nam', 'Giày nam nâng tầm bước chân nam giới', 'https://res.cloudinary.com/dxxdhz5f5/image/upload/v1761847978/categories/giay-nam/vn5ejr5rhkssbqcund3y.jpg', true, 1, 1, NOW(), NOW()),
(5, 'Áo Nữ', 'ao-nu', '', NULL, true, 0, 2, NOW(), NOW()),
(6, 'Quần Nữ', 'quan-nu', NULL, NULL, true, 0, 2, NOW(), NOW()),
(8, 'Giày Nữ', 'giay-nu', NULL, NULL, true, 0, 2, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================ PRODUCTS ===============================
-- Gia VND. average_rating <= 9.99 (precision 3,2).
INSERT INTO products (id, name, slug, sku, short_description, description, price, compare_at_price, stock_quantity, active, featured, brand, material, gender, view_count, average_rating, review_count, sold_count, created_at, updated_at) VALUES
-- Ao Nam (cat 3)
(1,  'Áo Thun Nam Cotton Compact', 'ao-thun-nam-cotton-compact', 'ATN-001', 'Áo thun nam 100% cotton compact thoáng mát', 'Chất liệu cotton compact cao cấp, thấm hút mồ hôi, form regular fit dễ mặc. Phù hợp mặc hàng ngày và đi làm.', 199000, 259000, 120, true, true,  'CoolBasic', '100% Cotton Compact', 'Men',   532, 4.50, 12, 87,  NOW(), NOW()),
(2,  'Áo Sơ Mi Nam Oxford',        'ao-so-mi-nam-oxford',        'ASM-002', 'Sơ mi Oxford đứng form, lịch sự',           'Vải Oxford dày dặn, giữ form tốt, phù hợp môi trường công sở lẫn dạo phố. Cổ điển nhưng không lỗi thời.', 349000, 449000, 80,  true, false, 'UrbanVN',   'Cotton Oxford',      'Men',   321, 4.20, 8,  45,  NOW(), NOW()),
(3,  'Áo Polo Nam Pique',          'ao-polo-nam-pique',          'APL-003', 'Polo pique cổ bẻ thanh lịch',               'Vải cá sấu pique co giãn nhẹ, cổ bẻ dệt bo chắc chắn, không bai nhão sau nhiều lần giặt.', 279000, 329000, 100, true, true,  'CoolBasic', 'Cotton Pique',       'Men',   410, 4.70, 15, 92,  NOW(), NOW()),
-- Quan Nam (cat 4)
(4,  'Quần Jean Nam Slim Fit',     'quan-jean-nam-slim-fit',     'QJN-004', 'Jean slim fit co giãn thoải mái',           'Denim co giãn 2 chiều, form slim tôn dáng nhưng vẫn thoải mái khi vận động. Màu xanh đậm dễ phối đồ.', 499000, 599000, 90,  true, false, 'UrbanVN',   'Denim Spandex',      'Men',   287, 4.30, 9,  56,  NOW(), NOW()),
(5,  'Quần Kaki Nam',              'quan-kaki-nam',              'QKN-005', 'Kaki nam đứng form, không nhăn',            'Vải kaki cao cấp chống nhăn, phù hợp đi làm. Thiết kế tối giản với 2 màu cơ bản.', 399000, NULL,   70,  true, false, 'UrbanVN',   'Khaki Cotton',       'Men',   198, 4.10, 5,  34,  NOW(), NOW()),
(6,  'Quần Short Nam Thể Thao',    'quan-short-nam-the-thao',    'QSN-006', 'Short thể thao nhanh khô',                  'Vải poly nhanh khô, có túi khoá kéo, lưng thun co giãn. Lý tưởng cho gym và chạy bộ.', 179000, 229000, 150, true, false, 'ActiveFit', 'Polyester',          'Men',   356, 4.40, 11, 103, NOW(), NOW()),
-- Giay Nam (cat 7)
(7,  'Giày Sneaker Nam Basic',     'giay-sneaker-nam-basic',     'GSN-007', 'Sneaker trắng basic dễ phối',               'Da tổng hợp cao cấp, đế cao su đúc chống trượt. Item phải có trong tủ đồ mọi chàng trai.', 699000, 899000, 60,  true, true,  'StreetVN',  'Da tổng hợp',        'Men',   612, 4.60, 21, 78,  NOW(), NOW()),
(8,  'Giày Chạy Bộ Nam',           'giay-chay-bo-nam',           'GCB-008', 'Giày chạy đế êm, siêu nhẹ',                 'Đế phylon êm ái, upper mesh thoáng khí, trọng lượng chỉ 240g. Đồng hành mọi cung đường chạy.', 899000, 1190000, 45, true, false, 'ActiveFit', 'Mesh + Phylon',      'Men',   445, 4.80, 17, 52,  NOW(), NOW()),
(9,  'Dép Quai Ngang Nam',         'dep-quai-ngang-nam',         'DQN-009', 'Dép quai ngang êm nhẹ',                     'Đế EVA siêu nhẹ, quai ôm chân không đau. Đi trong nhà hay ra phố đều hợp.', 149000, NULL,   200, true, false, 'StreetVN',  'EVA',                'Men',   289, 4.00, 7,  145, NOW(), NOW()),
-- Ao Nu (cat 5)
(10, 'Áo Thun Nữ Croptop',         'ao-thun-nu-croptop',         'ATN-010', 'Croptop năng động trẻ trung',               'Cotton mềm mại co giãn 4 chiều, form croptop tôn dáng. Nhiều màu pastel dễ thương.', 189000, 239000, 110, true, true,  'CoolBasic', 'Cotton Spandex',     'Women', 478, 4.50, 14, 96,  NOW(), NOW()),
(11, 'Áo Sơ Mi Nữ Lụa',            'ao-so-mi-nu-lua',            'ASM-011', 'Sơ mi lụa mềm mại sang trọng',              'Chất lụa cao cấp rũ nhẹ, thoáng mát. Phù hợp công sở và các buổi gặp gỡ quan trọng.', 389000, 489000, 65,  true, false, 'UrbanVN',   'Lụa Polyester',      'Women', 302, 4.30, 10, 41,  NOW(), NOW()),
(12, 'Áo Khoác Nữ Bomber',         'ao-khoac-nu-bomber',         'AKN-012', 'Bomber nữ cá tính chống gió',               'Lớp ngoài chống gió nhẹ, lót trong mềm. Form bomber trẻ trung, dễ phối cùng jean và sneaker.', 599000, 749000, 40,  true, false, 'StreetVN',  'Polyester',          'Women', 265, 4.60, 8,  29,  NOW(), NOW()),
-- Quan Nu (cat 6)
(13, 'Quần Jean Nữ Ống Rộng',      'quan-jean-nu-ong-rong',      'QJN-013', 'Jean ống rộng phong cách retro',            'Denim cứng cáp form ống rộng, tôn dáng che khuyết điểm. Hot trend không thể bỏ lỡ.', 459000, 559000, 85,  true, false, 'UrbanVN',   'Denim',              'Women', 388, 4.40, 13, 67,  NOW(), NOW()),
(14, 'Chân Váy Chữ A',             'chan-vay-chu-a',             'CVA-014', 'Chân váy chữ A thanh lịch',                 'Form chữ A cạp cao tôn dáng, có lót trong. Dễ phối cùng sơ mi hoặc áo thun.', 299000, NULL,   75,  true, false, 'UrbanVN',   'Tuytsi',             'Women', 214, 4.20, 6,  38,  NOW(), NOW()),
(15, 'Quần Legging Nữ',            'quan-legging-nu',            'QLN-015', 'Legging tập gym nâng mông',                 'Vải co giãn 4 chiều độ nén vừa phải, cạp cao gọn bụng. Không lộ khi squat.', 199000, 249000, 130, true, false, 'ActiveFit', 'Nylon Spandex',      'Women', 502, 4.70, 19, 118, NOW(), NOW()),
-- Giay Nu (cat 8)
(16, 'Giày Sneaker Nữ Trắng',      'giay-sneaker-nu-trang',      'GSN-016', 'Sneaker trắng nữ must-have',                'Thiết kế tối giản tôn chân, đế độn 3cm hack dáng tự nhiên. Da tổng hợp dễ vệ sinh.', 649000, 799000, 55,  true, true,  'StreetVN',  'Da tổng hợp',        'Women', 587, 4.50, 22, 84,  NOW(), NOW()),
(17, 'Giày Cao Gót 5cm',           'giay-cao-got-5cm',           'GCG-017', 'Cao gót 5cm êm chân đi cả ngày',            'Gót vuông 5cm vững chãi, mũi tròn không đau ngón. Lót đệm êm cho ngày dài công sở.', 549000, NULL,   40,  true, false, 'UrbanVN',   'Da tổng hợp',        'Women', 246, 4.10, 9,  31,  NOW(), NOW()),
(18, 'Sandal Nữ Đế Bệt',           'sandal-nu-de-bet',           'SND-018', 'Sandal bệt nhẹ nhàng nữ tính',              'Quai da mảnh thanh lịch, đế cao su chống trượt. Người bạn của mọi chuyến đi chơi.', 259000, 319000, 90,  true, false, 'StreetVN',  'Da tổng hợp',        'Women', 273, 4.30, 8,  59,  NOW(), NOW()),
-- The Thao (cat 9)
(19, 'Bộ Đồ Tập Gym Nam',          'bo-do-tap-gym-nam',          'BDG-019', 'Set áo + short tập gym nam',                'Combo áo thun và short thể thao nhanh khô, tiết kiệm hơn mua lẻ. Đủ size từ S đến 2XL.', 449000, 549000, 70,  true, true,  'ActiveFit', 'Polyester',          'Men',   395, 4.60, 12, 63,  NOW(), NOW()),
(20, 'Áo Bra Thể Thao Nữ',         'ao-bra-the-thao-nu',         'ABT-020', 'Bra thể thao độ nâng đỡ trung bình',        'Thiết kế ôm thoáng, dây đan lưng cá tính. Phù hợp yoga, gym và chạy bộ nhẹ.', 249000, 299000, 95,  true, false, 'ActiveFit', 'Nylon Spandex',      'Women', 334, 4.40, 11, 72,  NOW(), NOW()),
(21, 'Quần Jogger Unisex',         'quan-jogger-unisex',         'QJU-021', 'Jogger unisex phong cách đường phố',        'Nỉ da cá mềm mịn, bo gấu gọn gàng. Form unisex nam nữ đều mặc đẹp.', 329000, 399000, 120, true, false, 'StreetVN',  'Nỉ Cotton',          'Unisex', 421, 4.50, 16, 89, NOW(), NOW()),
(22, 'Áo Khoác Gió Chạy Bộ',       'ao-khoac-gio-chay-bo',       'AKG-022', 'Khoác gió siêu nhẹ có thể gấp gọn',         'Chống gió chống mưa nhẹ, gấp gọn bằng lòng bàn tay. Phản quang an toàn khi chạy tối.', 499000, 649000, 60,  true, false, 'ActiveFit', 'Polyester Ripstop',  'Unisex', 308, 4.70, 9,  47, NOW(), NOW()),
-- Care & Share (cat 10)
(23, 'Áo Thun Care & Share',       'ao-thun-care-share',         'CAS-023', 'Mỗi chiếc áo góp 10.000đ vào quỹ trẻ em',   'Cotton mềm in hình minh hoạ độc quyền. Toàn bộ lợi nhuận đóng góp cho các hoạt động xã hội hướng tới trẻ em.', 161000, NULL, 300, true, true, 'CareShare', '100% Cotton', 'Unisex', 654, 4.90, 28, 215, NOW(), NOW()),
(24, 'Túi Tote Care & Share',      'tui-tote-care-share',        'CAS-024', 'Túi tote canvas gây quỹ từ thiện',          'Canvas dày dặn đựng được laptop 14 inch. Mua túi là góp quỹ xây thư viện cho trẻ vùng cao.', 99000, NULL, 250, true, false, 'CareShare', 'Canvas', 'Unisex', 387, 4.80, 15, 168, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ==================== PRODUCT <-> CATEGORY ===========================
-- Moi san pham gan voi category con + category cha (de filter theo ca hai)
INSERT INTO product_categories (product_id, category_id) VALUES
(1,3),(1,1),   (2,3),(2,1),   (3,3),(3,1),
(4,4),(4,1),   (5,4),(5,1),   (6,4),(6,1),(6,9),
(7,7),(7,1),   (8,7),(8,1),(8,9),   (9,7),(9,1),
(10,5),(10,2), (11,5),(11,2), (12,5),(12,2),
(13,6),(13,2), (14,6),(14,2), (15,6),(15,2),(15,9),
(16,8),(16,2), (17,8),(17,2), (18,8),(18,2),
(19,9),        (20,9),        (21,9),        (22,9),
(23,10),       (24,10);

-- ============================ SIZES ==================================
INSERT INTO product_sizes (product_id, size) VALUES
(1,'S'),(1,'M'),(1,'L'),(1,'XL'),(1,'2XL'),
(2,'M'),(2,'L'),(2,'XL'),
(3,'S'),(3,'M'),(3,'L'),(3,'XL'),
(4,'29'),(4,'30'),(4,'31'),(4,'32'),(4,'34'),
(5,'29'),(5,'30'),(5,'32'),(5,'34'),
(6,'M'),(6,'L'),(6,'XL'),
(7,'39'),(7,'40'),(7,'41'),(7,'42'),(7,'43'),
(8,'39'),(8,'40'),(8,'41'),(8,'42'),
(9,'39'),(9,'40'),(9,'41'),(9,'42'),(9,'43'),
(10,'S'),(10,'M'),(10,'L'),
(11,'S'),(11,'M'),(11,'L'),(11,'XL'),
(12,'S'),(12,'M'),(12,'L'),
(13,'26'),(13,'27'),(13,'28'),(13,'29'),(13,'30'),
(14,'S'),(14,'M'),(14,'L'),
(15,'S'),(15,'M'),(15,'L'),
(16,'35'),(16,'36'),(16,'37'),(16,'38'),(16,'39'),
(17,'35'),(17,'36'),(17,'37'),(17,'38'),
(18,'35'),(18,'36'),(18,'37'),(18,'38'),(18,'39'),
(19,'M'),(19,'L'),(19,'XL'),(19,'2XL'),
(20,'S'),(20,'M'),(20,'L'),
(21,'S'),(21,'M'),(21,'L'),(21,'XL'),
(22,'M'),(22,'L'),(22,'XL'),
(23,'S'),(23,'M'),(23,'L'),(23,'XL'),(23,'2XL');

-- ============================ COLORS =================================
INSERT INTO product_colors (product_id, color) VALUES
(1,'Đen'),(1,'Trắng'),(1,'Xám'),
(2,'Trắng'),(2,'Xanh Nhạt'),
(3,'Đen'),(3,'Xanh Navy'),(3,'Đỏ Đô'),
(4,'Xanh Đậm'),(4,'Đen'),
(5,'Be'),(5,'Xám Đậm'),
(6,'Đen'),(6,'Xám'),(6,'Xanh Rêu'),
(7,'Trắng'),(7,'Đen'),
(8,'Đen'),(8,'Xanh Dương'),
(9,'Đen'),(9,'Nâu'),
(10,'Hồng Pastel'),(10,'Trắng'),(10,'Vàng Nhạt'),
(11,'Trắng'),(11,'Be'),(11,'Đen'),
(12,'Đen'),(12,'Xanh Rêu'),
(13,'Xanh Nhạt'),(13,'Xanh Đậm'),
(14,'Đen'),(14,'Nâu'),
(15,'Đen'),(15,'Tím Than'),(15,'Nâu Đất'),
(16,'Trắng'),
(17,'Đen'),(17,'Nude'),
(18,'Nâu'),(18,'Đen'),(18,'Trắng'),
(19,'Đen'),(19,'Xám'),
(20,'Đen'),(20,'Hồng'),(20,'Tím Than'),
(21,'Đen'),(21,'Xám'),(21,'Be'),
(22,'Đen'),(22,'Xanh Neon'),
(23,'Trắng'),(23,'Vàng'),
(24,'Be');

-- ============================ IMAGES =================================
-- Anh mau tu picsum.photos (thay bang anh Cloudinary that khi co)
INSERT INTO product_images (id, product_id, image_url, thumbnail_url, cloudinary_public_id, alt_text, is_primary, display_order, folder_path, color, created_at, updated_at)
SELECT
  p.id * 2 - 1, p.id,
  'https://picsum.photos/seed/fashion' || p.id || 'a/600/800',
  'https://picsum.photos/seed/fashion' || p.id || 'a/300/400',
  'seed/product-' || p.id || '-main',
  p.name, true, 0, 'products/seed', NULL, NOW(), NOW()
FROM products p
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_images (id, product_id, image_url, thumbnail_url, cloudinary_public_id, alt_text, is_primary, display_order, folder_path, color, created_at, updated_at)
SELECT
  p.id * 2, p.id,
  'https://picsum.photos/seed/fashion' || p.id || 'b/600/800',
  'https://picsum.photos/seed/fashion' || p.id || 'b/300/400',
  'seed/product-' || p.id || '-alt',
  p.name || ' - góc nhìn khác', false, 1, 'products/seed', NULL, NOW(), NOW()
FROM products p
ON CONFLICT (id) DO NOTHING;

-- ============================ COUPONS ================================
INSERT INTO coupons (id, code, description, discount_type, discount_value, minimum_order_value, maximum_discount_amount, max_usage_count, used_count, max_usage_per_user, start_date, expiry_date, active, notes, created_at, updated_at) VALUES
(1, 'WELCOME10', 'Giảm 10% cho đơn hàng đầu tiên (tối đa 50K)', 'PERCENTAGE',    10,     200000, 50000, 1000, 0, 1,    NOW(), '2027-12-31 23:59:59', true, 'Coupon chào mừng khách mới', NOW(), NOW()),
(2, 'FREESHIP',  'Miễn phí vận chuyển cho đơn từ 300K',          'FREE_SHIPPING', 0,      300000, NULL,  NULL, 0, NULL, NOW(), '2027-12-31 23:59:59', true, NULL, NOW(), NOW()),
(3, 'SALE50K',   'Giảm thẳng 50K cho đơn từ 500K',               'FIXED_AMOUNT',  50000,  500000, NULL,  500,  0, 2,    NOW(), '2027-12-31 23:59:59', true, NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ==================== RESET SEQUENCES ================================
-- Bat buoc sau khi insert ID tuong minh, neu khong app se loi duplicate key
SELECT setval(pg_get_serial_sequence('categories','id'),     (SELECT COALESCE(MAX(id),1) FROM categories));
SELECT setval(pg_get_serial_sequence('products','id'),       (SELECT COALESCE(MAX(id),1) FROM products));
SELECT setval(pg_get_serial_sequence('product_images','id'), (SELECT COALESCE(MAX(id),1) FROM product_images));
SELECT setval(pg_get_serial_sequence('coupons','id'),        (SELECT COALESCE(MAX(id),1) FROM coupons));

-- ==================== KIEM TRA KET QUA ===============================
SELECT 'categories' AS bang, COUNT(*) AS so_dong FROM categories
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'product_images', COUNT(*) FROM product_images
UNION ALL SELECT 'coupons', COUNT(*) FROM coupons;
