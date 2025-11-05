/**
 * Care & Share Page - Exact replica of Coolmate's Care & Share page
 * https://www.coolmate.me/collection/care-and-share
 * Strategy: ISR (Incremental Static Regeneration)
 * Revalidate: 30 minutes
 */

import Link from 'next/link';
import { searchProductsWithFilters } from '@/lib/api/products';
import { getAllActiveCategories } from '@/lib/api/categories';
import CareAndShareCarousel from '@/components/care-and-share/carousel';
import ProductCard from '@/components/product/ProductCard';

// ISR: Revalidate every 30 minutes
export const revalidate = 1800;

export default async function CareAndSharePage() {
  // Fetch all categories to find Care & Share category
  const allCategories = await getAllActiveCategories();

  // Find Care & Share category by slug
  const careShareCategory = allCategories.find(cat => cat.slug === 'care-and-share');

  // Fetch Care & Share products
  const careShareProducts = careShareCategory
    ? await searchProductsWithFilters({
        categoryId: careShareCategory.id,
        page: 0,
        size: 20,
        sort: 'createdAt,desc'
      })
    : { content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 };

  return (
    <div className="min-h-screen bg-white">
      {/* Hero Carousel */}
      <CareAndShareCarousel />
        
      {/* Hero Section - About Care & Share */}
      <section className="py-10 lg:px-16 lg:py-20 px-3 md:pb-0">
        <h1 className="text-center text-3xl font-medium uppercase leading-10 text-gray-900 lg:text-4xl lg:leading-tight">
          Về Care &amp; Share<br className="lg:hidden" /> by Coolmate
        </h1>
        <p className="mt-5 text-center text-base font-normal leading-6 text-gray-900/70 lg:mt-6">
          Care &amp; Share là dự án CSR được xây dựng và phát triển bởi Coolmate, trong đó hoạt động trọng tâm là bán hàng gây quỹ và<br className="hidden lg:block" /> sử dụng quỹ để tổ chức các hoạt động xã hội trọng tâm hướng tới trẻ em
        </p>
        <div className="mt-6 flex justify-center lg:mt-10">
          <img
            src="https://n7media.coolmate.me/uploads/March2025/mceclip0_88.png"
            alt="Care & Share Campaign Banner"
            className="w-full object-cover lg:max-w-[670px]"
          />
        </div>
      </section>

          
      {/* Campaign Progress Trackers */}
      <section className="py-8 bg-gray-50">
        <section className="px-4 py-10 lg:px-16 lg:py-20 grid gap-5 md:gap-10 lg:grid-cols-2 lg:pb-0">
  {/* Card 1 */}
  <div className="flex flex-col justify-between rounded-xl bg-neutral-50 px-6 py-6 md:px-10">
    <div>
      <img 
        alt="logo care and share" 
        src="https://n7media.coolmate.me/uploads/September2025/Frame_87834.png" 
        className="h-10 w-auto object-contain lg:h-8"
        loading="lazy"
        decoding="async"
      />
      <h2 className="mt-4 font-criteria text-3xl font-semibold uppercase leading-tight text-neutral-900 md:text-[44px]">
        Kỷ yếu trên bản
      </h2>
      <p className="mt-4 font-sans text-base leading-6 text-neutral-900 md:text-lg md:leading-6">
        C&S cùng Kỷ yếu trên bản gây quỹ để mang lại cho các em học sinh vùng cao những bức ảnh kỷ yếu đầy ý nghĩa
      </p>
    </div>
    <div className="mt-2 flex items-center gap-x-4 md:mt-4">
      <div className="relative h-2 flex-1 rounded-full bg-gray-200">
        <div className="h-full rounded-full bg-orange-500" style={{ width: '72%' }}></div>
      </div>
      <p className="font-sans text-lg font-medium leading-6 text-orange-500">
        72<span className="align-super text-sm">%</span>
      </p>
    </div>
    <div className="mt-1 flex items-center justify-between md:mt-2">
      <div>
        <p className="font-sans text-sm leading-5 text-neutral-900/60">05/09/2025</p>
        <p className="font-sans text-lg font-semibold leading-9 text-blue-600 md:text-2xl">179.931.591đ</p>
      </div>
      <div className="text-end">
        <p className="font-sans text-sm leading-5 text-neutral-900/60">31/10/2025</p>
        <p className="font-sans text-lg font-semibold leading-9 text-orange-500 md:text-2xl">250.000.000đ</p>
      </div>
    </div>
  </div>

  {/* Card 2 */}
  <div className="flex flex-col justify-between rounded-xl bg-neutral-50 px-6 py-6 md:px-10">
    <div>
      <h2 className="mt-4 font-criteria text-3xl font-semibold uppercase leading-tight text-neutral-900 md:text-[44px]">
        Cùng miền Trung vững vàng
      </h2>
      <p className="mt-4 font-sans text-base leading-6 text-neutral-900 md:text-lg md:leading-6">
        Toàn bộ doanh thu từ BST được trích gửi qua tài khoản MTTQ gửi bà con miền Trung khắc phục cuộc sống sau mưa lũ.
      </p>
    </div>
    <div className="mt-2 flex items-center gap-x-4 md:mt-4">
      <div className="relative h-2 flex-1 rounded-full bg-gray-200">
        <div className="h-full rounded-full bg-red-500" style={{ width: '1%' }}></div>
      </div>
      <p className="font-sans text-lg font-medium leading-6 text-red-500">
        1<span className="align-super text-sm">%</span>
      </p>
    </div>
    <div className="mt-1 flex items-center justify-between md:mt-2">
      <div>
        <p className="font-sans text-sm leading-5 text-neutral-900/60">01/11/2025</p>
        <p className="font-sans text-lg font-semibold leading-9 text-blue-600 md:text-2xl">1.343.000đ</p>
      </div>
      <div className="text-end">
        <p className="font-sans text-sm leading-5 text-neutral-900/60">08/11/2025</p>
        <p className="font-sans text-lg font-semibold leading-9 text-red-500 md:text-2xl">184.000.000đ</p>
      </div>
    </div>
  </div>
        </section>
      </section>

      {/* Product Collection Section */}
      <section className="py-12 bg-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <h2 className="uppercase text-2xl md:text-3xl font-bold text-gray-900 mb-8 text-center">
            sản phẩm care & share
          </h2>

          {/* Product Grid - Real Products */}
          {careShareProducts.content.length > 0 ? (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-6 mb-8">
              {careShareProducts.content.map((product) => (
                <ProductCard key={product.id} product={product} badge={null} />
              ))}
            </div>
          ) : (
            <div className="text-center py-12">
              <p className="text-gray-500 text-lg">
                Chưa có sản phẩm Care & Share nào.
              </p>
              <p className="text-gray-400 text-sm mt-2">
                Vui lòng quay lại sau để xem các sản phẩm mới nhất.
              </p>
            </div>
          )}

          {/* Total Products Count */}
          {careShareProducts.totalElements > 0 && (
            <div className="text-center mt-8">
              <p className="text-gray-600">
                Hiển thị {careShareProducts.content.length} trên tổng số {careShareProducts.totalElements} sản phẩm
              </p>
            </div>
          )}
        </div>
      </section>

      <section className="px-4 py-10 lg:px-16 lg:py-20 pt-5">
  <div className="grid grid-cols-1 lg:grid-cols-2 lg:gap-x-10">
    {/* Left Content */}
    <div>
      <h2 className="font-criteria text-4xl font-semibold italic !leading-tight text-neutral-900 lg:text-6xl">
        &ldquo;Một công ty <span className="text-primary">không cần phải lớn</span> mới làm được điều ý nghĩa.&rdquo;
      </h2>
      <p className="mt-5 text-base leading-6 text-neutral-700 md:text-lg">
        Coolmate đã nghĩ và tin như thế khi khởi xướng chương trình Care & Share này. Sức nhỏ làm việc nhỏ, ít nhất chúng ta đã bắt tay vào lan toả điều tích cực.
      </p>
      
      <ul className="mt-8 grid grid-cols-2 gap-x-4 gap-y-6 font-sans lg:mt-10 lg:gap-x-8 lg:gap-y-8">
        {/* Stat 1 */}
        <li>
          <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl bg-[#273BCD33] p-3 lg:h-16 lg:w-16">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <g clipPath="url(#clip0_2134_5078)">
                <g clipPath="url(#clip1_2134_5078)">
                  <path fillRule="evenodd" clipRule="evenodd" d="M6.72258 5.91292C8.07158 5.31239 9.52574 5 11 5C12.4744 5 13.9285 5.31239 15.2775 5.91292C16.6258 6.51314 17.837 7.38668 18.8478 8.47311C19.4644 9.13581 20.5356 9.13581 21.1522 8.47311C23.1958 6.27679 26.0136 5.00002 29 5.00002C31.9864 5.00002 34.8042 6.27679 36.8478 8.47311C38.8854 10.6629 40 13.5945 40 16.6151C40 19.6358 38.8854 22.5674 36.8478 24.7571L24.4514 38.0798C22.0692 40.6401 17.9308 40.6401 15.5486 38.0798L3.15226 24.7571C2.14172 23.6712 1.34948 22.3922 0.81204 20.9978C0.27462 19.6034 0 18.1147 0 16.6151C0 15.1156 0.27462 13.6269 0.81204 12.2325C1.34948 10.8381 2.14172 9.55914 3.15226 8.47311C4.16314 7.38668 5.37432 6.51314 6.72258 5.91292Z" fill="#273BCD"></path>
                </g>
              </g>
              <defs>
                <clipPath id="clip0_2134_5078">
                  <rect width="40" height="40" fill="white"></rect>
                </clipPath>
                <clipPath id="clip1_2134_5078">
                  <rect width="40" height="40" fill="white"></rect>
                </clipPath>
              </defs>
            </svg>
          </div>
          <p className="mb-2 mt-3 text-lg font-semibold leading-6 text-primary lg:text-2xl">61.401 khách hàng</p>
          <p className="text-sm leading-5 text-neutral-800 lg:text-base">Số khách hàng đồng hành cùng dự án</p>
        </li>

        {/* Stat 2 */}
        <li>
          <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl bg-[#273BCD33] p-3 lg:h-16 lg:w-16">
            <svg width="39" height="39" viewBox="0 0 39 39" fill="none" xmlns="http://www.w3.org/2000/svg">
              <g clipPath="url(#clip0_2134_3445)">
                <path d="M21.1961 26.2827H16.957V39.0001H21.1961V26.2827Z" fill="#273BCD"></path>
                <path d="M38.5753 26.2827H23.7383V28.8262H38.5753V26.2827Z" fill="#273BCD"></path>
                <path d="M38.5753 31.3696H23.7383V33.9131H38.5753V31.3696Z" fill="#273BCD"></path>
                <path d="M23.7383 36.4566V39.0001H37.3035C38.0058 39.0001 38.5753 38.4307 38.5753 37.7283V36.4565H23.7383V36.4566Z" fill="#273BCD"></path>
                <path d="M0.423828 36.4565V37.7283C0.423828 38.4306 0.993213 39.0001 1.69559 39.0001H14.413V36.4566L0.423828 36.4565Z" fill="#273BCD"></path>
                <path d="M14.4129 31.3696H0.423828V33.9131H14.4129V31.3696Z" fill="#273BCD"></path>
                <path d="M14.4129 26.2827H0.423828V28.8262H14.4129V26.2827Z" fill="#273BCD"></path>
                <path d="M37.3043 0H1.69559C0.993518 0 0.423828 0.569232 0.423828 1.27177V22.4674C0.423828 23.17 0.993518 23.7392 1.69559 23.7392H37.3043C38.0064 23.7392 38.576 23.17 38.576 22.4674V1.27177C38.576 0.569232 38.0064 0 37.3043 0ZM14.4129 7.63044H10.38C9.99582 8.713 9.13644 9.57237 8.05427 9.9562V12.9352C9.13644 13.319 9.99582 14.1784 10.38 15.261H14.4129V17.8044H9.32596C8.62388 17.8044 8.05419 17.2352 8.05419 16.5326C8.05419 15.8314 7.48367 15.2609 6.78243 15.2609C6.08035 15.2609 5.51066 14.6916 5.51066 13.9891V8.90213C5.51066 8.1996 6.08035 7.63036 6.78243 7.63036C7.48367 7.63036 8.05419 7.05984 8.05419 6.3586C8.05419 5.65607 8.62388 5.08683 9.32596 5.08683H14.4129V7.63044ZM21.1956 21.1957H16.9565V2.54345H21.1956V21.1957ZM33.4891 13.9891C33.4891 14.6916 32.9194 15.2609 32.2173 15.2609C31.516 15.2609 30.9455 15.8314 30.9455 16.5326C30.9455 17.2352 30.3758 17.8044 29.6738 17.8044H23.739V15.261H28.6198C29.0039 14.1784 29.8633 13.319 30.9455 12.9352V9.9562C29.8633 9.57244 29.004 8.713 28.6198 7.63044H23.739V5.08706H29.6738C30.3758 5.08706 30.9455 5.6563 30.9455 6.35883C30.9455 7.06007 31.516 7.63059 32.2173 7.63059C32.9194 7.63059 33.4891 8.19983 33.4891 8.90236V13.9891Z" fill="#273BCD"></path>
              </g>
              <defs>
                <clipPath id="clip0_2134_3445">
                  <rect width="39" height="39" fill="white"></rect>
                </clipPath>
              </defs>
            </svg>
          </div>
          <p className="mb-2 mt-3 text-lg font-semibold leading-6 text-primary lg:text-2xl">Hơn 2 tỷ đồng</p>
          <p className="text-sm leading-5 text-neutral-800 lg:text-base">Là số tiền đã góp được từ ngày thành lập</p>
        </li>

        {/* Stat 3 */}
        <li>
          <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl bg-[#273BCD33] p-3 lg:h-16 lg:w-16">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M21.1876 8.56023V0H18.8113V8.56023L9.76172 15.5001V40H30.237V15.5001L21.1876 8.56023ZM18.8112 14.931V17.1699H21.1875V14.931C21.9883 15.3577 22.5348 16.2011 22.5348 17.1699C22.5348 18.5679 21.3974 19.7053 19.9994 19.7053C18.6013 19.7053 17.464 18.568 17.464 17.1699C17.4639 16.201 18.0105 15.3577 18.8112 14.931ZM14.877 23.802H25.1216V26.1784H14.877V23.802ZM14.877 27.7629H25.1216V30.1392H14.877V27.7629ZM14.877 34.1V31.7237H25.1216V34.1H14.877Z" fill="#273BCD"></path>
            </svg>
          </div>
          <p className="mb-2 mt-3 text-lg font-semibold leading-6 text-primary lg:text-2xl">114.877 sản phẩm</p>
          <p className="text-sm leading-5 text-neutral-800 lg:text-base">Số lượng sản phẩm Care & Share đã bán ra</p>
        </li>

        {/* Stat 4 */}
        <li>
          <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl bg-[#273BCD33] p-3 lg:h-16 lg:w-16">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <g clipPath="url(#clip0_2134_5078)">
                <g clipPath="url(#clip1_2134_5078)">
                  <path fillRule="evenodd" clipRule="evenodd" d="M6.72258 5.91292C8.07158 5.31239 9.52574 5 11 5C12.4744 5 13.9285 5.31239 15.2775 5.91292C16.6258 6.51314 17.837 7.38668 18.8478 8.47311C19.4644 9.13581 20.5356 9.13581 21.1522 8.47311C23.1958 6.27679 26.0136 5.00002 29 5.00002C31.9864 5.00002 34.8042 6.27679 36.8478 8.47311C38.8854 10.6629 40 13.5945 40 16.6151C40 19.6358 38.8854 22.5674 36.8478 24.7571L24.4514 38.0798C22.0692 40.6401 17.9308 40.6401 15.5486 38.0798L3.15226 24.7571C2.14172 23.6712 1.34948 22.3922 0.81204 20.9978C0.27462 19.6034 0 18.1147 0 16.6151C0 15.1156 0.27462 13.6269 0.81204 12.2325C1.34948 10.8381 2.14172 9.55914 3.15226 8.47311C4.16314 7.38668 5.37432 6.51314 6.72258 5.91292Z" fill="#273BCD"></path>
                </g>
              </g>
              <defs>
                <clipPath id="clip0_2134_5078">
                  <rect width="40" height="40" fill="white"></rect>
                </clipPath>
                <clipPath id="clip1_2134_5078">
                  <rect width="40" height="40" fill="white"></rect>
                </clipPath>
              </defs>
            </svg>
          </div>
          <p className="mb-2 mt-3 text-lg font-semibold leading-6 text-primary lg:text-2xl">13 Dự án</p>
          <p className="text-sm leading-5 text-neutral-800 lg:text-base">Số dự án đã thực hiện được</p>
        </li>
      </ul>
    </div>

    {/* Right Content - Carousel */}
    <div className="relative mt-8">
      <div className="relative w-full" role="region" aria-roledescription="carousel">
        <div className="overflow-hidden">
          <div className="flex -ml-4" style={{ transform: 'translate3d(-1246.57px, 0px, 0px)' }}>
            <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full cursor-pointer pl-4" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
              <img 
                alt="care and share image 1" 
                src="https://n7media.coolmate.me/uploads/September2025/mceclip0_100.jpg" 
                className="aspect-[654/600] w-full rounded-2xl object-cover"
                loading="lazy"
                decoding="async"
              />
            </div>
            <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full cursor-pointer pl-4">
              <img 
                alt="care and share image 2" 
                src="https://n7media.coolmate.me/uploads/September2025/mceclip1_93.jpg" 
                className="aspect-[654/600] w-full rounded-2xl object-cover"
                loading="lazy"
                decoding="async"
              />
            </div>
            <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full cursor-pointer pl-4">
              <img 
                alt="care and share image 3" 
                src="https://n7media.coolmate.me/uploads/September2025/mceclip3_29.jpg" 
                className="aspect-[654/600] w-full rounded-2xl object-cover"
                loading="lazy"
                decoding="async"
              />
            </div>
          </div>
        </div>
        <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 transform gap-x-2">
          <button className="h-2 w-2 rounded-full border border-white bg-transparent"></button>
          <button className="h-2 w-2 rounded-full border border-white bg-transparent"></button>
          <button className="h-2 w-2 rounded-full border border-white bg-white"></button>
        </div>
      </div>
    </div>
  </div>

  {/* Bottom Section */}
  <div className="mt-10 grid max-md:gap-y-4 md:mt-20 lg:grid-cols-3 lg:gap-x-8 2xl:mt-13">
    {/* Left Card */}
    <div className="mt-4 flex flex-col justify-between rounded-2xl bg-[linear-gradient(119.73deg,#132294_0%,#273BCD_57.71%)] p-6 text-white md:p-8 lg:mt-0 2xl:p-14">
      <div className="flex h-full flex-col justify-between gap-y-6 2xl:h-auto">
        <h3 className="font-criteria text-3xl font-semibold !leading-tight md:text-3xl 2xl:text-[44px]">Care & Share 2024 đã làm được gì?</h3>
        <div className="2xl:mt-10">
          <div className="flex items-center gap-x-4">
            <img 
              alt="Care & Share 2024 đã làm được gì?" 
              src="https://n7media.coolmate.me/uploads/August2025/Frame_87793.png" 
              className="w-auto object-contain"
              loading="lazy"
              decoding="async"
            />
          </div>
          <p className="mt-4 text-base md:mt-6 md:text-lg 2xl:text-xl">
            Hoàn thành dự án đồng hành cùng tổ chức phẫu thuật nụ cười Operation Smile tài trợ kinh phí  <strong>550.000.000 VNĐ</strong> cho <strong>55 ca phẫu thuật hàm ếch.</strong>
          </p>
        </div>
      </div>
    </div>

    {/* Right Carousel */}
    <div className="relative w-full lg:col-span-2" role="region" aria-roledescription="carousel">
      <div className="overflow-hidden">
        <div className="flex ml-0" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
          {/* Slide 1 */}
          <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full cursor-pointer pl-2 first-of-type:pl-0 last-of-type:pr-2" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
            <div className="grid grid-cols-2 gap-x-2">
              <img 
                alt="Care & Share 2024 đã làm được gì?" 
                src="https://n7media.coolmate.me/uploads/August2025/Frame_87862.jpg" 
                className="h-40 w-full rounded-lg object-cover md:h-full md:rounded-3xl"
                loading="lazy"
                decoding="async"
              />
              <img 
                alt="Care & Share 2024 đã làm được gì?" 
                src="https://n7media.coolmate.me/uploads/August2025/Frame_87863.jpg" 
                className="h-40 w-full rounded-lg object-cover md:h-full md:rounded-3xl"
                loading="lazy"
                decoding="async"
              />
            </div>
          </div>

          {/* Slide 2 */}
          <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full cursor-pointer pl-2 first-of-type:pl-0 last-of-type:pr-2">
            <div className="grid grid-cols-2 gap-x-2">
              <img 
                alt="Care & Share 2024 đã làm được gì?" 
                src="https://n7media.coolmate.me/uploads/August2025/Frame_87879.jpg" 
                className="h-40 w-full rounded-lg object-cover md:h-full md:rounded-3xl"
                loading="lazy"
                decoding="async"
              />
              <img 
                alt="Care & Share 2024 đã làm được gì?" 
                src="https://n7media.coolmate.me/uploads/August2025/Frame_87880.jpg" 
                className="h-40 w-full rounded-lg object-cover md:h-full md:rounded-3xl"
                loading="lazy"
                decoding="async"
              />
            </div>
          </div>

          {/* Slide 3 */}
          <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full cursor-pointer pl-2 first-of-type:pl-0 last-of-type:pr-2">
            <div className="grid grid-cols-2 gap-x-2">
              <img 
                alt="Care & Share 2024 đã làm được gì?" 
                src="https://n7media.coolmate.me/uploads/August2025/Frame_87864.jpg" 
                className="h-40 w-full rounded-lg object-cover md:h-full md:rounded-3xl"
                loading="lazy"
                decoding="async"
              />
              <img 
                alt="Care & Share 2024 đã làm được gì?" 
                src="https://n7media.coolmate.me/uploads/August2025/Frame_87871.jpg" 
                className="h-40 w-full rounded-lg object-cover md:h-full md:rounded-3xl"
                loading="lazy"
                decoding="async"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Carousel Buttons */}
      <button className="inline-flex items-center justify-center gap-2 whitespace-nowrap focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-secondary-foreground transition-all align-text-bottom bg-neutral-900 hover:bg-neutral-800 text-secondary-foreground active:bg-neutral-700 rounded-full absolute -left-3 top-1/2 h-7 w-7 -translate-y-1/2">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-arrow-left h-4 w-4">
          <path d="m12 19-7-7 7-7"></path>
          <path d="M19 12H5"></path>
        </svg>
        <span className="sr-only">Previous slide</span>
      </button>
      <button className="inline-flex items-center justify-center gap-2 whitespace-nowrap focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-secondary-foreground transition-all align-text-bottom bg-neutral-900 hover:bg-neutral-800 text-secondary-foreground active:bg-neutral-700 rounded-full absolute -right-3 top-1/2 h-7 w-7 -translate-y-1/2">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-arrow-right h-4 w-4">
          <path d="M5 12h14"></path>
          <path d="m12 5 7 7-7 7"></path>
        </svg>
        <span className="sr-only">Next slide</span>
      </button>
    </div>
  </div>
</section>
<section className="px-4 py-10 lg:px-16 lg:py-20 !py-0">
  <div className="relative grid grid-cols-1 items-center gap-8 rounded-xl bg-neutral-800 px-5 py-10 text-white md:grid-cols-12 md:rounded-3xl md:px-15 md:py-20">
    <img 
      alt="Operation Smile" 
      src="https://n7media.coolmate.me/uploads/August2025/mceclip0_86.png" 
      className="absolute left-0 top-0 h-full w-auto object-contain"
      loading="lazy"
      decoding="async"
    />
    
    <div className="col-span-1 space-y-4 md:col-span-5">
      <div className="font-criteria text-7xl font-extrabold">2023</div>
      <div className="font-criteria text-3xl font-semibold uppercase">
        ĐÁNH DẤU SỰ HỢP TÁC GIỮA C&S VỚI OPERATION SMILE
      </div>
      <div>
        <strong className="font-criteria font-semibold">550.000.000đ</strong> tài trợ 100% tiền kinh phí khám và điều trị cho gói 55 em nhỏ
      </div>
    </div>
    
    <div className="col-span-1 grid h-fit grid-cols-2 items-center gap-8 md:col-span-7">
      <div className="flex h-full items-center justify-center rounded-2xl bg-white px-5 py-4 md:px-17 md:py-9">
        <img 
          alt="Operation Smile" 
          src="https://n7media.coolmate.me/uploads/August2025/mceclip2_71.png" 
          className="w-auto object-contain"
          loading="lazy"
          decoding="async"
        />
      </div>
      <div className="flex h-full items-center justify-center rounded-2xl bg-white px-5 py-4 md:px-17 md:py-9">
        <img 
          alt="Operation Smile" 
          src="https://n7media.coolmate.me/uploads/August2025/mceclip4_33.png" 
          className="w-auto object-contain"
          loading="lazy"
          decoding="async"
        />
      </div>
    </div>
  </div>
</section>
<section className="px-4 py-10 lg:px-16 lg:py-20 pt-0 max-md:mt-10">
  <div className="flex items-center justify-center gap-6 lg:mb-8">
    <h2 className="text-center font-criteria text-xl font-bold uppercase leading-8 text-neutral-900 md:text-2xl lg:text-4xl lg:leading-13">
      Khách hàng nói về
    </h2>
    <img 
      alt="icon play" 
      src="https://n7media.coolmate.me/uploads/August2025/mceclip2_71.png" 
      className="h-9 w-auto md:h-15"
      loading="lazy"
      decoding="async"
    />
  </div>
  
  <div className="relative mt-4 w-full" role="region" aria-roledescription="carousel">
    <div className="overflow-hidden">
      <div className="flex -ml-4 pl-0 max-md:ml-0" style={{ transform: 'translate3d(-16px, 0px, 0px)' }}>
        {/* Slide 1 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer group ml-2 flex basis-full flex-col rounded-2xl border pl-0 first-of-type:ml-4 md:!flex-shrink md:basis-1/3 lg:ml-4" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
          <a target="_blank" rel="noreferrer noopener" className="relative overflow-hidden rounded-2xl" href="https://www.youtube.com/watch?v=-YkJSkACxbU&amp;t=721s&amp;ab_channel=T%C3%A2nM%E1%BB%99tC%C3%BA">
            <img 
              alt="Tân Một Cú" 
              src="https://n7media.coolmate.me/uploads/August2025/mceclip6_95.jpg" 
              className="w-full rounded-2xl object-cover transition-all duration-300 group-hover:scale-105 max-lg:h-[152px]"
              loading="lazy"
              decoding="async"
            />
          </a>
          <div className="mt-4 flex flex-1 flex-col p-4">
            <img 
            className='w-[28px] h-[22px]'
              alt="Quote" 
              src="https://n7media.coolmate.me/uploads/August2025/mceclip3_44.png" 
              loading="lazy"
              decoding="async"
            />
            <p className="mt-3 flex-1 text-base leading-5 text-neutral-700 lg:leading-6">
              &ldquo;Sự chia sẻ đối với mình nó chỉ tốt khi nó chỉ là để chia sẻ và không mong nhận lại bất kì điều gì khác. Giống như chiến dịch Care &amp; Share của Coolmate.&rdquo;
            </p>
            <p className="mt-3 text-right text-xs font-medium leading-3.5 text-primary lg:text-xl lg:leading-6">
              -Tân Một Cú
            </p>
          </div>
        </div>

        {/* Slide 2 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer group ml-2 flex basis-full flex-col rounded-2xl border pl-0 first-of-type:ml-4 md:!flex-shrink md:basis-1/3 lg:ml-4">
          <a target="_blank" rel="noreferrer noopener" className="relative overflow-hidden rounded-2xl" href="https://www.youtube.com/watch?v=Ltq0dkiG0QE&amp;t=16s&amp;ab_channel=Nguy%E1%BB%85nH%E1%BB%AFuTr%C3%AD">
            <img 
              alt="Nguyễn Hữu Trí" 
              src="https://n7media.coolmate.me/uploads/August2025/mceclip7_9.jpg" 
              className="w-full rounded-2xl object-cover transition-all duration-300 group-hover:scale-105 max-lg:h-[152px]"
              loading="lazy"
              decoding="async"
            />
          </a>
          <div className="mt-4 flex flex-1 flex-col p-4">
            <img 
              className='w-[28px] h-[22px]'
              alt="Quote" 
              src="https://n7media.coolmate.me/uploads/August2025/mceclip3_44.png" 
              loading="lazy"
              decoding="async"
            />
            <p className="mt-3 flex-1 text-base leading-5 text-neutral-700 lg:leading-6">
              &ldquo;Bên cạnh trải nghiệm về quần áo hay phong cách tối giản, Coolmate còn lan tỏa niềm tự hào, niềm vui chân thật của một thế hệ Việt Nam tâm huyết.&rdquo;
            </p>
            <p className="mt-3 text-right text-xs font-medium leading-3.5 text-primary lg:text-xl lg:leading-6">
              -Nguyễn Hữu Trí
            </p>
          </div>
        </div>

        {/* Slide 3 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer group ml-2 flex basis-full flex-col rounded-2xl border pl-0 first-of-type:ml-4 md:!flex-shrink md:basis-1/3 lg:ml-4">
          <a target="_blank" rel="noreferrer noopener" className="relative overflow-hidden rounded-2xl" href="https://www.youtube.com/watch?v=rZSTBOBL3Dk&amp;ab_channel=V%C5%A9L%C3%AATrangAnh">
            <img 
            
              alt="Vũ Lê Trang Anh" 
              src="https://n7media.coolmate.me/uploads/August2025/mceclip8_94.jpg" 
              className="w-full rounded-2xl object-cover transition-all duration-300 group-hover:scale-105 max-lg:h-[152px]"
              loading="lazy"
              decoding="async"
            />
          </a>
          <div className="mt-4 flex flex-1 flex-col p-4">
            <img 

              className='w-[28px] h-[22px]'
              alt="Quote" 
              src="https://n7media.coolmate.me/uploads/August2025/mceclip3_44.png" 
              loading="lazy"
              decoding="async"
            />
            <p className="mt-3 flex-1 text-base leading-5 text-neutral-700 lg:leading-6">
              Video: 10 cách phối đồ với áo thun đen Care &amp; Share by Coolmate
            </p>
            <p className="mt-3 text-right text-xs font-medium leading-3.5 text-primary lg:text-xl lg:leading-6">
              -Vũ Lê Trang Anh
            </p>
          </div>
        </div>
      </div>
    </div>

    {/* Carousel Navigation Buttons */}
    
  </div>
</section>

      

    <section className="px-4 py-10 lg:px-16 lg:py-20 md:!py-0">
  <h2 className="text-center font-criteria text-2xl font-bold uppercase leading-8 text-neutral-900 lg:mb-8 lg:text-4xl lg:leading-13">
    Dự án điển hình
  </h2>
  
  <div className="relative mt-4 w-full" role="region" aria-roledescription="carousel">
    <div className="overflow-hidden">
      <div className="flex ml-0" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
        {/* Slide 1 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer basis-full pl-4 first-of-type:pl-0 last-of-type:pr-3 lg:basis-1/2" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
          <picture className="group block h-full w-full overflow-hidden rounded-2xl lg:rounded-3xl">
            <source srcSet="https://n7media.coolmate.me/uploads/October2024/mceclip3_48.png?aio=w-585" media="(max-width: 768px)" />
            <img 
              alt="banner" 
              src="https://n7media.coolmate.me/uploads/October2024/mceclip1_58.png" 
              className="h-full w-full object-cover transition-all duration-300 group-hover:scale-105 max-lg:h-56"
              loading="lazy"
              decoding="async"
            />
          </picture>
        </div>

        {/* Slide 2 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer basis-full pl-4 first-of-type:pl-0 last-of-type:pr-3 lg:basis-1/2">
          <picture className="group block h-full w-full overflow-hidden rounded-2xl lg:rounded-3xl">
            <source srcSet="https://n7media.coolmate.me/uploads/October2024/mceclip5_98.png?aio=w-585" media="(max-width: 768px)" />
            <img 
              alt="banner" 
              src="https://n7media.coolmate.me/uploads/October2024/mceclip0_61.png" 
              className="h-full w-full object-cover transition-all duration-300 group-hover:scale-105 max-lg:h-56"
              loading="lazy"
              decoding="async"
            />
          </picture>
        </div>

        {/* Slide 3 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer basis-full pl-4 first-of-type:pl-0 last-of-type:pr-3 lg:basis-1/2">
          <picture className="group block h-full w-full overflow-hidden rounded-2xl lg:rounded-3xl">
            <source srcSet="https://n7media.coolmate.me/uploads/March2025/mceclip0_67.png?aio=w-585" media="(max-width: 768px)" />
            <img 
              alt="banner" 
              src="https://n7media.coolmate.me/uploads/March2025/mceclip0_67.png" 
              className="h-full w-full object-cover transition-all duration-300 group-hover:scale-105 max-lg:h-56"
              loading="lazy"
              decoding="async"
            />
          </picture>
        </div>
      </div>
    </div>

    {/* Carousel Navigation Buttons */}
    <button className="inline-flex items-center justify-center gap-2 whitespace-nowrap focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-neutral-400 transition-all align-text-bottom bg-white border border-neutral-300 hover:bg-neutral-50 text-neutral-900 active:bg-neutral-100 rounded-full absolute -left-3 top-1/2 h-7 w-7 -translate-y-1/2">
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-arrow-left h-4 w-4">
    <path d="m12 19-7-7 7-7"></path>
    <path d="M19 12H5"></path>
  </svg>
  <span className="sr-only">Previous slide</span>
</button>

<button className="inline-flex items-center justify-center gap-2 whitespace-nowrap focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-neutral-400 transition-all align-text-bottom bg-white border border-neutral-300 hover:bg-neutral-50 text-neutral-900 active:bg-neutral-100 rounded-full absolute -right-3 top-1/2 h-7 w-7 -translate-y-1/2">
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-arrow-right h-4 w-4">
    <path d="M5 12h14"></path>
    <path d="m12 5 7 7-7 7"></path>
  </svg>
  <span className="sr-only">Next slide</span>
</button>
  </div>
</section>


     <section className="px-4 py-12 lg:px-16 lg:py-24">
  {/* Fund Operations Card */}
  <div className="relative overflow-hidden rounded-[32px] bg-blue-600 px-6 py-10 text-white shadow-xl md:px-12 md:py-16 lg:px-16">
    <div className="relative z-10 grid grid-cols-1 items-center gap-10 md:grid-cols-2 lg:gap-16">
      {/* Left Column - Information */}
      <div className="space-y-6">
        <p className="text-sm font-semibold uppercase tracking-wider opacity-90">HOẠT ĐỘNG CỦA QUỸ</p>
        <img
          alt="Care & Share Logo"
          loading="lazy"
          width="160"
          height="60"
          decoding="async"
          className="h-auto w-40"
          src="https://n7media.coolmate.me/uploads/August2025/mceclip3_68.png"
          style={{ color: 'transparent' }}
        />
        <div className="space-y-4 text-sm leading-relaxed md:text-base">
          <p className="opacity-95">
            Care &amp; Share mở tài khoản Thiện Nguyện với STK: 1089 tại Ngân Hàng MBBank (loại tài khoản chỉ dành cho các hoạt động Thiện Nguyện)
          </p>
          <p className="opacity-95">
            Toàn bộ số tiền trích ra từ 10% doanh thu + Số tiền quyên góp khác sẽ được chuyển 100% vào tài khoản Thiện Nguyện
          </p>
          <p className="opacity-95">
            Care &amp; Share sẽ sử dụng khoản tiền này cho các hoạt động Thiện Nguyện trong năm và CÔNG KHAI, MINH BẠCH tới tất cả khách hàng tại các kênh truyền thông chính của Care&amp;Share như Fanpage, Website
          </p>
        </div>
      </div>

      {/* Right Column - Bank Info Card */}
      <div className="rounded-[24px] bg-white p-6 text-gray-900 shadow-lg md:p-8">
        <div className="grid grid-cols-2 gap-6">
          {/* QR Code */}
          <div className="col-span-1 flex items-center justify-center">
            <img
              alt="QR Code"
              loading="lazy"
              width="180"
              height="180"
              decoding="async"
              className="h-auto w-full max-w-[180px]"
              src="https://n7media.coolmate.me/uploads/August2025/mceclip2_18.png"
              style={{ color: 'transparent' }}
            />
          </div>

          {/* Bank Details */}
          <div className="col-span-1 flex flex-col justify-center space-y-3">
            <img
              alt="MB Bank Logo"
              loading="lazy"
              width="100"
              height="30"
              decoding="async"
              className="h-auto w-24"
              src="https://n7media.coolmate.me/uploads/August2025/mceclip4_41.png"
              style={{ color: 'transparent' }}
            />
            <div className="space-y-1.5">
              <p className="text-sm font-medium md:text-base">CÔNG TY TNHH FASTECH ASIA</p>
              <p className="text-sm font-semibold md:text-base">
                STK: <strong className="text-blue-600">1089</strong>
              </p>
            </div>
            <div className="mt-4 space-y-1">
              <p className="text-sm font-semibold text-gray-900 md:text-base">Ngân Hàng MBBank</p>
              <p className="text-xs text-gray-500 leading-tight">
                (loại tài khoản chỉ dành cho các hoạt động Thiện Nguyện)
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    {/* Decorative Element */}
    <img
      alt="Decoration"
      loading="lazy"
      width="120"
      height="120"
      decoding="async"
      className="absolute right-0 top-0 h-auto w-20 opacity-80 md:w-28 lg:w-32"
      src="https://n7media.coolmate.me/uploads/August2025/mceclip5_71.png"
      style={{ color: 'transparent' }}
    />
  </div>

  {/* Bank Statement Section */}
  <h2 className="mb-6 mt-16 text-center font-criteria text-xl font-semibold uppercase leading-tight text-gray-900 lg:mb-10 lg:mt-20 lg:text-4xl">
    SAO KÊ TÀI KHOẢN QUỸ CARE &amp; SHARE TẠI MB BANK
  </h2>
  <div className="scrollbar mx-auto max-h-[700px] overflow-y-auto overflow-x-hidden rounded-[32px] border-2 border-gray-200 shadow-sm lg:w-[816px]">
    <img
      alt="sao kê tài khoản quỹ care &amp; share tại mb bank"
      loading="lazy"
      width="816"
      height="700"
      decoding="async"
      className="w-full rounded-[32px] object-cover"
      src="https://n7media.coolmate.me/uploads/July2025/mceclip0_82.jpg"
      style={{ color: 'transparent' }}
    />
  </div>

  {/* View Report Button */}
  <div className="mt-8 flex justify-center">
    <a
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center justify-center rounded-full bg-blue-600 px-8 py-3 text-sm font-medium text-white shadow-md transition-all duration-200 hover:bg-blue-700 hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 active:scale-95"
      href="https://thiennguyen.app/user/caresharewithcoolmate"
    >
      Xem báo cáo
    </a>
  </div>
</section>
<section className="px-4 py-10 lg:px-16 lg:py-20 md:!py-0">
  <h2 className="mb-4 text-center font-criteria text-lg font-medium uppercase leading-6 text-neutral-900 lg:mb-8 lg:text-4xl lg:leading-13">
    minh bạch quỹ care &amp; share
  </h2>
  
  <div className="rounded-2xl bg-neutral-100 px-6 py-2 pr-1 lg:mx-auto lg:max-w-[816px]">
    <ul className="scrollbar max-h-[350px] overflow-y-auto overflow-x-hidden pr-5">
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">11/10/2023</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>50.000.000đ</strong> vào <strong>Quỹ Operation Smile</strong> <strong>Tiền tài trợ phẫu thuật</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">08/07/2023</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>50.000.000đ</strong> vào <strong>Quỹ Operation Smile</strong> <strong>Tiền tài trợ phẫu thuật</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">02/12/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>10.000.000đ</strong> vào <strong>Chuyến Xe số 7</strong> - <strong>Coolmate gửi tài trợ Chuyến xe số 7- Nguyễn Hoàng Khải</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">27/09/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>8.700.000đ</strong> vào <strong>Quỹ nuôi em</strong> - <strong>Coolmate chuyển khoản Nuôi em Tháng 9 NELCAI04914 ,15,16,17,18,19- Hoàng Hoa Trung</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">23/08/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>10.000.000đ</strong> vào <strong>Báo điện tử dân trí-NCC985</strong> - <strong>Coolmate Care &amp; Share ủng hộ MS 4515</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">22/06/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>14.500.000đ</strong> vào <strong>Quỹ nuôi em</strong> - <strong>Coolmate ck NELCAI02857 58 59 60 61 61 63 64 65 66 - Hoàng Hoa Trung</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">27/05/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>15.950.000đ</strong> vào <strong>Quỹ nuôi em</strong> - <strong>Coolmate chuyển khoản Nuôi em Tháng 4NELCAI01907,NELCAI01908,NELCAI01909,NELCAI01910,NELCAI01911,NELCAI01912,NELCAI01913,NELCAI01914,NELCAI01915,NELCAI01916,NELCAI01917- Hoàng Hoa Trung</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">12/05/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>10.150.000đ</strong> vào <strong>Quỹ nuôi em</strong> - <strong>Coolmate chuyển khoản Nuôi em ( 7 Em)- Hoàng Hoa Trung</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">08/04/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>232.419.587đ</strong> vào <strong>TRUNG TÂM THÔNG TIN NGUỒN LỰC TÌNH NGUYỆN VIỆT NAM-NCC648</strong> - <strong>V/v đề nghị thanh toán kinh phí tài trợ xây dựng Điểm Trường MN bản Trung Dù, tỉnh Điện Biên theo CV số: 95 - CV/TNQG</strong>
        </p>
      </li>
      
      <li className="border-b border-b-neutral-300 py-2">
        <p className="text-xs leading-3.5 text-neutral-500/70">02/03/2022</p>
        <p className="break-words text-base leading-6 text-neutral-800">
          Care &amp; Share by Coolmate đã chuyển <strong>11.333.542đ</strong> vào <strong>Cặp Lá Yêu Thương-NCC00203</strong> - <strong>Coolmate ủng hộ các bé có hoàn cảnh khó khăn</strong>
        </p>
      </li>
    </ul>
  </div>
</section>
<section className="px-4 py-10 lg:px-16 lg:py-20">
  <h2 className="flex items-center justify-center text-center font-criteria text-2xl font-bold uppercase leading-8 text-neutral-900 lg:mb-8 lg:text-4xl lg:leading-13">
    Blog 
    <img 
      alt="icon play" 
      loading="lazy" 
      width="50" 
      height="50" 
      decoding="async" 
      className="h-9 w-auto md:h-15" 
      src="https://n7media.coolmate.me/uploads/August2025/mceclip2_71.png" 
      style={{ color: 'transparent' }}
    />
  </h2>
  
  <div className="relative mt-4 w-full" role="region" aria-roledescription="carousel">
    <div className="overflow-hidden">
      <div className="flex -ml-4 -pl-2" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
        
        {/* Blog Item 1 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer basis-full pl-2 first-of-type:pl-4 md:basis-1/3 lg:pl-6" style={{ transform: 'translate3d(0px, 0px, 0px)' }}>
          <div className="flex items-start gap-2 lg:block">
            <a target="_blank" rel="noreferrer noopener" className="relative" href="/post/care&amp;share-x-vun-art-3795">
              <img 
                alt="blog" 
                loading="lazy" 
                width="440" 
                height="334" 
                decoding="async" 
                className="min-w-[150px] rounded-2xl object-cover max-lg:h-[110px] lg:h-96 lg:max-h-96 lg:w-full" 
                src="https://n7media.coolmate.me/uploads/February2024/CareShare_x_Vun_Art_1.jpg" 
                style={{ color: 'transparent' }}
              />
            </a>
            <div className="lg:mt-10">
              <a className="line-clamp-2 text-sm font-medium leading-4.5 text-neutral-900 lg:text-2xl lg:leading-7" href="/post/care&amp;share-x-vun-art-3795">
                VỤN ART X CARE&amp;SHARE: TIẾP SỨC CHO NHỮNG ƯỚC MƠ VIẾT LÊN TỪ VỤN
              </a>
              <div className="mb-2 mt-1 text-xs leading-4 text-neutral-500 lg:text-base lg:leading-6">
                <a href="/blog/coolmate-co-gi-moi">Coolmate có gì mới</a>
                <span> | 23/02/2024</span>
              </div>
              <p className="line-clamp-3 text-xs leading-4 text-neutral-700 lg:text-base lg:leading-6">
                Dự án hợp tác Care&amp;Share x Vụn Art chính là sự gặp gỡ của những con người cùng chung ý tưởng giúp đời, giúp người và tạo nên những giá trị bền vững cho cộng đồng xã hội.
              </p>
            </div>
          </div>
        </div>

        {/* Blog Item 2 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer basis-full pl-2 first-of-type:pl-4 md:basis-1/3 lg:pl-6">
          <div className="flex items-start gap-2 lg:block">
            <a target="_blank" rel="noreferrer noopener" className="relative" href="/post/du-an-xuan-gan-ket-tet-sum-vay-3720">
              <img 
                alt="blog" 
                loading="lazy" 
                width="440" 
                height="334" 
                decoding="async" 
                className="min-w-[150px] rounded-2xl object-cover max-lg:h-[110px] lg:h-96 lg:max-h-96 lg:w-full" 
                src="https://n7media.coolmate.me/uploads/January2024/du-an-xuan-gan-ket-tet-sum-vay-1.jpg" 
                style={{ color: 'transparent' }}
              />
            </a>
            <div className="lg:mt-10">
              <a className="line-clamp-2 text-sm font-medium leading-4.5 text-neutral-900 lg:text-2xl lg:leading-7" href="/post/du-an-xuan-gan-ket-tet-sum-vay-3720">
                [Care&amp;Share] Dự án Xuân Gắn Kết - Tết Sum Vầy 
              </a>
              <div className="mb-2 mt-1 text-xs leading-4 text-neutral-500 lg:text-base lg:leading-6">
                <a href="/blog/coolmate-co-gi-moi">Coolmate có gì mới</a>
                <span> | 29/01/2024</span>
              </div>
              <p className="line-clamp-3 text-xs leading-4 text-neutral-700 lg:text-base lg:leading-6">
                Với dự án &ldquo;Xuân Gắn Kết - Tết Sum Vầy&rdquo;, Care&amp;Share tài trợ cho 4 chương trình, trong đó 2 chương trình nổi bật nhất là &ldquo;Xuân yêu thương 2024&rdquo; và &ldquo;Chuyến xe Tết 0 đồng&rdquo;.
              </p>
            </div>
          </div>
        </div>

        {/* Blog Item 3 */}
        <div role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 cursor-pointer basis-full pl-2 first-of-type:pl-4 md:basis-1/3 lg:pl-6">
          <div className="flex items-start gap-2 lg:block">
            <a target="_blank" rel="noreferrer noopener" className="relative" href="/post/careshare-hanh-trinh-thuong-ban-3539">
              <img 
                alt="blog" 
                loading="lazy" 
                width="440" 
                height="334" 
                decoding="async" 
                className="min-w-[150px] rounded-2xl object-cover max-lg:h-[110px] lg:h-96 lg:max-h-96 lg:w-full" 
                src="https://n7media.coolmate.me/uploads/January2024/careshare-hanh-trinh-thuong-ban-3.jpg" 
                style={{ color: 'transparent' }}
              />
            </a>
            <div className="lg:mt-10">
              <a className="line-clamp-2 text-sm font-medium leading-4.5 text-neutral-900 lg:text-2xl lg:leading-7" href="/post/careshare-hanh-trinh-thuong-ban-3539">
                Care&amp;Share đồng hành cùng Hành Trình Thương Bản
              </a>
              <div className="mb-2 mt-1 text-xs leading-4 text-neutral-500 lg:text-base lg:leading-6">
                <a href="/blog/coolmate-co-gi-moi">Coolmate có gì mới</a>
                <span> | 05/01/2024</span>
              </div>
              <p className="line-clamp-3 text-xs leading-4 text-neutral-700 lg:text-base lg:leading-6">
                &ldquo;Hành Trình Thương Bản&rdquo; là hoạt động thiện nguyện đã giúp Care&amp;Share kết thúc trọn vẹn một năm 2023 với nhiều cột mốc đáng nhớ, đồng thời mở ra năm 2024 cùng nhiều dự án ý nghĩa đang chờ đón.
              </p>
            </div>
          </div>
        </div>

        {/* Các blog item khác... (giữ nguyên cấu trúc tương tự) */}
        
      </div>
    </div>

    {/* Carousel Navigation Buttons */}
    <button className="inline-flex items-center justify-center gap-2 whitespace-nowrap focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-secondary-foreground transition-all align-text-bottom bg-white border border-neutral-300 hover:bg-neutral-50 text-neutral-900 active:bg-neutral-100 rounded-full absolute -left-4 top-1/2 h-7 w-7 -translate-y-1/2">
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-arrow-left h-4 w-4">
        <path d="m12 19-7-7 7-7"></path>
        <path d="M19 12H5"></path>
      </svg>
      <span className="sr-only">Previous slide</span>
    </button>
    
    <button className="inline-flex items-center justify-center gap-2 whitespace-nowrap focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-secondary-foreground transition-all align-text-bottom bg-white border border-neutral-300 hover:bg-neutral-50 text-neutral-900 active:bg-neutral-100 rounded-full absolute -right-4 top-1/2 h-7 w-7 -translate-y-1/2">
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-arrow-right h-4 w-4">
        <path d="M5 12h14"></path>
        <path d="m12 5 7 7-7 7"></path>
      </svg>
      <span className="sr-only">Next slide</span>
    </button>
  </div>

  {/* View More Button */}
  <Link
  className="items-center gap-2 whitespace-nowrap rounded-full focus-visible:outline-none disabled:cursor-not-allowed [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 font-criteria uppercase text-sm disabled:bg-neutral-300 disabled:text-neutral-400 relative transition-all align-text-bottom bg-neutral-900 hover:bg-neutral-800 text-white active:bg-neutral-700 px-6 py-2 h-[37px] mx-auto mt-6 flex w-fit justify-center" 
  href="#"
>
  Xem thêm
</Link>
</section>
<section className="px-4 py-10 lg:px-16 lg:py-20 md:!pt-0">
  <div className="flex flex-col items-center justify-center gap-4 lg:flex-row lg:gap-4">
    {/* Facebook Button */}
    <a
      target="_blank"
      rel="noreferrer noopener"
      className="inline-flex items-center justify-center gap-3 whitespace-nowrap rounded-xl bg-blue-600 px-6 py-3 text-sm font-medium text-white shadow-md transition-all duration-200 hover:bg-blue-700 hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 active:scale-95 lg:h-[54px] lg:px-8 lg:text-base"
      href="https://www.facebook.com/caresharewithcoolmate"
    >
      <svg width="28" height="28" viewBox="0 0 28 29" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-7 w-7 lg:h-8 lg:w-8">
        <circle cx="14" cy="14.8604" r="13.5" stroke="white"></circle>
        <path d="M15.1931 22.8604V15.5625H17.5508L17.9046 12.7176H15.1931V10.9015C15.1931 10.0781 15.4124 9.51696 16.5506 9.51696L18 9.51634V6.97174C17.7494 6.93791 16.889 6.86035 15.8876 6.86035C13.7965 6.86035 12.3649 8.18592 12.3649 10.6198V12.7176H10V15.5625H12.3649V22.8604H15.1931Z" fill="white"></path>
      </svg>
      Care &amp; Share with Coolmate
    </a>

    {/* TikTok Button */}
    <a
      target="_blank"
      rel="noreferrer noopener"
      className="inline-flex items-center justify-center gap-3 whitespace-nowrap rounded-xl bg-gray-900 px-6 py-3 text-sm font-medium text-white shadow-md transition-all duration-200 hover:bg-gray-800 hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-gray-700 focus:ring-offset-2 active:scale-95 lg:h-[54px] lg:px-8 lg:text-base"
      href="https://www.tiktok.com/@cool.coolmate"
    >
      <svg width="28" height="28" viewBox="0 0 29 29" fill="none" xmlns="http://www.w3.org/2000/svg" className="h-7 w-7 lg:h-8 lg:w-8">
        <circle cx="14.5" cy="14.8604" r="13.5" stroke="white"></circle>
        <path d="M21.4925 10.8665C20.5817 10.8665 19.7413 10.5636 19.0665 10.0525C18.2925 9.46656 17.7364 8.60711 17.54 7.61652C17.4914 7.37177 17.4652 7.11919 17.4627 6.86035H14.8609V13.9989L14.8578 17.909C14.8578 18.9543 14.1798 19.8407 13.24 20.1524C12.9673 20.2429 12.6727 20.2858 12.366 20.2689C11.9745 20.2473 11.6076 20.1286 11.2887 19.9371C10.6101 19.5296 10.1501 18.7903 10.1376 17.9446C10.118 16.6229 11.1821 15.5453 12.4975 15.5453C12.7572 15.5453 13.0065 15.5879 13.24 15.6652V13.7141V13.0127C12.9938 12.976 12.7432 12.9569 12.4897 12.9569C11.05 12.9569 9.70338 13.5579 8.74082 14.6405C8.0133 15.4586 7.57691 16.5024 7.50958 17.5969C7.42137 19.0348 7.94535 20.4016 8.96151 21.41C9.11082 21.558 9.26761 21.6954 9.43157 21.8222C10.3028 22.4954 11.3679 22.8604 12.4897 22.8604C12.7432 22.8604 12.9938 22.8416 13.24 22.805C14.288 22.6491 15.2549 22.1674 16.018 21.41C16.9556 20.4795 17.4736 19.2441 17.4792 17.9293L17.4658 12.0903C17.9131 12.4368 18.4022 12.7235 18.9271 12.946C19.7435 13.2918 20.6091 13.4671 21.5 13.4668V11.5698V10.8659C21.5006 10.8665 21.4931 10.8665 21.4925 10.8665Z" fill="white"></path>
      </svg>
      Coolmate Care &amp; Share
    </a>
  </div>
</section>
    </div>
  );
}

export const metadata = {
  title: 'Care & Share - Sẻ Chia Yêu Thương',
  description: 'Care & Share là dự án CSR được xây dựng và phát triển, trong đó hoạt động trọng tâm là bán hàng gây quỹ và sử dụng quỹ để tổ chức các hoạt động xã hhội trọng tâm hướng tới trẻ em',
  keywords: 'care and share, csr, từ thiện, chia sẻ yêu thương, dự án xã hội, bán hàng gây quỹ, coolmate care',
  openGraph: {
    title: 'Care & Share - Sẻ Chia Yêu Thương',
    description: 'Care & Share là dự án CSR được xây dựng và phát triển, trong đó hoạt động trọng tâm là bán hàng gây quỹ và sử dụng quỹ để tổ chức các hoạt động xã hội trọng tâm hướng tới trẻ em',
    type: 'website',
    siteName: 'Coolmate Fashion',
  },
};
