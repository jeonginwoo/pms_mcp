package kr.proten.pms.notification.repository;

import kr.proten.pms.notification.NotificationType;
import kr.proten.pms.notification.service.entity.Notification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림 저장소 — 조회는 언제나 수신자로 먼저 좁힌다(남의 알림은 존재도 보이지 않는다). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadOrderByCreatedAtDesc(
            Long recipientId, boolean read, Pageable pageable);

    /** 멱등 판정 (F1-2·F2-2·F3-2) — 유니크 제약과 짝을 이루는 선검사. */
    boolean existsByRecipientIdAndDedupeKey(Long recipientId, String dedupeKey);


    /**
     * 미읽음 알림 회수 (AC F3-3) — 읽은 알림은 남긴다.
     *
     * 조회 후 삭제가 아니라 **조건부 삭제 한 문장**인 이유: 회수 대상을 먼저 읽어 두면
     * 그 사이에 사용자가 읽음 처리를 커밋해도 이미 로드한 엔티티를 지우게 된다 —
     * "읽은 알림은 유지"가 커밋 시점에는 지켜지지 않는다. `read_flag = false`를 DELETE의
     * 술어로 두면 DB가 잠근 행을 다시 평가하므로, 먼저 커밋한 읽음 처리가 이긴다.
     *
     * `@Modifying(clearAutomatically = true)`: 같은 트랜잭션의 영속성 컨텍스트에 남아 있는
     * 사본이 삭제된 행을 되살리지 않도록 비운다.
     *
     * @return 실제로 회수된 건수 — 0이면 이미 읽혔거나 회수할 것이 없었다
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            delete from Notification n
             where n.refType = :refType
               and n.refId = :refId
               and n.type = :type
               and n.read = false""")
    int deleteUnreadFor(
            @Param("refType") String refType,
            @Param("refId") Long refId,
            @Param("type") NotificationType type);
}
