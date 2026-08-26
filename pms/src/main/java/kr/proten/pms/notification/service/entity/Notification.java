package kr.proten.pms.notification.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 인앱 알림 1건 (PRD-pms §4 notification).
 *
 * 수신자별 행이다 — 한 사건이 세 사람에게 가면 세 행이다. 공유 행 + 읽음 표를 따로
 * 두지 않는 이유: 회수(F3-3)와 개인 설정 필터(F1-5)가 전부 수신자 단위라 공유 행은
 * 매번 다시 갈라야 한다.
 *
 * `dedupeKey`가 멱등의 실현 지점이다(F1-2·F2-2·F3-2) — 유니크 제약이 스키마에 있어
 * 애플리케이션이 잊어도 중복이 들어가지 않는다.
 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;
    // 대상 종류·id — 화면이 알림에서 대상으로 이동하는 데 쓴다
    @Column(name = "ref_type", length = 60)
    private String refType;
    @Column(name = "ref_id")
    private Long refId;
    @Column(nullable = false, length = 500)
    private String message;
    // 같은 사건 재발행 시 중복 적재를 막는 키 (recipient_id와 함께 유일)
    @Column(name = "dedupe_key", nullable = false, length = 200)
    private String dedupeKey;
    @Column(name = "read_flag", nullable = false)
    private boolean read;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    private Notification(
            Long recipientId,
            NotificationType type,
            String refType,
            Long refId,
            String message,
            String dedupeKey,
            Instant createdAt) {
        this.recipientId = recipientId;
        this.type = type;
        this.refType = refType;
        this.refId = refId;
        this.message = message;
        this.dedupeKey = dedupeKey;
        this.read = false;
        this.createdAt = createdAt;
    }

    /** 알림을 만든다 — 생성 시점은 언제나 미읽음이다. */
    public static Notification of(
            Long recipientId,
            NotificationType type,
            String refType,
            Long refId,
            String message,
            String dedupeKey,
            Instant createdAt) {
        if (recipientId == null || type == null || createdAt == null) {
            throw new IllegalArgumentException("recipientId·type·createdAt은 필수입니다");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("알림 문구는 비어 있을 수 없습니다");
        }

        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("멱등 키는 필수입니다 (F1-2)");
        }

        return new Notification(recipientId, type, refType, refId, message, dedupeKey, createdAt);
    }

    /** 읽음 처리 (AC F1-3) — 되돌리는 경로는 두지 않는다. */
    public void markRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public String getMessage() {
        return message;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Notification{id=" + id + ", recipientId=" + recipientId + ", type=" + type
                + ", read=" + read + "}";
    }
}
