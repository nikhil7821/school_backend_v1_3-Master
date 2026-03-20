package com.sc.repository;

import com.sc.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    Optional<ClassEntity> findByClassCode(String classCode);

    List<ClassEntity> findByClassName(String className);

    List<ClassEntity> findByAcademicYear(String academicYear);

    List<ClassEntity> findBySection(String section);

    List<ClassEntity> findByStatus(String status);

    // Find by isDeleted
    List<ClassEntity> findByIsDeletedFalse();

    @Query("SELECT c FROM ClassEntity c WHERE c.isDeleted = false")
    List<ClassEntity> findAllActiveClasses();

    //    @Query("SELECT c FROM ClassEntity c WHERE c.classTeacherId = :teacherId AND c.isDeleted = false")
    @Query("SELECT c FROM ClassEntity c WHERE c.classTeacher.id = :teacherId AND c.isDeleted = false")
    List<ClassEntity> findByClassTeacherId(@Param("teacherId") Long teacherId);

    //    @Query("SELECT c FROM ClassEntity c WHERE c.assistantTeacherId = :teacherId AND c.isDeleted = false")
    @Query("SELECT c FROM ClassEntity c WHERE c.assistantTeacher.id = :teacherId AND c.isDeleted = false")
    List<ClassEntity> findByAssistantTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT COUNT(c) FROM ClassEntity c WHERE c.className = :className AND c.academicYear = :academicYear AND c.isDeleted = false")
    Long countByClassNameAndAcademicYear(@Param("className") String className, @Param("academicYear") String academicYear);

    /**
     * 🔴 NEW: Find classes where teacher is either class teacher or assistant teacher
     */
    @Query("SELECT c FROM ClassEntity c WHERE (c.classTeacher.id = :teacherId OR c.assistantTeacher.id = :teacherId) AND c.isDeleted = false")
    List<ClassEntity> findByAnyTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT c FROM ClassEntity c WHERE c.classCode = :classCode AND c.isDeleted = false")
    Optional<ClassEntity> findByClassCodeAndNotDeleted(@Param("classCode") String classCode);
}