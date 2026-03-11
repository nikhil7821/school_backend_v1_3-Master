package com.sc.repository;

import com.sc.entity.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Long> {

    // ============= 🔍 EXISTENCE CHECKS =============
    boolean existsByStudentId(String studentId);
    boolean existsByStudentRollNumber(String studentRollNumber);

    // ============= 🔍 FIND BY UNIQUE FIELDS =============
    StudentEntity findByStudentId(String studentId);
    StudentEntity findByStudentRollNumber(String studentRollNumber);

    // ============= 🔍 FIND BY FILTERS =============
    List<StudentEntity> findByCurrentClass(String currentClass);
    List<StudentEntity> findByCurrentClassAndSection(String currentClass, String section);
    List<StudentEntity> findByStatus(String status);
    List<StudentEntity> findByAdmissionDate(Date admissionDate);

    // ============= 📊 COUNT QUERIES =============
    long countByStatus(String status);
    long countByGender(String gender);

    @Query("SELECT s.currentClass, COUNT(s) FROM StudentEntity s GROUP BY s.currentClass")
    List<Object[]> getStudentCountByClass();

    @Query("SELECT s.currentClass, s.section, COUNT(s) FROM StudentEntity s GROUP BY s.currentClass, s.section")
    List<Object[]> getStudentCountByClassAndSection();

    // ============= 🔍 SEARCH =============
    @Query("SELECT s FROM StudentEntity s WHERE " +
            "(:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR " +
            "LOWER(s.middleName) LIKE LOWER(CONCAT('%', :name, '%')) OR " +
            "LOWER(s.lastName) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:fatherName IS NULL OR LOWER(s.fatherName) LIKE LOWER(CONCAT('%', :fatherName, '%'))) AND " +
            "(:studentId IS NULL OR s.studentId = :studentId) AND " +
            "(:rollNumber IS NULL OR s.studentRollNumber = :rollNumber)")
    List<StudentEntity> searchStudents(@Param("name") String name,
                                       @Param("fatherName") String fatherName,
                                       @Param("studentId") String studentId,
                                       @Param("rollNumber") String rollNumber);
}