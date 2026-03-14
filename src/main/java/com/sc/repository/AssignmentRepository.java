package com.sc.repository;

import com.sc.entity.AssignmentEntity;

import com.sc.enum_util.PublishStatus;
import com.sc.enum_util.StatusType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {

    AssignmentEntity findByAssignmentCode(String assignmentCode);

    List<AssignmentEntity> findByStatus(StatusType status);

    List<AssignmentEntity> findByClassNameAndSection(String className, String section);

    List<AssignmentEntity> findBySubject(String subject);

    List<AssignmentEntity> findByCreatedByTeacher_Id(Long teacherId);

    List<AssignmentEntity> findByDueDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM AssignmentEntity a WHERE a.dueDate < :now AND a.status = 'active'")
    List<AssignmentEntity> findOverdueAssignments(@Param("now") LocalDateTime now);

    // ============= NEW PUBLISH QUERIES =============
    @Query("SELECT a FROM AssignmentEntity a WHERE a.publishStatus = :status")
    List<AssignmentEntity> findByPublishStatus(@Param("status") PublishStatus status);

    @Query("SELECT a FROM AssignmentEntity a WHERE a.publishStatus = 'SCHEDULED' AND a.scheduledPublishDate <= :now")
    List<AssignmentEntity> findScheduledForPublishing(@Param("now") LocalDateTime now);

    @Query("SELECT a FROM AssignmentEntity a WHERE a.createdByTeacher.id = :teacherId AND a.publishStatus = :status")
    List<AssignmentEntity> findByTeacherAndPublishStatus(@Param("teacherId") Long teacherId, @Param("status") PublishStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE AssignmentEntity a SET a.publishStatus = 'PUBLISHED', a.publishedDate = :now WHERE a.assignmentId = :id")
    int publishAssignment(@Param("id") Long id, @Param("now") LocalDateTime now);
    // ===============================================

    @Query("SELECT a FROM AssignmentEntity a WHERE " +
            "(:subject IS NULL OR a.subject = :subject) AND " +
            "(:className IS NULL OR a.className = :className) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:fromDate IS NULL OR a.dueDate >= :fromDate) AND " +
            "(:toDate IS NULL OR a.dueDate <= :toDate)")
    Page<AssignmentEntity> searchAssignments(
            @Param("subject") String subject,
            @Param("className") String className,
            @Param("status") StatusType status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    @Query("SELECT a.status, COUNT(a) FROM AssignmentEntity a GROUP BY a.status")
    List<Object[]> countByStatus();

    @Query("SELECT DISTINCT a FROM AssignmentEntity a WHERE " +
            "a.status = 'active' AND " +
            "(a.publishStatus = 'PUBLISHED' OR " +
            "(a.publishStatus = 'SCHEDULED' AND a.scheduledPublishDate <= :now)) AND " +
            "(" +
            "(a.assignTo = 'specific_class' AND a.className = :className AND " +
            "(a.section = 'All Sections' OR a.section = :section)) OR " +
            "(a.assignTo = 'multiple_classes' AND :className MEMBER OF a.assignedClasses) OR " +
            "(a.assignTo = 'individual_students' AND :studentId MEMBER OF a.assignedStudents) OR " +
            "(a.assignTo = 'whole_school')) " +
            "ORDER BY a.dueDate ASC")
    List<AssignmentEntity> findAssignmentsForStudent(
            @Param("studentId") Long studentId,
            @Param("className") String className,
            @Param("section") String section,
            @Param("now") LocalDateTime now);

    boolean existsByTitleAndClassNameAndSection(String title, String className, String section);
}