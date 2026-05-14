package com.database2026.backend.menu;

import com.database2026.backend.menu.MenuDtos.InhaMenuCrawlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InhaMenuCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(InhaMenuCrawlScheduler.class);

    private final InhaMenuCrawlerService inhaMenuCrawlerService;

    public InhaMenuCrawlScheduler(InhaMenuCrawlerService inhaMenuCrawlerService) {
        this.inhaMenuCrawlerService = inhaMenuCrawlerService;
    }

    @Scheduled(cron = "0 0 6 * * MON", zone = "Asia/Seoul")
    public void crawlStudentDiningWeekly() {
        try {
            InhaMenuCrawlResponse response = inhaMenuCrawlerService.crawlStudentDining();
            log.info("인하대 학생식당 메뉴 주간 크롤링 완료. importedCount={}", response.importedCount());
        } catch (RuntimeException exception) {
            log.warn("인하대 학생식당 메뉴 주간 크롤링 실패", exception);
        }
    }
}
