/**
 * Marketing copy for the public shell (landing page + header mega-menus).
 *
 * The wording and grouping mirror KiotViet's own site one-for-one, because
 * this app is a KiotViet UI clone built as a portfolio piece: the labels
 * (e-invoicing, business loans, FoodApp/OTA, payroll...) exist for layout
 * fidelity, not as a claim that every one of them is implemented. The brand
 * name shown to visitors is ours (Tryum) - only the layout is borrowed.
 *
 * Header and landing share these constants so the mega-menu can never drift
 * from the sections it scrolls to.
 */

export interface MarketingLink {
  label: string;
  /** Renders the small red "Miễn phí" pill next to the label. */
  free?: boolean;
}

export interface MarketingGroup {
  title: string;
  items: MarketingLink[];
}

/** "Giải pháp" mega-menu columns. */
export const MARKETING_SOLUTIONS: MarketingGroup[] = [
  {
    title: 'Bán hàng',
    items: [
      { label: 'Bán buôn, bán lẻ' },
      { label: 'Ăn uống, giải trí' },
      { label: 'Sức khỏe, làm đẹp' },
      { label: 'Khách sạn, nhà nghỉ' },
    ],
  },
  {
    title: 'Kế toán & Thuế',
    items: [
      { label: 'Kế toán hộ kinh doanh', free: true },
      { label: 'Hoá đơn điện tử', free: true },
      { label: 'Tư vấn thuế' },
    ],
  },
  {
    title: 'Tài chính',
    items: [{ label: 'Giải pháp thanh toán QR' }, { label: 'Giải pháp vay vốn kinh doanh' }],
  },
  {
    title: 'Bán hàng Online',
    items: [
      { label: 'Đồng bộ sàn TMĐT và mạng xã hội' },
      { label: 'Tích hợp FoodApp' },
      { label: 'Tích hợp OTA' },
      { label: 'Tạo website bán hàng' },
      { label: 'Giải pháp giao hàng' },
    ],
  },
  {
    title: 'Quản lý nhân viên',
    items: [
      { label: 'Bảng chấm công' },
      { label: 'Bảng tính lương' },
      { label: 'Lịch làm việc' },
      { label: 'Bảng hoa hồng' },
    ],
  },
];

/** One of the four business families, each with the trades it covers. */
export interface IndustryGroup extends MarketingGroup {
  /**
   * The retail family carries twice as many trades as the others, so it is
   * laid out over two grid columns - exactly how KiotViet splits it.
   */
  wide?: boolean;
}

/** "Ngành hàng" mega-menu / "chuyên biệt cho từng ngành hàng" section. */
export const MARKETING_INDUSTRIES: IndustryGroup[] = [
  {
    title: 'Bán buôn, bán lẻ',
    wide: true,
    items: [
      { label: 'Tạp hóa & Siêu thị' },
      { label: 'Thời trang' },
      { label: 'Điện tử & Điện máy' },
      { label: 'Vật liệu xây dựng' },
      { label: 'Nông sản & Thực phẩm' },
      { label: 'Nhà thuốc' },
      { label: 'Xe & máy móc' },
      { label: 'Mỹ phẩm' },
      { label: 'Nội thất & Gia dụng' },
      { label: 'Mẹ & Bé' },
      { label: 'Sách & Văn phòng phẩm' },
      { label: 'Hoa & Quà tặng' },
      { label: 'Khác' },
    ],
  },
  {
    title: 'Ăn uống, giải trí',
    items: [
      { label: 'Quán ăn' },
      { label: 'Cafe, Trà sữa' },
      { label: 'Karaoke, Bida' },
      { label: 'Bar, Pub & Club' },
      { label: 'Căng tin & Trạm dừng nghỉ' },
      { label: 'Nhà hàng' },
    ],
  },
  {
    title: 'Sức khỏe, làm đẹp',
    items: [
      { label: 'Thẩm mỹ viện' },
      { label: 'Hair Salon' },
      { label: 'Nail & Mi' },
      { label: 'Phòng khám' },
      { label: 'Spa & Massage' },
      { label: 'Gym, Yoga & Pilates' },
    ],
  },
  {
    title: 'Khách sạn, nhà nghỉ',
    items: [
      { label: 'Khách sạn' },
      { label: 'Homestay' },
      { label: 'Nhà nghỉ' },
      { label: 'Nhà trọ' },
      { label: 'Villa' },
      { label: 'Resort' },
      { label: 'Camping/Glamping' },
    ],
  },
];
