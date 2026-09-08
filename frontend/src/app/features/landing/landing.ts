import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MARKETING_INDUSTRIES } from '../../core/marketing/marketing.data';
import { Footer } from '../../layout/footer/footer';

/**
 * Public landing page, laid out section-for-section like KiotViet's homepage
 * (hero carousel -> value props -> the five solution families -> security ->
 * industries -> social proof -> apps -> news -> press -> contact CTA).
 *
 * Every "photo" on the real site is rendered here as a CSS/markup mock of the
 * product screen it shows - the repo ships no stock photography, and a mocked
 * POS screen reads truer than a grey placeholder box would.
 */

/** Which mock product screen a hero slide paints; see landing.html's @switch. */
export type HeroVariant = 'retail' | 'fashion' | 'salon';

export interface HeroSlide {
  variant: HeroVariant;
  /** Tailwind classes for the slide's tinted backdrop. */
  backdrop: string;
}

export interface CoverWidgetRow {
  label: string;
  value?: string;
}

/** The little product-screen card floating on a solution card's cover art. */
export interface CoverWidget {
  title: string;
  rows: CoverWidgetRow[];
  chip?: string;
}

export interface SolutionCard {
  title: string;
  description: string;
  /** Tailwind classes for the card's mock cover image. */
  cover: string;
  widget?: CoverWidget;
}

export interface OnlineChannel {
  title: string;
  description: string;
}

export interface FinanceSolution {
  title: string;
  description: string;
}

export interface Testimonial {
  name: string;
  role: string;
  quote: string;
  /** Tailwind classes for the mock avatar. */
  avatar: string;
}

export interface MobileApp {
  name: string;
  /** Tailwind classes for the mock app icon tile. */
  icon: string;
}

export interface NewsPost {
  title: string;
  cover: string;
}

export interface SupportChannel {
  title: string;
  lines: string[];
}

interface PricingTier {
  name: string;
  price: string;
  period: string;
  description: string;
  features: string[];
  highlighted?: boolean;
}

const HERO_ROTATE_MS = 5000;

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink, Footer],
  templateUrl: './landing.html',
})
export class Landing {
  readonly industries = MARKETING_INDUSTRIES;

  readonly heroSlides: HeroSlide[] = [
    { variant: 'retail', backdrop: 'from-amber-100 to-orange-200' },
    { variant: 'fashion', backdrop: 'from-indigo-50 to-slate-200' },
    { variant: 'salon', backdrop: 'from-emerald-50 to-emerald-100' },
  ];

  /** Index into heroSlides; advances on its own and on dot clicks. */
  readonly heroIndex = signal(0);

  // --- Filler for the mocked product screens inside the hero slides. ---

  readonly posTabs = ['Món chính', 'Thức uống', 'Ăn vặt', 'Tráng miệng', 'Combo'];
  readonly heroInvoices = ['268.000', '1.320.000', '475.000'];
  readonly heroBookings = ['Vũ Thúy Anh', 'Nguyễn Thu Hà', 'Nguyễn Ngọc Diệu Linh'];
  readonly heroServiceCheckins = [
    { name: 'A. Trung', service: 'Cắt tóc', at: '13:45' },
    { name: 'Chị Phương', service: 'Gội đầu', at: '16:20' },
  ];
  readonly heroSalonCheckins = [
    { name: 'Nguyễn Minh Trung', at: '13:45' },
    { name: 'Vũ Phương Anh', at: '16:20' },
    { name: 'Phạm Nam Phương', at: '17:05' },
  ];
  readonly heroHours = ['08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00'];
  /** name + the booked slot range painted on that room's timeline row. */
  readonly heroRooms = [
    { name: 'test 3 (DEL)', from: 1, span: 2, tone: 'bg-emerald-400' },
    { name: 'VIP (DEL)', from: 3, span: 2, tone: 'bg-amber-300' },
    { name: 'P2001', from: 0, span: 3, tone: 'bg-emerald-300' },
    { name: 'P2002', from: 4, span: 2, tone: 'bg-sky-300' },
    { name: 'P2004', from: 2, span: 3, tone: 'bg-orange-300' },
  ];
  /** 6x6 on/off grid standing in for a payment QR code. */
  readonly qrCells = [
    1, 1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 1, 1,
    0, 0, 1, 1,
  ];

