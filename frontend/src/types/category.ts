/**
 * Category Types
 */

export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
  imageUrl?: string;
  parentId?: number;
  parentName?: string;
  level: number;
  displayOrder: number;
  active: boolean;
  productCount?: number;
  children?: Category[];
  createdAt: string;
  updatedAt: string;
}

export interface CategoryTree extends Category {
  children: CategoryTree[];
}
