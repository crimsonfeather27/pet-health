package com.pethealth.config;

import com.pethealth.entity.HealthRecord;
import com.pethealth.entity.Like;
import com.pethealth.entity.PetProfile;
import com.pethealth.entity.Post;
import com.pethealth.entity.Reminder;
import com.pethealth.entity.Reply;
import com.pethealth.entity.User;
import com.pethealth.repository.HealthRecordRepository;
import com.pethealth.repository.LikeRepository;
import com.pethealth.repository.PetProfileRepository;
import com.pethealth.repository.PostRepository;
import com.pethealth.repository.ReminderRepository;
import com.pethealth.repository.ReplyRepository;
import com.pethealth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用启动后自动填充 Demo 数据（数据库为空时才插入）
 * <p>
 * ⚠️ 关键修复：统一使用 LocalDateTime，避免与 Post/Reply 等实体的时间类型不一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final LikeRepository likeRepository;
    private final PetProfileRepository petProfileRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final ReminderRepository reminderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("数据库已有数据，跳过初始化");
            return;
        }

        log.info("开始初始化 Demo 数据...");
        LocalDateTime now = LocalDateTime.now();

        User demo = User.builder()
                .username("demo")
                .password(passwordEncoder.encode("123456"))
                .email("demo@pethealth.com")
                .avatar("https://api.dicebear.com/7.x/bottts/svg?seed=demo")
                .createdAt(now)
                .updatedAt(now)
                .build();
        demo = userRepository.save(demo);
        log.info("Demo 用户创建完成: {}", demo.getUsername());

        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .email("admin@pethealth.com")
                .avatar("https://api.dicebear.com/7.x/bottts/svg?seed=admin")
                .createdAt(now)
                .updatedAt(now)
                .build();
        admin = userRepository.save(admin);

        Post p1 = postRepository.save(Post.builder()
                .title("我家猫咪最近不爱吃饭，该怎么办？")
                .content("橘猫，2岁，最近一周食欲下降，之前每天吃两顿现在一顿都吃不完。精神还行但没以前活泼了，需要去医院检查吗？")
                .authorId(demo.getId()).authorName("demo")
                .category("health").tags(List.of("食欲下降", "猫咪", "橘猫")).petSpecies("cat")
                .viewCount(156).likeCount(8).replyCount(2).status("published")
                .createdAt(now.minusDays(2)).updatedAt(now.minusDays(2))
                .build());

        Post p2 = postRepository.save(Post.builder()
                .title("新手养狗须知——养柯基需要准备什么？")
                .content("马上要养第一只柯基啦！大家推荐一下狗粮品牌？还有必需品清单～谢谢各位前辈！")
                .authorId(admin.getId()).authorName("admin")
                .category("experience").tags(List.of("新手", "柯基", "狗粮")).petSpecies("dog")
                .viewCount(289).likeCount(15).replyCount(2).status("published")
                .createdAt(now.minusDays(5)).updatedAt(now.minusDays(5))
                .build());

        Post p3 = postRepository.save(Post.builder()
                .title("狗狗打完疫苗后多久可以洗澡？")
                .content("刚打了四联疫苗，兽医说一周内不能洗澡，但我家狗已经两周没洗了臭得不行……")
                .authorId(demo.getId()).authorName("demo")
                .category("vaccine").tags(List.of("疫苗", "洗澡", "护理")).petSpecies("dog")
                .viewCount(98).likeCount(5).replyCount(1).status("published")
                .createdAt(now.minusDays(1)).updatedAt(now.minusDays(1))
                .build());

        // —— p1 回复（2 条，与 replyCount 一致）——
        Reply r1 = replyRepository.save(Reply.builder()
                .postId(p1.getId())
                .content("可能是天气热，试试换湿粮或者加点冻干拌一拌～我家猫夏天也这样。")
                .authorId(admin.getId()).authorName("admin").likeCount(4)
                .isAccepted(false).createdAt(now.minusDays(1)).updatedAt(now.minusDays(1))
                .build());

        replyRepository.save(Reply.builder()
                .postId(p1.getId())
                .content("如果持续一周以上食欲不好建议就医，可能是毛球或者肠胃问题，查一下放心。")
                .authorId(demo.getId()).authorName("demo").likeCount(2)
                .isAccepted(true).createdAt(now.minusDays(1)).updatedAt(now.minusDays(1))
                .build());

        // —— p2 回复（2 条，与 replyCount 一致）——
        replyRepository.save(Reply.builder()
                .postId(p2.getId())
                .content("柯基建议喂中大型犬粮，狗粮品牌可以看配料表前几位是肉就行。必需品记得买围栏和漏食玩具～")
                .authorId(demo.getId()).authorName("demo").likeCount(3)
                .isAccepted(false).createdAt(now.minusDays(4)).updatedAt(now.minusDays(4))
                .build());

        replyRepository.save(Reply.builder()
                .postId(p2.getId())
                .content("新手必备清单：狗粮、食盆水盆、牵引绳、窝垫、尿垫、梳毛刷、磨牙玩具，还有驱虫药别忘了！")
                .authorId(admin.getId()).authorName("admin").likeCount(5)
                .isAccepted(true).createdAt(now.minusDays(3)).updatedAt(now.minusDays(3))
                .build());

        // —— p3 回复（1 条，与 replyCount 一致）——
        replyRepository.save(Reply.builder()
                .postId(p3.getId())
                .content("保险起见还是等满一周再洗吧，可以用宠物湿巾先擦擦，忍忍就好～")
                .authorId(admin.getId()).authorName("admin").likeCount(1)
                .isAccepted(false).createdAt(now.minusDays(1)).updatedAt(now.minusDays(1))
                .build());

        likeRepository.save(Like.builder()
                .userId(admin.getId()).targetType("POST").targetId(p1.getId())
                .createdAt(now.minusDays(1))
                .build());

        PetProfile cat = PetProfile.builder()
                .ownerId(demo.getId()).ownerName("demo")
                .name("橘座").species("cat").breed("橘猫")
                .gender("公").birthday(java.time.LocalDate.now().minusYears(2))
                .avatar("https://api.dicebear.com/7.x/thumbs/svg?seed=juzuo")
                .description("贪吃又傲娇的小橘，体重 6kg")
                .vaccines(List.of(
                        PetProfile.Vaccine.builder().name("猫三联").vaccinatedAt(java.time.LocalDate.now().minusMonths(8)).nextDueAt(java.time.LocalDate.now().plusMonths(4)).vetClinic("爱心宠物医院").build(),
                        PetProfile.Vaccine.builder().name("狂犬疫苗").vaccinatedAt(java.time.LocalDate.now().minusMonths(4)).nextDueAt(java.time.LocalDate.now().plusMonths(8)).vetClinic("爱心宠物医院").build()
                ))
                .dewormings(List.of(
                        PetProfile.Deworming.builder().type("体内驱虫").medicine("拜宠清").dewormedAt(java.time.LocalDate.now().minusMonths(1)).nextDueAt(java.time.LocalDate.now().plusMonths(2)).build(),
                        PetProfile.Deworming.builder().type("体外驱虫").medicine("大宠爱").dewormedAt(java.time.LocalDate.now().minusMonths(2)).nextDueAt(java.time.LocalDate.now().plusMonths(1)).build()
                ))
                .checkups(List.of(
                        PetProfile.Checkup.builder().checkedAt(java.time.LocalDate.now().minusMonths(6)).vetName("李医生").clinic("爱心宠物医院").result("一切正常，体重 5.8kg").build()
                ))
                .createdAt(now).updatedAt(now)
                .build();
        petProfileRepository.save(cat);

        PetProfile dog = PetProfile.builder()
                .ownerId(admin.getId()).ownerName("admin")
                .name("豆豆").species("dog").breed("柯基")
                .gender("公").birthday(java.time.LocalDate.now().minusYears(1).minusMonths(3))
                .avatar("https://api.dicebear.com/7.x/thumbs/svg?seed=doudou")
                .description("活泼好动的小短腿，喜欢咬拖鞋")
                .vaccines(List.of(
                        PetProfile.Vaccine.builder().name("四联疫苗").vaccinatedAt(java.time.LocalDate.now().minusMonths(6)).nextDueAt(java.time.LocalDate.now().plusMonths(6)).vetClinic("宠安医院").build()
                ))
                .dewormings(List.of(
                        PetProfile.Deworming.builder().type("体内驱虫").medicine("拜宠清").dewormedAt(java.time.LocalDate.now().minusWeeks(2)).nextDueAt(java.time.LocalDate.now().plusMonths(1)).build()
                ))
                .createdAt(now).updatedAt(now)
                .build();
        petProfileRepository.save(dog);

        healthRecordRepository.save(HealthRecord.builder()
                .petId(cat.getId()).ownerId(demo.getId())
                .recordType("体重").value(Map.of("value", 6.0, "unit", "kg"))
                .recordedAt(now.minusDays(7))
                .notes("最近好像有点胖了")
                .createdAt(now.minusDays(7))
                .build());

        healthRecordRepository.save(HealthRecord.builder()
                .petId(cat.getId()).ownerId(demo.getId())
                .recordType("体温").value(Map.of("value", 38.5, "unit", "°C"))
                .recordedAt(now.minusDays(7))
                .createdAt(now.minusDays(7))
                .build());

        // Demo 提醒：一条已到期（remindAt 在过去），ReminderScheduler 会立即扫到
        reminderRepository.save(Reminder.builder()
                .ownerId(demo.getId()).petId(cat.getId()).petName(cat.getName())
                .email(demo.getEmail())
                .type("VACCINE")
                .title("猫三联疫苗即将到期")
                .description("橘座的猫三联疫苗将于下周到期，请尽快接种。")
                .remindAt(now.minusMinutes(5))
                .advanceDays(7)
                .status("PENDING")
                .notifyMethod(List.of("INAPP"))
                .createdAt(now).updatedAt(now)
                .build());

        // 另一条未到期（未来 3 天）
        reminderRepository.save(Reminder.builder()
                .ownerId(admin.getId()).petId(dog.getId()).petName(dog.getName())
                .email(admin.getEmail())
                .type("DEWORMING")
                .title("体内驱虫到期")
                .description("豆豆的体内驱虫将于 3 天后到期。")
                .remindAt(now.plusDays(3))
                .advanceDays(3)
                .status("PENDING")
                .notifyMethod(List.of("INAPP"))
                .createdAt(now).updatedAt(now)
                .build());

        log.info("Demo 数据初始化完成：{} 用户, {} 帖子, {} 回复, {} 点赞, {} 宠物档案, {} 健康记录, {} 提醒",
                userRepository.count(), postRepository.count(),
                replyRepository.count(), likeRepository.count(),
                petProfileRepository.count(), healthRecordRepository.count(),
                reminderRepository.count());
    }
}
