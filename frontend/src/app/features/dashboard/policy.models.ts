export interface PolicyDTO {
  id: number;
  title: string;
  content: string;
  displayOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyRequest {
  title: string;
  content: string;
  displayOrder?: number;
}
