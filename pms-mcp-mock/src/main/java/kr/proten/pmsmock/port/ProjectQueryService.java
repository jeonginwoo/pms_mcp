package kr.proten.pmsmock.port;

import java.util.List;

import kr.proten.pmsmock.port.dto.ProjectDetail;
import kr.proten.pmsmock.port.dto.ProjectSummary;

/**
 * 실전 계약: PMS project 모듈의 애플리케이션 서비스가 이 시그니처를 구현한다 (부록 B-3 승격 경로).
 * 가시성·404 은닉은 서비스(이 계층) 책임 — 어댑터는 호출만 한다 (구조 원칙 3).
 */
public interface ProjectQueryService {

    /** 가시성 내 프로젝트 검색 (FR-AI-10). status·keyword는 null 허용. */
    List<ProjectSummary> searchProjects(int callerId, String status, String keyword);

    /** 프로젝트 상세 — version 포함. 가시성 밖/부재는 404 은닉 (ToolError.notFound). */
    ProjectDetail getProjectDetail(int callerId, int projectId);
}
