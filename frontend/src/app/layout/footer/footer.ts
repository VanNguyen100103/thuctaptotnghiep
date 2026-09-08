import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface FooterColumn {
  title: string;
  links: string[];
}

export interface ForeignHotline {
  flag: string;
  country: string;
  number: string;
}

export interface BranchRegion {
  region: string;
  cities: string[];
}

/**
 * Public marketing footer, mirroring KiotViet's: company block + map on top,
 * four link columns under it, then the nationwide branch list.
 *
 * Only the landing page mounts it - the dashboard and each store's storefront
 * have their own chrome and would look wrong under a corporate footer.
 */
@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './footer.html',
})
export class Footer {
  readonly columns: FooterColumn[] = [
    {
      title: 'Doanh nghiệp',
      links: [
        'Về Tryum',
        'Khách hàng',
        'Điều khoản & chính sách sử dụng',
        'Liên hệ',
        'Tuyển dụng Tryum',
      ],
    },
    {
      title: 'Hỗ trợ',
      links: [
        'Video hướng dẫn sử dụng',
        'Câu hỏi thường gặp',
        'Wiki Tryum',
        'Hướng dẫn sử dụng',
        'Blog',
      ],
    },
  ];

  readonly hotlines = [
    { label: 'Tư vấn bán hàng', number: '1800 6162' },
    { label: 'Chăm sóc khách hàng', number: '1900 6522' },
  ];

  readonly foreignHotlines: ForeignHotline[] = [
    { flag: '🇻🇳', country: 'Việt Nam', number: '(+84) 24 3990 4991' },
    { flag: '🇯🇵', country: 'Nhật Bản', number: '(+81) 50 5050 8167' },
    { flag: '🇱🇦', country: 'Lào', number: '(+856) 20 9991 5707' },
    { flag: '🇰🇭', country: 'Campuchia', number: '(+855) 23 962 610' },
  ];

  readonly branches: BranchRegion[] = [
    {
      region: 'Miền Bắc',
      cities: [
        'Hà Nội',
        'Nam Định',
        'Hải Phòng',
        'Quảng Ninh',
        'Phú Thọ',
        'Hải Dương',
        'Bắc Ninh',
        'Thái Nguyên',
      ],
    },
    {
      region: 'Miền Trung',
      cities: [
        'Đà Nẵng',
        'Thanh Hóa',
        'Lâm Đồng',
        'Khánh Hòa',
        'Huế',
        'Bình Thuận',
        'Nghệ An',
        'Bình Định',
        'Quảng Ngãi',
        'Đắk Lắk',
        'Gia Lai',
      ],
    },
    {
      region: 'Miền Nam',
      cities: ['TP. Hồ Chí Minh', 'Cần Thơ', 'Vũng Tàu', 'Kiên Giang', 'Đồng Tháp'],
    },
  ];
}
