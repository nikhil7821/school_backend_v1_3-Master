package com.sc.repository;

import com.sc.entity.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    List<SubmissionEntity> findByAssignment_AssignmentId(Long assignmentId);

    List<SubmissionEntity> findByStudent_StdId(Long studentId);

    Optional<SubmissionEntity> findByAssignment_AssignmentIdAndStudent_StdId(Long assignmentId, Long studentId);

    List<SubmissionEntity> findByAssignment_AssignmentIdAndStatus(Long assignmentId, String status);

    Long countByAssignment_AssignmentIdAndStatus(Long assignmentId, String status);

    List<SubmissionEntity> findByAssignment_AssignmentIdAndIsLateTrue(Long assignmentId);

    Long countByAssignment_AssignmentIdAndIsLateTrue(Long assignmentId);

    @Query("SELECT s FROM SubmissionEntity s WHERE s.assignment.assignmentId = :assignmentId AND s.status = 'submitted' AND s.obtainedMarks IS NULL")
    List<SubmissionEntity> findUngradedSubmissions(@Param("assignmentId") Long assignmentId);

    @Query("SELECT " +
            "COUNT(s) as total, " +
            "SUM(CASE WHEN s.status = 'submitted' OR s.status = 'graded' THEN 1 ELSE 0 END) as submitted, " +
            "SUM(CASE WHEN s.isLate = true THEN 1 ELSE 0 END) as late, " +
            "SUM(CASE WHEN s.obtainedMarks IS NOT NULL THEN 1 ELSE 0 END) as graded, " +
            "AVG(s.obtainedMarks) as average " +
            "FROM SubmissionEntity s WHERE s.assignment.assignmentId = :assignmentId")
    List<Object[]> getSubmissionStats(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s.grade, COUNT(s) FROM SubmissionEntity s WHERE s.assignment.assignmentId = :assignmentId AND s.grade IS NOT NULL GROUP BY s.grade")
    List<Object[]> getGradeDistribution(@Param("assignmentId") Long assignmentId);

    void deleteByAssignment_AssignmentId(Long assignmentId);
}