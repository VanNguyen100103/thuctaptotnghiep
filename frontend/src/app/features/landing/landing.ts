import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface PricingTier {
  name: string;
  price: string;
  period: string;
  description: string;
  features: string[];
  highlighted?: boolean;
}

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './landing.html',
})
export class Landing {
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
}