  readonly valueProps = [
    {
      title: 'Đơn giản & Dễ sử dụng',
      description: 'Giao diện đơn giản và thông minh. Chỉ mất 15 phút làm quen.',
      tint: 'from-pink-100 to-rose-200',
    },
    {
      title: 'Tiết kiệm chi phí',
      description: 'Miễn phí cài đặt, triển khai, nâng cấp và hỗ trợ.',
      tint: 'from-amber-100 to-orange-200',
    },
    {
      title: 'Phù hợp từng ngành hàng',
      description: 'Phù hợp cho hơn 20 ngành nghề kinh doanh khác nhau.',
      tint: 'from-sky-100 to-blue-200',
    },
  ];

  readonly salesSolutions: SolutionCard[] = [
    {
      title: 'Bán buôn, bán lẻ',
      description:
        'Tối ưu quy trình bán hàng cả online và offline, tiết kiệm chi phí và thời gian quản lý vận hành',
      cover: 'from-sky-200 via-blue-100 to-indigo-200',
    },
    {
      title: 'Ăn uống, giải trí',
      description:
        'Order chính xác, vận hành tối ưu. Phần mềm duy nhất tích hợp tất cả Foodapp liền mạch',
      cover: 'from-amber-200 via-orange-100 to-rose-200',
    },
    {
      title: 'Sức khỏe, làm đẹp',
      description:
        'Quản lý lịch hẹn, hồ sơ khách hàng và liệu trình một cách chuyên nghiệp và hiệu quả',
      cover: 'from-fuchsia-200 via-purple-100 to-violet-200',
    },
    {
      title: 'Khách sạn, nhà nghỉ',
      description: 'Đặt lịch dễ dàng, tiết kiệm 200% thời gian lễ tân, tích hợp Agoda, Booking,..',
      cover: 'from-emerald-200 via-teal-100 to-cyan-200',
    },
  ];

  readonly accountingSolutions: SolutionCard[] = [
    {
      title: 'Kế toán hộ kinh doanh',
      description: 'Miễn phí giải pháp kế toán hộ kinh doanh',
      cover: 'from-blue-100 to-indigo-100',
      widget: {
        title: 'Báo cáo kế toán',
        rows: [
          { label: 'Doanh thu', value: '12.500.000 đ' },
          { label: 'Chi phí', value: '8.200.000 đ' },
          { label: 'Lợi nhuận', value: '4.300.000 đ' },
          { label: 'Thuế GTGT', value: '1.250.000 đ' },
        ],
      },
    },
    {
      title: 'Hoá đơn điện tử',
      description: 'Miễn phí giải pháp xuất Hoá đơn điện tử từ máy tính tiền và chữ ký số',
      cover: 'from-sky-100 to-blue-100',
      widget: {
        title: 'Hóa đơn điện tử',
        rows: [
          { label: 'Đã phát hành', value: '75%' },
          { label: 'Chờ ký số', value: '18%' },
          { label: 'Nháp', value: '7%' },
        ],
        chip: 'Xuất hóa đơn thành công',
      },
    },
    {
      title: 'Tư vấn thuế',
      description:
        'Miễn phí hỗ trợ và tư vấn về các vấn đề tuân thủ thuế với hơn 1000 tư vấn viên trên toàn quốc',
      cover: 'from-cyan-100 to-sky-100',
      widget: {
        title: 'Invoice',
        rows: [
          { label: 'Miễn phí hóa đơn', value: '15% to-up' },
          { label: 'Tư vấn viên', value: '1000+' },
          { label: 'Phản hồi', value: '< 15 phút' },
        ],
      },
    },
  ];

  readonly onlineChannels: OnlineChannel[] = [
    {
      title: 'Đồng bộ sàn TMĐT và mạng xã hội',
      description:
        'Tích hợp và bán hàng trên các nền tảng tất cả sàn TMĐT và mạng xã hội như Shopee, Tiktok, Facebook, Zalo,...',
    },
    {
      title: 'Tích hợp FoodApp',
      description:
        'Nhận đơn từ các ứng dụng giao đồ ăn ngay trên phần mềm, không cần mở thêm thiết bị nào khác.',
    },
    {
      title: 'Tích hợp OTA',
      description:
        'Đồng bộ phòng và giá với các kênh đặt phòng trực tuyến, tránh trùng lịch và huỷ phòng ngoài ý muốn.',
    },
    {
      title: 'Tạo website bán hàng',
      description:
        'Mỗi cửa hàng có một storefront riêng, đồng bộ tồn kho và đơn hàng với phần mềm quản lý.',
    },
    {
      title: 'Giải pháp giao hàng',
      description:
        'Kết nối các đối tác vận chuyển, đẩy đơn và theo dõi trạng thái giao hàng ngay trong phần mềm.',
    },
  ];

  /** Which "Bán hàng Online" accordion row is expanded. */
  readonly openChannel = signal(0);

