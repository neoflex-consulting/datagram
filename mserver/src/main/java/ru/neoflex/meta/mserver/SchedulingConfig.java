package ru.neoflex.meta.mserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    @Value("${scheduler.pool_size:10}")
    private String poolSize;
    @Bean
    public TaskScheduler taskScheduler() {
        TaskScheduler scheduler = new ThreadPoolTaskScheduler();
        ((ThreadPoolTaskScheduler) scheduler).setPoolSize(Integer.parseInt(poolSize));
        return scheduler;
    }
}
