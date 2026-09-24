package com.ut.edu.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables {@code @Scheduled} jobs (e.g. {@link com.ut.edu.backend.store.SubscriptionExpiryJob}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Spring's default scheduler is a pool of one, which means every job here
     * waits on the slowest. That was tolerable while the jobs were a nightly
     * sweep and a ten-minute poll; it is not once
     * {@link com.ut.edu.backend.automation.AutomationDispatcher} runs every
     * fifteen seconds and spends its time waiting on another machine's HTTP.
     * Three threads is enough for the jobs that exist and small enough to
     * stay inside Render's 192MB heap.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-");
        // A job still running at shutdown gets a moment to finish, so an
        // in-flight webhook is not orphaned mid-POST on every redeploy.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