  readonly financeSolutions: FinanceSolution[] = [
    {
      title: 'Giải pháp thanh toán QR',
      description:
        'Tích hợp QR thanh toán, tiền về tức thì, theo dõi giao dịch ngay trên phần mềm, hỗ trợ thiết bị QR loa',
    },
    {
      title: 'Giải pháp vay vốn kinh doanh',
      description:
        'Vay tín chấp nhanh chóng với đa dạng gói phù hợp, kết nối trực tiếp các đối tác uy tín như MB, VPBank',
    },
  ];

  /** Which finance card is highlighted; also picks the illustration beside it. */
  readonly activeFinance = signal(1);

  readonly staffSolutions: SolutionCard[] = [
    {
      title: 'Bảng chấm công',
      description: 'Quản lý chấm công nhân viên chính xác, theo dõi kiểm tra dễ dàng',
      cover: 'from-blue-100 to-sky-100',
      widget: {
        title: 'Chấm công tháng 9',
        rows: [
          { label: 'Ngày công', value: '26/26' },
          { label: 'Đi muộn', value: '1' },
          { label: 'Nghỉ phép', value: '2' },
        ],
      },
    },
    {
      title: 'Bảng tính lương',
      description:
        'Tự động tính lương dựa trên công, phụ cấp, thưởng phạt. Xuất bảng lương chi tiết nhanh chóng',
      cover: 'from-indigo-100 to-blue-100',
      widget: {
        title: 'Bảng lương tháng 9',
        rows: [
          { label: 'Nguyễn Minh Trung' },
          { label: 'Vũ Phương Anh' },
          { label: 'Phạm Nam Phương' },
          { label: 'Đặng Thu Hà' },
        ],
        chip: 'Chốt lương',
      },
    },
    {
      title: 'Lịch làm việc',
      description:
        'Sắp xếp ca làm việc linh hoạt, phân công nhân viên theo chi nhánh, theo dõi nghỉ phép dễ dàng',
      cover: 'from-sky-100 to-cyan-100',
      widget: {
        title: 'Lịch làm việc - Tháng 1',
        rows: [
          { label: 'Ca sáng', value: '5 nhân viên' },
          { label: 'Ca chiều', value: '4 nhân viên' },
          { label: 'Ca tối', value: '3 nhân viên' },
        ],
      },
    },
    {
      title: 'Bảng hoa hồng',
      description:
        'Thiết lập chính sách hoa hồng đa dạng theo doanh số, sản phẩm. Tự động tính và báo cáo minh bạch',
      cover: 'from-violet-100 to-indigo-100',
      widget: {
        title: 'Bảng hoa hồng',
        rows: [{ label: 'Bảng hoa hồng 1' }, { label: 'Bảng hoa hồng 2' }],
        chip: 'Tạo bảng mới',
      },
    },
  ];

  readonly tiers: PricingTier[] = [
    {
      name: 'FREE_TRIAL',
      price: 'Miễn phí',
      period: '14 ngày',
      description: 'Dùng thử toàn bộ tính năng, không cần thẻ thanh toán.',
      features: ['Không giới hạn sản phẩm', 'Không giới hạn nhân viên', 'Toàn bộ tính năng'],
    },
    {
      name: 'BASIC',
      price: '$5',
      period: '/ tháng',
      description: 'Phù hợp cửa hàng nhỏ mới bắt đầu.',
      features: ['Tối đa 50 sản phẩm', '1 nhân viên', 'Quản lý đơn hàng & tồn kho'],
      highlighted: true,
    },
    {
      name: 'PRO',
      price: '$15',
      period: '/ tháng',
      description: 'Cho cửa hàng đang tăng trưởng.',
      features: [
        'Không giới hạn sản phẩm',
        'Không giới hạn nhân viên',
        'Gợi ý sản phẩm bằng AI',
        'Tìm kiếm nâng cao (Elasticsearch)',
      ],
    },
  ];

  /** Tints for the "300.000+ nhà kinh doanh tin dùng" photo mosaic. */
  readonly mosaic = [
    'from-amber-200 to-orange-300',
    'from-rose-200 to-pink-300',
    'from-sky-200 to-blue-300',
    'from-emerald-200 to-teal-300',
    'from-violet-200 to-purple-300',
    'from-cyan-200 to-sky-300',
    'from-orange-200 to-amber-300',
    'from-blue-200 to-indigo-300',
    'from-teal-200 to-emerald-300',
    'from-fuchsia-200 to-rose-300',
  ];

