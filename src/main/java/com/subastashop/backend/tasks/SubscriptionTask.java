package com.subastashop.backend.tasks;

import com.subastashop.backend.services.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SubscriptionTask {

    private final SubscriptionService subscriptionService;

    public SubscriptionTask(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // Se ejecuta todos los días a la medianoche
    @Scheduled(cron = "0 0 0 * * ?")
    public void executeTrialCheck() {
        log.info("Tarea programada: Iniciando revisión automática de periodos de prueba.");
        subscriptionService.processExpiredTrials();
    }
}
