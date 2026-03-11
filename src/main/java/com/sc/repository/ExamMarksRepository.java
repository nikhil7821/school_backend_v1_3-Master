package com.sc.repository;

import com.sc.entity.ExamMarksEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamMarksRepository extends JpaRepository<ExamMarksEntity, Long> {

    Optional<ExamMarksEntity> findByStudentStdIdAndExamTypeAndAcademicYear(
            Long stdId, String examType, String academicYear);

    List<ExamMarksEntity> findByStudentStdId(Long stdId);

    boolean existsByStudentStdIdAndExamTypeAndAcademicYear(
            Long stdId, String examType, String academicYear);

    @Query("SELECT DISTINCT e.examType FROM ExamMarksEntity e ORDER BY e.examType")
    List<String> findDistinctExamTypes();

    @Query("SELECT e FROM ExamMarksEntity e " +
            "WHERE e.student.currentClass = :className " +
            "AND (:section IS NULL OR e.student.section = :section) " +
            "AND e.examType = :examType " +
            "AND e.academicYear = :academicYear")
    List<ExamMarksEntity> findByClassSectionExamAndYear(
            @Param("className") String className,
            @Param("section") String section,
            @Param("examType") String examType,
            @Param("academicYear") String academicYear);
}