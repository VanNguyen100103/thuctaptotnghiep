package com.ut.edu.backend.kafka;

import com.ut.edu.backend.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer Service
 * Sends events to Kafka topics for async processing
 */
@Service
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "spring.kafka.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class KafkaProducerService {

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Send order created event
     */
    public void sendOrderCreatedEvent(Long orderId, String orderNumber, Map<String, Object> orderData) {
        String topic = KafkaConfig.ORDER_CREATED_TOPIC;
        orderData.put("orderId", orderId);
        orderData.put("orderNumber", orderNumber);
        orderData.put("eventType", "ORDER_CREATED");
        orderData.put("timestamp", System.currentTimeMillis());

        sendMessage(topic, orderNumber, orderData);
        log.info("Sent ORDER_CREATED event for order: {}", orderNumber);
    }

    /**
     * Send order updated event
     */
    public void sendOrderUpdatedEvent(Long orderId, String orderNumber, String status, Map<String, Object> orderData) {
        String topic = KafkaConfig.ORDER_UPDATED_TOPIC;
        orderData.put("orderId", orderId);
        orderData.put("orderNumber", orderNumber);
        orderData.put("status", status);
        orderData.put("eventType", "ORDER_UPDATED");
        orderData.put("timestamp", System.currentTimeMillis());

        sendMessage(topic, orderNumber, orderData);
        log.info("Sent ORDER_UPDATED event for order: {} with status: {}", orderNumber, status);
    }

    /**
     * Send order cancelled event
     */
    public void sendOrderCancelledEvent(Long orderId, String orderNumber, String reason) {
        String topic = KafkaConfig.ORDER_CANCELLED_TOPIC;
        Map<String, Object> data = Map.of(
            "orderId", orderId,
            "orderNumber", orderNumber,
            "reason", reason,
            "eventType", "ORDER_CANCELLED",
            "timestamp", System.currentTimeMillis()
        );

        sendMessage(topic, orderNumber, data);
        log.info("Sent ORDER_CANCELLED event for order: {}", orderNumber);
    }

    /**
     * Send payment completed event
     */
    public void sendPaymentCompletedEvent(Long orderId, String orderNumber, String transactionId, Double amount) {
        String topic = KafkaConfig.PAYMENT_COMPLETED_TOPIC;
        Map<String, Object> data = Map.of(
            "orderId", orderId,
            "orderNumber", orderNumber,
            "transactionId", transactionId,
            "amount", amount,
            "eventType", "PAYMENT_COMPLETED",
            "timestamp", System.currentTimeMillis()
        );

        sendMessage(topic, transactionId, data);
        log.info("Sent PAYMENT_COMPLETED event for order: {}, transaction: {}", orderNumber, transactionId);
    }

    /**
     * Send payment failed event
     */
    public void sendPaymentFailedEvent(Long orderId, String orderNumber, String reason) {
        String topic = KafkaConfig.PAYMENT_FAILED_TOPIC;
        Map<String, Object> data = Map.of(
            "orderId", orderId,
            "orderNumber", orderNumber,
            "reason", reason,
            "eventType", "PAYMENT_FAILED",
            "timestamp", System.currentTimeMillis()
        );

        sendMessage(topic, orderNumber, data);
        log.info("Sent PAYMENT_FAILED event for order: {}", orderNumber);
    }

    /**
     * Send email notification event
     */
    public void sendEmailNotification(String recipientEmail, String subject, String body, String templateName) {
        String topic = KafkaConfig.EMAIL_NOTIFICATION_TOPIC;
        Map<String, Object> data = Map.of(
            "recipientEmail", recipientEmail,
            "subject", subject,
            "body", body,
            "templateName", templateName,
            "eventType", "EMAIL_NOTIFICATION",
            "timestamp", System.currentTimeMillis()
        );

        sendMessage(topic, recipientEmail, data);
        log.info("Sent EMAIL_NOTIFICATION event to: {}", recipientEmail);
    }

    /**
     * Send email event with custom data
     */
    public void sendEmailEvent(String recipientEmail, String eventType, Map<String, Object> emailData) {
        String topic = KafkaConfig.EMAIL_NOTIFICATION_TOPIC;
        emailData.put("eventType", eventType);
        emailData.put("timestamp", System.currentTimeMillis());

        sendMessage(topic, recipientEmail, emailData);
        log.info("Sent {} email event to: {}", eventType, recipientEmail);
    }

    /**
     * Send inventory update event
     */
    public void sendInventoryUpdateEvent(Long productId, String sku, Integer quantityChange, String operation) {
        String topic = KafkaConfig.INVENTORY_UPDATE_TOPIC;
        Map<String, Object> data = Map.of(
            "productId", productId,
            "sku", sku,
            "quantityChange", quantityChange,
            "operation", operation, // "DECREMENT" or "INCREMENT"
            "eventType", "INVENTORY_UPDATE",
            "timestamp", System.currentTimeMillis()
        );

        sendMessage(topic, sku, data);
        log.info("Sent INVENTORY_UPDATE event for product: {} ({}), operation: {}, quantity: {}",
            productId, sku, operation, quantityChange);
    }

    /**
     * Generic method to send message to Kafka
     */
    private void sendMessage(String topic, String key, Object payload) {
        if (kafkaTemplate == null) {
            log.warn("Kafka is disabled. Message not sent to topic: {}", topic);
            return;
        }

        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Message sent successfully to topic: {}, partition: {}, offset: {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                    );
                } else {
                    log.error("Failed to send message to topic: {}, error: {}", topic, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error sending message to Kafka topic: {}", topic, e);
        }
    }
}
