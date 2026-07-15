package com.ut.edu.backend.media;

import com.ut.edu.backend.user.User;
import com.ut.edu.backend.category.Category;
import com.ut.edu.backend.review.Review;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cloudinary Service Implementation
 * Manages product images with folder-specific configuration
 */
@Service
@Slf4j
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    /**
     * Upload multiple images for a product to Cloudinary
     * Images are organized in folders: products/{category}/{productId}/
     *
     * @param files Array of image files
     * @param product Product entity
     * @param categorySlug Category slug for folder organization
     * @return List of ProductImage entities
     */
    public List<ProductImage> uploadProductImages(MultipartFile[] files, Product product, String categorySlug) throws IOException {
        List<ProductImage> productImages = new ArrayList<>();

        // Generate folder path: products/category-slug/product-id/
        String folderPath = String.format("products/%s/%d",
            categorySlug != null ? categorySlug : "uncategorized",
            product.getId()
        );

        log.info("Uploading {} images to folder: {}", files.length, folderPath);

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];

            try {
                // Upload image to Cloudinary
                Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                        "folder", folderPath,
                        "resource_type", "image"
                    )
                );

                // Create thumbnail transformation URL
                String publicId = uploadResult.get("public_id").toString();
                String thumbnailUrl = cloudinary.url()
                    .format("auto")
                    .transformation(new com.cloudinary.Transformation()
                        .width(300).height(300).crop("fill")
                        .quality("auto:low").fetchFormat("auto"))
                    .generate(publicId);

                // Create ProductImage entity
                ProductImage productImage = ProductImage.builder()
                    .product(product)
                    .imageUrl(uploadResult.get("secure_url").toString())
                    .cloudinaryPublicId(uploadResult.get("public_id").toString())
                    .thumbnailUrl(thumbnailUrl)
                    .folderPath(folderPath)
                    .displayOrder(i)
                    .isPrimary(i == 0) // First image is primary
                    .altText(product.getName() + " - Image " + (i + 1))
                    .build();

                productImages.add(productImage);

                log.info("Successfully uploaded image {}/{}: {}",
                    i + 1, files.length, uploadResult.get("public_id"));

            } catch (IOException e) {
                log.error("Failed to upload image {}/{} for product {}",
                    i + 1, files.length, product.getId(), e);
                throw new IOException("Failed to upload image: " + e.getMessage(), e);
            }
        }

        return productImages;
    }

    /**
     * Upload a single image to Cloudinary
     *
     * @param file Image file
     * @param folderPath Custom folder path
     * @return ProductImage entity
     */
    public ProductImage uploadSingleImage(MultipartFile file, String folderPath) throws IOException {
        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", folderPath,
                    "resource_type", "image"
                )
            );

            // Create thumbnail
            String publicId = uploadResult.get("public_id").toString();
            String thumbnailUrl = cloudinary.url()
                .format("auto")
                .transformation(new com.cloudinary.Transformation()
                    .width(300).height(300).crop("fill")
                    .quality("auto:low").fetchFormat("auto"))
                .generate(publicId);

            return ProductImage.builder()
                .imageUrl(uploadResult.get("secure_url").toString())
                .cloudinaryPublicId(uploadResult.get("public_id").toString())
                .thumbnailUrl(thumbnailUrl)
                .folderPath(folderPath)
                .build();

        } catch (IOException e) {
            log.error("Failed to upload single image to folder: {}", folderPath, e);
            throw new IOException("Failed to upload image: " + e.getMessage(), e);
        }
    }

    /**
     * Delete an image from Cloudinary
     *
     * @param publicId Cloudinary public ID
     * @return true if successful
     */
    public boolean deleteImage(String publicId) {
        try {
            Map<String, Object> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", "image")
            );

            String resultStatus = result.get("result").toString();
            boolean success = "ok".equals(resultStatus);

            if (success) {
                log.info("Successfully deleted image: {}", publicId);
            } else {
                log.warn("Failed to delete image: {}, result: {}", publicId, resultStatus);
            }

            return success;

        } catch (Exception e) {
            log.error("Error deleting image from Cloudinary: {}", publicId, e);
            return false;
        }
    }

    /**
     * Delete multiple images from Cloudinary
     *
     * @param publicIds List of Cloudinary public IDs
     * @return Number of successfully deleted images
     */
    public int deleteMultipleImages(List<String> publicIds) {
        int deletedCount = 0;

        for (String publicId : publicIds) {
            if (deleteImage(publicId)) {
                deletedCount++;
            }
        }

        log.info("Deleted {}/{} images", deletedCount, publicIds.size());
        return deletedCount;
    }

    /**
     * Delete all images in a folder
     *
     * @param folderPath Folder path to delete
     * @return true if successful
     */
    public boolean deleteFolder(String folderPath) {
        try {
            Map<String, Object> result = cloudinary.api().deleteResourcesByPrefix(
                folderPath,
                ObjectUtils.asMap("resource_type", "image")
            );

            log.info("Successfully deleted folder: {}", folderPath);
            return true;

        } catch (Exception e) {
            log.error("Error deleting folder from Cloudinary: {}", folderPath, e);
            return false;
        }
    }

    /**
     * Generate a transformation URL for an image
     *
     * @param publicId Cloudinary public ID
     * @param width Desired width
     * @param height Desired height
     * @param crop Crop mode (fill, fit, scale, etc.)
     * @return Transformed image URL
     */
    public String getTransformedImageUrl(String publicId, int width, int height, String crop) {
        return cloudinary.url()
            .format("auto")
            .transformation(new com.cloudinary.Transformation()
                .width(width).height(height).crop(crop)
                .quality("auto:good").fetchFormat("auto"))
            .generate(publicId);
    }

    /**
     * Validate image file
     *
     * @param file MultipartFile to validate
     * @return true if valid image
     */
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // Check file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            log.warn("Image file too large: {} bytes", file.getSize());
            return false;
        }

        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("Invalid content type: {}", contentType);
            return false;
        }

        // Allow only specific image types
        List<String> allowedTypes = List.of(
            "image/jpeg", "image/jpg", "image/png",
            "image/gif", "image/webp"
        );

        if (!allowedTypes.contains(contentType.toLowerCase())) {
            log.warn("Content type not allowed: {}", contentType);
            return false;
        }

        return true;
    }

    /**
     * Validate multiple image files
     *
     * @param files Array of files to validate
     * @return true if all files are valid images
     */
    public boolean validateImages(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return false;
        }

        // Max 10 images per product
        if (files.length > 10) {
            log.warn("Too many images: {}, max allowed: 10", files.length);
            return false;
        }

        for (MultipartFile file : files) {
            if (!isValidImage(file)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Upload category image to Cloudinary
     * Images are organized in folders: categories/{categorySlug}/
     *
     * @param file Image file
     * @param categorySlug Category slug for folder organization
     * @return Image URL from Cloudinary
     */
    public String uploadCategoryImage(MultipartFile file, String categorySlug) throws IOException {
        // Validate image
        if (!isValidImage(file)) {
            throw new IOException("Invalid image file");
        }

        // Generate folder path: categories/category-slug/
        String folderPath = String.format("categories/%s",
            categorySlug != null ? categorySlug : "uncategorized"
        );

        log.info("Uploading category image to folder: {}", folderPath);

        try {
            // Upload image to Cloudinary
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", folderPath,
                    "resource_type", "image",
                    "transformation", new com.cloudinary.Transformation()
                        .width(800).height(600).crop("limit")
                        .quality("auto:good")
                )
            );

            String imageUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            log.info("Successfully uploaded category image: {}", publicId);

            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to upload category image to folder: {}", folderPath, e);
            throw new IOException("Failed to upload category image: " + e.getMessage(), e);
        }
    }

    /**
     * Upload user avatar to Cloudinary
     * Images are organized in folders: avatars/{userId}/
     *
     * @param file Image file
     * @param userId User ID for folder organization
     * @return Avatar URL from Cloudinary
     */
    public String uploadUserAvatar(MultipartFile file, Long userId) throws IOException {
        // Validate image
        if (!isValidImage(file)) {
            throw new IOException("Invalid image file");
        }

        // Generate folder path: avatars/user-{userId}/
        String folderPath = String.format("avatars/user-%d", userId);

        log.info("Uploading user avatar to folder: {}", folderPath);

        try {
            // Upload image to Cloudinary with transformation for avatar
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", folderPath,
                    "resource_type", "image",
                    "transformation", new com.cloudinary.Transformation()
                        .width(400).height(400).crop("fill")  // Square avatar
                        .gravity("face")  // Focus on face if detected
                        .quality("auto:good")
                )
            );

            String imageUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            log.info("Successfully uploaded user avatar: {}", publicId);

            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to upload user avatar to folder: {}", folderPath, e);
            throw new IOException("Failed to upload avatar: " + e.getMessage(), e);
        }
    }

    /**
     * Extract publicId from Cloudinary URL
     * URL format: https://res.cloudinary.com/cloud-name/image/upload/v123456/folder/image.jpg
     * PublicId: folder/image (without extension)
     *
     * @param imageUrl Cloudinary image URL
     * @return publicId or null if cannot extract
     */
    public String extractPublicIdFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            // Find the position after "/upload/"
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) {
                log.warn("Invalid Cloudinary URL format: {}", imageUrl);
                return null;
            }

            // Get substring after "/upload/v123456/"
            String afterUpload = imageUrl.substring(uploadIndex + 8); // 8 = length of "/upload/"

            // Remove version number (vXXXXXX/)
            int slashIndex = afterUpload.indexOf('/');
            if (slashIndex != -1 && afterUpload.startsWith("v")) {
                afterUpload = afterUpload.substring(slashIndex + 1);
            }

            // Remove file extension (.jpg, .png, etc.)
            int lastDotIndex = afterUpload.lastIndexOf('.');
            if (lastDotIndex != -1) {
                afterUpload = afterUpload.substring(0, lastDotIndex);
            }

            log.debug("Extracted publicId: {} from URL: {}", afterUpload, imageUrl);
            return afterUpload;

        } catch (Exception e) {
            log.error("Error extracting publicId from URL: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * Delete image from Cloudinary by URL
     *
     * @param imageUrl Cloudinary image URL
     * @return true if successful
     */
    public boolean deleteImageByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.warn("Cannot delete image: URL is null or empty");
            return false;
        }

        String publicId = extractPublicIdFromUrl(imageUrl);
        if (publicId == null) {
            log.error("Cannot extract publicId from URL: {}", imageUrl);
            return false;
        }

        return deleteImage(publicId);
    }

    /**
     * Upload review image to Cloudinary
     * Images are organized in folders: reviews/{reviewId}/
     *
     * @param file Image file
     * @param reviewId Review ID for folder organization
     * @return Image URL from Cloudinary
     */
    public String uploadReviewImage(MultipartFile file, Long reviewId) throws IOException {
        // Validate image
        if (!isValidImage(file)) {
            throw new IOException("Invalid image file");
        }

        // Generate folder path: reviews/review-{reviewId}/
        String folderPath = String.format("reviews/review-%d", reviewId);

        log.info("Uploading review image to folder: {}", folderPath);

        try {
            // Upload image to Cloudinary
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", folderPath,
                    "resource_type", "image",
                    "transformation", new com.cloudinary.Transformation()
                        .width(1200).height(1200).crop("limit")
                        .quality("auto:good")
                )
            );

            String imageUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            log.info("Successfully uploaded review image: {}", publicId);

            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to upload review image to folder: {}", folderPath, e);
            throw new IOException("Failed to upload review image: " + e.getMessage(), e);
        }
    }

    /**
     * Delete review images from Cloudinary by review ID
     * Deletes entire folder: reviews/{reviewId}/
     *
     * @param reviewId Review ID
     * @return true if successful
     */
    public boolean deleteReviewImages(Long reviewId) {
        String folderPath = String.format("reviews/review-%d", reviewId);
        log.info("Deleting review images folder: {}", folderPath);
        return deleteFolder(folderPath);
    }
}
