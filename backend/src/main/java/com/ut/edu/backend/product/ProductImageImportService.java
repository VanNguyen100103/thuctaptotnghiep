package com.ut.edu.backend.product;

import com.ut.edu.backend.media.CloudinaryService;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pulls the image URLs found in an imported product sheet (the "Hình ảnh
 * (url1,url2...)" column of a KiotViet export) into Cloudinary, then records
 * them as {@link ProductImage} rows.
 *
 * Runs OFF the import request thread on purpose. A real export is ~1200 rows
 * with one to three pictures each; even at Cloudinary's fast path (~1s per
 * fetch) that is several minutes of work, which no browser, proxy or Render
 * request timeout will sit through. So {@link ProductImportService} returns
 * its created/updated counts as soon as the rows themselves are written,
 * hands the picture work here, and the import dialog polls
 * {@link #progressFor} to show "đã tải x/y ảnh".
 *
 * The image bytes never pass through this server: Cloudinary is given the
 * source URL and fetches it itself (see
 * {@link CloudinaryService#uploadImageFromUrl}), which is what keeps a
 * 1200-image import inside Render's 192MB heap.
 *
 * Tenancy: background threads never carry a TenantContext, so Hibernate's
 * tenant filter is off here by design (see TenantFilterAspect's javadoc) -
 * every product id is therefore re-checked against the store that queued it
 * before anything is written, the same defense in depth TenantGuard provides
 * on request threads.
 */
@Service
@Slf4j
public class ProductImageImportService {

    /** Kept per job for the dialog's error list; a whole sheet of dead links must not grow unbounded in memory. */
    private static final int MAX_ERRORS_KEPT = 20;

    private final CloudinaryService cloudinaryService;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final RedisProductCacheService productCacheService;
    private final ExecutorService uploadPool;

    /**
     * Latest job per store. Bounded by the number of stores that have
     * imported during this JVM's lifetime (one small record each), and a
     * store only ever has one visible job - a second import started while
     * the first is still uploading extends it rather than replacing it, so
     * the progress bar never jumps backwards.
     */
    private final Map<Long, JobState> jobsByStore = new ConcurrentHashMap<>();

    public ProductImageImportService(
            CloudinaryService cloudinaryService,
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            RedisProductCacheService productCacheService,
            @Value("${product.image-import.threads:4}") int threads) {
        this.cloudinaryService = cloudinaryService;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productCacheService = productCacheService;
        this.uploadPool = Executors.newFixedThreadPool(Math.max(1, threads), runnable -> {
            Thread thread = new Thread(runnable, "product-image-import");
            // Daemon: a half-finished picture backlog must never hold up a
            // redeploy - whatever is missing gets fetched by re-importing the
            // same sheet, which is idempotent (see publicIdFor).
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        uploadPool.shutdownNow();
    }

    /** One product's worth of pictures, queued by {@link ProductImportService} once that row itself is saved. */
    public record PendingProductImages(Long productId, String productName, List<String> urls) {
    }

    /** Snapshot for the import dialog; {@code running} stays true until every queued URL has been uploaded, skipped or failed. */
    public record ImageImportProgress(int total, int uploaded, int skipped, int failed, boolean running, List<String> errors) {

        static ImageImportProgress idle() {
            return new ImageImportProgress(0, 0, 0, 0, false, List.of());
        }
    }

    /**
     * Queues every picture of an import for background upload, returning how
     * many URLs were queued so the import result can tell the dialog whether
     * to start polling at all.
     */
    public int enqueue(Long storeId, List<PendingProductImages> pending) {
        int urlCount = pending.stream().mapToInt(product -> product.urls().size()).sum();
        if (urlCount == 0) {
            return 0;
        }
        JobState job = jobsByStore.compute(storeId, (id, existing) ->
                existing != null && existing.isRunning() ? existing.addTotal(urlCount) : new JobState(urlCount));

        // One task PER PRODUCT rather than per URL: a product's own pictures
        // then upload in sheet order on a single thread, so "url1" reliably
        // becomes displayOrder 0 and the primary image, while different
        // products still upload in parallel.
        for (PendingProductImages product : pending) {
            uploadPool.submit(() -> importProductImages(storeId, product, job));
        }
        log.info("Queued {} product image(s) for store {}", urlCount, storeId);
        return urlCount;
    }

    public ImageImportProgress progressFor(Long storeId) {
        JobState job = jobsByStore.get(storeId);
        return job == null ? ImageImportProgress.idle() : job.snapshot();
    }

    private void importProductImages(Long storeId, PendingProductImages pending, JobState job) {
        try {
            if (!productRepository.existsByIdAndStoreId(pending.productId(), storeId)) {
                job.recordFailure(pending.urls().size(),
                        "Bỏ qua ảnh của sản phẩm #" + pending.productId() + " (không thuộc cửa hàng này)");
                return;
            }

            List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(pending.productId());
            int displayOrder = existing.size();
            boolean hasPrimary = existing.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsPrimary()));

            for (String url : pending.urls()) {
                try {
                    String publicId = publicIdFor(storeId, pending.productId(), url);
                    if (Boolean.TRUE.equals(productImageRepository.existsByCloudinaryPublicId(publicId))) {
                        // Re-import of a sheet already processed: this picture is
                        // both on Cloudinary and in the DB, so there is nothing to do.
                        job.skipped.incrementAndGet();
                        continue;
                    }

                    ProductImage image = cloudinaryService.uploadImageFromUrl(url, publicId, pending.productName());
                    // A proxy is enough to fill product_id and saves loading the
                    // row again - the same trick TenantGuard#currentStoreRef uses
                    // when the import sets Product.store.
                    image.setProduct(productRepository.getReferenceById(pending.productId()));
                    image.setDisplayOrder(displayOrder++);
                    image.setIsPrimary(!hasPrimary);
                    hasPrimary = true;
                    productImageRepository.save(image);
                    job.uploaded.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Image import failed for product {} ({}): {}", pending.productId(), url, e.getMessage());
                    job.recordFailure(1, "Không tải được ảnh của " + pending.productName() + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            // Only the two repository lookups above the loop can land here -
            // every per-URL failure is already caught inside it - so none of
            // this product's URLs have been counted yet. Nothing may kill the
            // pool thread or leave the progress bar short of its total.
            log.error("Image import task failed for product {}", pending.productId(), e);
            job.recordFailure(pending.urls().size(), "Lỗi tải ảnh: " + e.getMessage());
        } finally {
            if (!job.isRunning()) {
                // The import request already invalidated the search caches, but
                // that was BEFORE these pictures existed - without a second
                // pass, a cached result could show the freshly imported
                // products image-less for the rest of the cache's 15 minutes.
                // A race here just invalidates twice, which is harmless.
                productCacheService.invalidateAllSearchResults();
            }
        }
    }

    /**
     * Deterministic Cloudinary id for a (store, product, source URL) triple:
     * {@code products/{storeId}/{productId}/{sha-256 prefix of the URL}}.
     *
     * Deriving it from the URL rather than a counter is what makes
     * re-importing the same sheet a no-op instead of a pile of duplicate
     * assets - both here (the existsByCloudinaryPublicId check above) and on
     * Cloudinary's side, which is called with overwrite=false.
     */
    private String publicIdFor(Long storeId, Long productId, String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.trim().getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash).substring(0, 16);
            return "products/%d/%d/%s".formatted(storeId, productId, hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Live counters behind {@link ImageImportProgress}, shared by every task of one import. */
    private static final class JobState {
        private final AtomicInteger total;
        private final AtomicInteger uploaded = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

        JobState(int total) {
            this.total = new AtomicInteger(total);
        }

        JobState addTotal(int more) {
            total.addAndGet(more);
            return this;
        }

        boolean isRunning() {
            return uploaded.get() + skipped.get() + failed.get() < total.get();
        }

        void recordFailure(int urlCount, String message) {
            if (urlCount <= 0) {
                return;
            }
            failed.addAndGet(urlCount);
            if (errors.size() < MAX_ERRORS_KEPT) {
                errors.add(message);
            }
        }

        ImageImportProgress snapshot() {
            return new ImageImportProgress(
                    total.get(), uploaded.get(), skipped.get(), failed.get(), isRunning(), List.copyOf(errors));
        }
    }
}
