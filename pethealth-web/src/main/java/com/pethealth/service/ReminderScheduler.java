package com.pethealth.service;

import com.pethealth.entity.Reminder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 提醒到期调度器
 * 每分钟扫描 remindAt <= now && status=PENDING 的提醒，到期即标记 SENT。
 * <p>
 * 发送语义（#10 原子抢占）：用 findAndModify 把 status 从 PENDING 原子翻转为 SENT，
 * 抢占条件里带 status=PENDING —— 多实例并发扫描时同一条提醒只会被抢占一次，
 * 不存在"先查出来再改状态"的竞态窗口，天然防止重复发送。
 * 每抢占一条都重新回查 DB（循环 findAndModify），直到无到期提醒为止。
 * <p>
 * 个人项目采用站内提醒：到期流转状态后，提醒中心页面即展示"已发送"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderScheduler {

    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelayString = "${reminder.scheduler-interval-seconds:60}000")
    public void scanAndSend() {
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        while (true) {
            // 原子抢占一条到期提醒（仅 status 仍为 PENDING 的会命中）
            Query query = new Query(Criteria.where("status").is("PENDING")
                    .and("remindAt").lte(now));
            Update update = new Update()
                    .set("status", "SENT")
                    .set("updatedAt", LocalDateTime.now());
            Reminder claimed = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(false), Reminder.class);

            if (claimed == null) break;
            sent++;
            log.info("到期提醒已发送: id={}, title={}, pet={}",
                    claimed.getId(), claimed.getTitle(), claimed.getPetName());
        }

        if (sent == 0) {
            log.debug("无到期提醒");
        } else {
            log.info("本轮共发送 {} 条到期提醒", sent);
        }
    }
}
