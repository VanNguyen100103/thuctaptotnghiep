package com.ut.edu.backend.service.inter;

import com.ut.edu.backend.model.Product;
import com.ut.edu.backend.model.ProductImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Cloudinary Service Interface
 * Manages image uploads to Cloudinary with folder structure
 */
public interface ICloudinaryService {

    /**
     * Upload multiple images for a product
     */
    List<ProductImage> uploadProductImages(MultipartFile[] files, Product product, String categorySlug) throws IOException;

    /**
     * Upload a single image
     */
    ProductImage uploadSingleImage(MultipartFile file, String folderPath) throws IOException;

    /**
     * Delete an image from Cloudinary
     */
    boolean deleteImage(String publicId);

    /**
     * Delete multiple images
     */
    int deleteMultipleImages(List<String> publicIds);

    /**
     * Delete entire folder
     */
    boolean deleteFolder(String folderPath);

    /**
     * Get transformed image URL
     */
    String getTransformedImageUrl(String publicId, int width, int height, String crop);

    /**
     * Validate single image file
     */
    boolean isValidImage(MultipartFile file);

    /**
     * Validate multiple image files
     */
    boolean validateImages(MultipartFile[] files);

    /**
     * Upload category image to Cloudinary
     * Images are organized in folders: categories/
     */
    String uploadCategoryImage(MultipartFile file, String categorySlug) throws IOException;

    /**
     * Upload user avatar to Cloudinary
     * Images are organized in folders: avatars/
     */
    String uploadUserAvatar(MultipartFile file, Long userId) throws IOException;

    /**
     * Extract publicId from Cloudinary URL
     */
    String extractPublicIdFromUrl(String imageUrl);

    /**
     * Delete image from Cloudinary by URL
     */
    boolean deleteImageByUrl(String imageUrl);

    /**
     * Upload review images to Cloudinary
     * Images are organized in folders: reviews/{reviewId}/
     */
    String uploadReviewImage(MultipartFile file, Long reviewId) throws IOException;

    /**
     * Delete review images from Cloudinary by review ID
     * Deletes entire folder: reviews/{reviewId}/
     */
    boolean deleteReviewImages(Long reviewId);
}
