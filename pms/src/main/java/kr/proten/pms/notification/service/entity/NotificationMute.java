package kr.proten.pms.notification.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * 이 사람이 이 유형을 껐다 (AC H1-4 · F1-5).
 *
 * <p><b>끈 것만 행으로 남긴다</b>(opt-out): 행이 없으면 켜진 것이다. 켠 것을 저장하면
 * 유형이 늘 때마다 전원 × 유형만큼 채워 넣어야 하고, 새 유형이 기본 꺼짐으로 들어와
 * "왜 알림이 안 오지"가 된다.
 */
@Entity
@Table(name = "notification_mutes")
@IdClass(NotificationMute.Key.class)
public class NotificationMute {
    @Id
    @Column(name = "person_id")
    private Long personId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    protected NotificationMute() {
    }

    private NotificationMute(Long personId, NotificationType type) {
        this.personId = personId;
        this.type = type;
    }

    public static NotificationMute of(Long personId, NotificationType type) {
        return new NotificationMute(personId, type);
    }

    public Long getPersonId() {
        return personId;
    }

    public NotificationType getType() {
        return type;
    }

    /** 복합 키 — 사람 하나가 유형마다 최대 한 행이다. */
    public static class Key implements Serializable {
        private Long personId;
        private NotificationType type;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof Key key)) {
                return false;
            }

            return Objects.equals(personId, key.personId) && type == key.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(personId, type);
        }
    }
}