  readonly testimonials: Testimonial[] = [
    {
      name: 'Phương Nguyễn',
      role: 'Chủ cửa hàng',
      quote: 'Tryum ra tính năng rất đúng thời điểm kê khai, rất tuyệt',
      avatar: 'from-amber-300 to-orange-400',
    },
    {
      name: 'Linh Phạm',
      role: 'Chủ cửa hàng',
      quote:
        'Người bán hàng đánh giá cao các tính năng của nó. Phần mềm thân thiện với người dùng và tự động hóa mọi thứ.',
      avatar: 'from-rose-300 to-pink-400',
    },
    {
      name: 'Chị Hoa',
      role: 'Chủ shop thời trang công sở',
      quote:
        'Phần mềm dễ dùng, mọi quy trình đều được tự động hoá. Quản lý cửa hàng nhàn hơn hẳn trước đây.',
      avatar: 'from-sky-300 to-blue-400',
    },
    {
      name: 'Anh Tuấn',
      role: 'Chủ chuỗi cafe',
      quote:
        'Ba chi nhánh dùng chung một tài khoản, doanh thu về đâu tôi cũng xem được ngay trên điện thoại.',
      avatar: 'from-emerald-300 to-teal-400',
    },
  ];

  /** Left-most testimonial in the visible window of three. */
  readonly testimonialIndex = signal(0);

  readonly apps: MobileApp[] = [
    { name: 'Tryum', icon: 'from-sky-400 to-blue-600' },
    { name: 'Tryum Hotel', icon: 'from-blue-500 to-indigo-600' },
    { name: 'Tryum Salon, Spa', icon: 'from-cyan-400 to-sky-600' },
    { name: 'Quản lý nhà hàng', icon: 'from-blue-400 to-cyan-600' },
    { name: 'Nhân viên nhà hàng', icon: 'from-indigo-400 to-blue-600' },
    { name: 'T-note', icon: 'from-teal-400 to-emerald-600' },
  ];

  readonly news: NewsPost[] = [
    {
      title: 'Chủ shop “biết tối ưu” giúp dòng tiền tự động sinh lời như thế nào?',
      cover: 'from-sky-200 via-blue-200 to-indigo-300',
    },
    {
      title: 'Hoàn tiền “hời” - Sinh lời “chất” đến 4.5%/năm khi thanh toán QR',
      cover: 'from-blue-300 via-sky-200 to-cyan-300',
    },
    {
      title: 'Bí quyết tránh thất thoát doanh thu mùa cao điểm',
      cover: 'from-indigo-200 via-blue-200 to-sky-300',
    },
  ];

  readonly press = ['VnExpress', 'Dân Trí', 'Đầu Tư Online', 'Tuổi Trẻ', 'VnEconomy', 'CafeF'];

  readonly supportChannels: SupportChannel[] = [
    {
      title: 'Hotline',
      lines: [
        'Tư vấn bán hàng: 1800 6162',
        'Chăm sóc khách hàng: 1900 6522',
        'Hoạt động 365 ngày/năm từ 7:00 đến 22:00 kể cả ngày nghỉ lễ, tết.',
      ],
    },
    {
      title: 'Tryum Fanpage',
      lines: ['Luôn trả lời các thông tin nhanh nhất thông qua các phản hồi trên Facebook.'],
    },
    {
      title: 'Kênh hỗ trợ Youtube',
      lines: [
        'Luôn cập nhật các kiến thức sử dụng phần mềm tức thời, trực quan giúp người dùng sử dụng được Tryum dễ dàng và hiệu quả nhất.',
      ],
    },
    {
      title: 'Chat trên web & mobile',
      lines: [
        'Luôn có người trực chat để trả lời câu hỏi của các bạn nhanh và hiệu quả nhất suốt 365 ngày/năm.',
      ],
    },
  ];

  constructor() {
    const timer = setInterval(
      () => this.heroIndex.update((i) => (i + 1) % this.heroSlides.length),
      HERO_ROTATE_MS,
    );
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  showHeroSlide(index: number): void {
    this.heroIndex.set(index);
  }

  /**
   * One row is always expanded - collapsing the open one would leave the
   * illustration beside the accordion without a subject, same as on KiotViet.
   */
  openOnlineChannel(index: number): void {
    this.openChannel.set(index);
  }

  selectFinance(index: number): void {
    this.activeFinance.set(index);
  }

  /** Wraps, so the arrows keep working at both ends like the real carousel. */
  moveTestimonials(step: number): void {
    const count = this.testimonials.length;
    this.testimonialIndex.update((i) => (i + step + count) % count);
  }

  /** The three testimonials currently on screen, starting at testimonialIndex. */
  visibleTestimonials(): Testimonial[] {
    const start = this.testimonialIndex();
    return [0, 1, 2].map((offset) => this.testimonials[(start + offset) % this.testimonials.length]);
  }
}
