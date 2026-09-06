package com.pethealth.service.dubbo;

import com.pethealth.entity.Reminder;

import java.util.List;

/**
 * 提醒服务 Dubbo 接口（Provider 端）
 * <p>
 * 由 reminder-service 实现，供 pethealth-web 等 Consumer 调用。设计文档 7.5 节。
 */
public interface ReminderDubboService {

    Reminder create(Reminder reminder);

    List<Reminder> findByOwnerId(String ownerId);

    /** 查询未来 N 小时内待发送的提醒 */
    List<Reminder> findPendingWithin(int hours);

    Reminder markSent(String reminderId);

    Reminder acknowledge(String reminderId);

    /**
     * 由 pet-service 事件触发，自动从疫苗/驱虫记录创建提醒
     */
    void scheduleFromPet(String petId);
}
