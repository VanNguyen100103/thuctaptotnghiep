/**
 * Address API Client - Matching Backend API
 * Handles all user address management API calls
 */

import { get, post, put, del } from './client';
import type {
  Address,
  CreateAddressRequest,
  UpdateAddressRequest,
  AddressListResponse,
  AddressResponse,
} from '@/types/address';

// ==================== AUTHENTICATED ENDPOINTS ====================

/**
 * Get all user addresses
 * Endpoint: GET /api/addresses
 * Requires: Authentication
 */
export async function getUserAddresses(): Promise<AddressListResponse> {
  return get<AddressListResponse>('/api/addresses');
}

/**
 * Get address by ID
 * Endpoint: GET /api/addresses/{id}
 * Requires: Authentication
 */
export async function getAddressById(id: number): Promise<Address> {
  return get<Address>(`/api/addresses/${id}`);
}

/**
 * Create new address
 * Endpoint: POST /api/addresses
 * Requires: Authentication
 */
export async function createAddress(data: CreateAddressRequest): Promise<AddressResponse> {
  return post<AddressResponse>('/api/addresses', data);
}

/**
 * Update address
 * Endpoint: PUT /api/addresses/{id}
 * Requires: Authentication
 */
export async function updateAddress(id: number, data: UpdateAddressRequest): Promise<AddressResponse> {
  return put<AddressResponse>(`/api/addresses/${id}`, data);
}

/**
 * Delete address
 * Endpoint: DELETE /api/addresses/{id}
 * Requires: Authentication
 */
export async function deleteAddress(id: number): Promise<{ message: string }> {
  return del<{ message: string }>(`/api/addresses/${id}`);
}

/**
 * Set default address
 * Endpoint: POST /api/addresses/{id}/set-default
 * Requires: Authentication
 */
export async function setDefaultAddress(id: number): Promise<AddressResponse> {
  return post<AddressResponse>(`/api/addresses/${id}/set-default`, {});
}
