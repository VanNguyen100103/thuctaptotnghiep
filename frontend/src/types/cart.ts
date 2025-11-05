/**
 * Cart Types - Matching Backend Response
 */

export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productSlug: string;
  productSku: string;
  productImage?: string;
  quantity: number;
  size?: string;
  color?: string;
  price: number; // Current price
  priceAtAdd: number; // Price when added to cart
  discountPrice?: number; // Discounted price if any
  subtotal: number;
  stockQuantity: number; // Available stock
  stockAvailable: number; // Alias for stockQuantity
  available: boolean; // Product availability status
}

export interface Cart {
  id: number;
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
  subtotal: number; // Subtotal before discounts
  discount: number; // Total discount amount
  total: number; // Final total after discounts
  createdAt: string;
  updatedAt: string;
}

export interface AddToCartRequest {
  productId: number;
  quantity: number;
  size?: string;
  color?: string;
}

export interface UpdateCartItemRequest {
  quantity: number;
}

// Backend API responses
export interface CartResponse {
  id: number;
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
  subtotal: number; // Subtotal before discounts
  discount: number; // Total discount amount
  total: number; // Final total after discounts
  createdAt: string;
  updatedAt: string;
}

export interface AddToCartResponse {
  message: string;
  cart: CartResponse;
  itemAdded: CartItem;
}

export interface UpdateCartItemResponse {
  message: string;
  cart: CartResponse;
  itemUpdated: CartItem;
}

export interface RemoveCartItemResponse {
  message: string;
  cart: CartResponse;
}

export interface ClearCartResponse {
  message: string;
  cart: CartResponse;
}
