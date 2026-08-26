package kr.proten.pms.notification.repository;

import java.util.List;
import kr.proten.pms.notification.service.entity.NotificationMute;
import kr.proten.pms.notification.service.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

/** 알림 설정 저장소 — 끈 것만 있다(행이 없으면 켜진 것이다). */
public interface NotificationMuteRepository
        extends JpaRepository<NotificationMute, NotificationMute.Key> {

    List<NotificationMute> findByPersonId(Long personId);

    /** F1-5 필터의 판정 한 줄 — 적재마다 부르므로 목록을 받아 오지 않는다. */
    boolean existsByPersonIdAndType(Long personId, NotificationType type);

    void deleteByPersonId(Long personId);
}
