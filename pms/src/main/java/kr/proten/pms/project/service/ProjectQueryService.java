package kr.proten.pms.project.service;

import kr.proten.pms.project.service.dto.ProjectDetail;
import kr.proten.pms.project.service.dto.ProjectSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 프로젝트 조회 유스케이스 — AC A3-1~A3-3.
 * 가시성 필터는 질의 조건으로 내려가고 단건은 가시성 밖을 404로 은닉한다.
 */
public interface ProjectQueryService {

    /** 가시성 범위 내 프로젝트 목록 — 조직 범위와 본인 배정의 합집합이다. */
    Page<ProjectSummary> listVisible(long callerPersonId, Pageable pageable);

    /** 프로젝트 단건 조회 — 배정 레코드는 타 팀 인원까지 그대로 노출한다. */
    ProjectDetail getProject(long callerPersonId, long projectId);
}
