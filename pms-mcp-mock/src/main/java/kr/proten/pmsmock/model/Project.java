package kr.proten.pmsmock.model;

import java.util.List;

/**
 * 시드 projects.json 추출본 (id = 시드 배열 인덱스+1 — PMS-M1 적재 순번과 정합).
 * progress·status·version만 가변 — update_progress 실험 대상.
 */
public class Project {

    private final int id;
    private final String name;
    private final String client;
    private final String startDate;
    private final String endDate;
    private final double contractMm;
    private final String engagement;
    private final String solution;
    private final int managerId;
    private final List<Integer> assigneeIds;
    private final String team;
    private final String division;

    private String status;
    private int progress;
    private int version = 1;

    public Project(int id, String name, String client, String status, int progress,
                   String startDate, String endDate, double contractMm, String engagement,
                   String solution, int managerId, List<Integer> assigneeIds,
                   String team, String division) {
        this.id = id;
        this.name = name;
        this.client = client;
        this.status = status;
        this.progress = progress;
        this.startDate = startDate;
        this.endDate = endDate;
        this.contractMm = contractMm;
        this.engagement = engagement;
        this.solution = solution;
        this.managerId = managerId;
        this.assigneeIds = List.copyOf(assigneeIds);
        this.team = team;
        this.division = division;
    }

    public int id() { return id; }
    public String name() { return name; }
    public String client() { return client; }
    public String status() { return status; }
    public int progress() { return progress; }
    public String startDate() { return startDate; }
    public String endDate() { return endDate; }
    public double contractMm() { return contractMm; }
    public String engagement() { return engagement; }
    public String solution() { return solution; }
    public int managerId() { return managerId; }
    public List<Integer> assigneeIds() { return assigneeIds; }
    public String team() { return team; }
    public String division() { return division; }
    public int version() { return version; }

    public boolean isParticipant(int personId) {
        return managerId == personId || assigneeIds.contains(personId);
    }

    public void applyProgress(int newProgress) {
        this.progress = newProgress;
        this.version++;
    }
}
