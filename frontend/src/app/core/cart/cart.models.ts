export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productSlug: string;
  productSku: string;
  quantity: number;
  size: string | null;
  color: string | null;
  priceAtAdd: number;
  subtotal: number;
  stockAvailable: number;
  productImage?: string;
}

export interface Cart {
  id: number;
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
  createdAt: string;
  updatedAt: string;
}

export interface AddCartItemRequest {
  productId: number;
  quantity: number;
  size?: string;
  color?: string;
}
