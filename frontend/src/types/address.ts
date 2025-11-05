/**
 * Address Types - Matching Backend API
 */

export type AddressType = 'SHIPPING' | 'BILLING';

export interface Address {
  id: number;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  phoneNumber?: string;
  type: AddressType;
  isDefault: boolean;
}

export interface CreateAddressRequest {
  addressLine1: string;
  addressLine2?: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  phoneNumber?: string;
  type: AddressType;
  isDefault?: boolean;
}

export interface UpdateAddressRequest {
  addressLine1: string;
  addressLine2?: string;
  city: string;
  stateProvince: string;
  postalCode: string;
  country: string;
  phoneNumber?: string;
  type: AddressType;
  isDefault?: boolean;
}

export interface AddressListResponse {
  addresses: Address[];
  count: number;
}

export interface AddressResponse {
  message: string;
  address: Address;
}
