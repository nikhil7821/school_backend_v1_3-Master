//package com.sc.service.impl;
//
//import com.sc.CustomExceptions.ResourceNotFoundException;
//import com.sc.dto.request.SingleMarksEntryRequest;
//import com.sc.dto.request.SubjectMarkRequest;
//import com.sc.dto.response.ExamMarksResponse;
//import com.sc.dto.response.SubjectMarkResponse;
//import com.sc.entity.ExamMarksEntity;
//import com.sc.entity.ExamSubjectMarkEntity;
//import com.sc.entity.StudentEntity;
//import com.sc.repository.ExamMarksRepository;
//import com.sc.repository.StudentRepository;
//import com.sc.service.MarksService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Service
//public class MarksServiceImpl implements MarksService {
//
//    @Autowired
//    private ExamMarksRepository examMarksRepository;
//
//    @Autowired
//    private StudentRepository studentRepository;
//
//    @Override
//    @Transactional
//    public ExamMarksResponse saveMarks(SingleMarksEntryRequest request, String enteredBy) {
//        if (request.getStdId() == null) throw new IllegalArgumentException("stdId required");
//        if (request.getExamType() == null || request.getExamType().trim().isEmpty()) throw new IllegalArgumentException("Exam type required");
//        if (request.getAcademicYear() == null || request.getAcademicYear().trim().isEmpty()) throw new IllegalArgumentException("Academic year required");
//        if (request.getSubjects() == null || request.getSubjects().isEmpty()) throw new IllegalArgumentException("Subjects required");
//
//        StudentEntity student = studentRepository.findById(request.getStdId())
//                .orElseThrow(() -> new ResourceNotFoundException("Student not found with stdId: " + request.getStdId()));
//
//        if (examMarksRepository.existsByStudentStdIdAndExamTypeAndAcademicYear(
//                student.getStdId(), request.getExamType(), request.getAcademicYear())) {
//            throw new IllegalStateException("Marks already exist");
//        }
//
//        ExamMarksEntity marks = new ExamMarksEntity();
//        marks.setStudent(student);
//        marks.setExamType(request.getExamType());
//        marks.setExamName(request.getExamName());
//        marks.setAcademicYear(request.getAcademicYear());
//        marks.setAssessmentDate(request.getAssessmentDate());
//        marks.setTeacherComments(request.getTeacherComments());
//        marks.setEnteredBy(enteredBy);
//
//        for (SubjectMarkRequest sub : request.getSubjects()) {
//            ExamSubjectMarkEntity sm = new ExamSubjectMarkEntity();
//            sm.setSubjectName(sub.getSubjectName());
//            sm.setMaxMarks(sub.getMaxMarks() != null ? sub.getMaxMarks() : 100);
//            sm.setObtainedMarks(sub.getObtainedMarks());
//            sm.setRemarks(sub.getRemarks());
//            sm.setPerformanceLevel(sub.getPerformanceLevel());
//            marks.addSubjectMark(sm);
//        }
//
//        marks.recalculateTotals();
//
//        ExamMarksEntity saved = examMarksRepository.save(marks);
//        return convertToResponse(saved);
//    }
//
//    @Override
//    public ExamMarksResponse getMarksById(Long marksRecordId) {
//        ExamMarksEntity marks = examMarksRepository.findById(marksRecordId)
//                .orElseThrow(() -> new ResourceNotFoundException("Marks not found"));
//        return convertToResponse(marks);
//    }
//
//    @Override
//    public List<ExamMarksResponse> getAllMarksOfStudent(Long stdId) {
//        List<ExamMarksEntity> list = examMarksRepository.findByStudentStdId(stdId);
//        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
//    }
//
//    @Override
//    public ExamMarksResponse getMarksByStudentAndExam(Long stdId, String examType, String academicYear) {
//        ExamMarksEntity marks = examMarksRepository.findByStudentStdIdAndExamTypeAndAcademicYear(
//                        stdId, examType, academicYear)
//                .orElseThrow(() -> new ResourceNotFoundException("Marks not found"));
//        return convertToResponse(marks);
//    }
//
//    @Override
//    public List<ExamMarksResponse> getMarksByClassAndExam(String className, String section, String examType, String academicYear) {
//        List<ExamMarksEntity> list = examMarksRepository.findByClassSectionExamAndYear(
//                className, section, examType, academicYear);
//        return list.stream().map(this::convertToResponse).collect(Collectors.toList());
//    }
//
//    @Override
//    @Transactional
//    public void deleteMarks(Long marksRecordId) {
//        examMarksRepository.deleteById(marksRecordId);
//    }
//
//    @Override
//    public boolean existsMarks(Long stdId, String examType, String academicYear) {
//        return examMarksRepository.existsByStudentStdIdAndExamTypeAndAcademicYear(stdId, examType, academicYear);
//    }
//
//    @Override
//    public List<String> getAllExamTypes() {
//        return examMarksRepository.findDistinctExamTypes();
//    }
//
//    private ExamMarksResponse convertToResponse(ExamMarksEntity e) {
//        ExamMarksResponse r = new ExamMarksResponse();
//
//        r.setMarksRecordId(e.getMarksRecordId());
//        r.setStdId(e.getStudent().getStdId());
//        r.setStudentId(e.getStudent().getStudentId()); // String studentId
//        r.setStudentName(e.getStudent().getFirstName() + " " + e.getStudent().getLastName());
//        r.setRollNumber(e.getStudent().getStudentRollNumber());
//        r.setClassName(e.getStudent().getCurrentClass());
//        r.setSection(e.getStudent().getSection());
//
//        r.setExamType(e.getExamType());
//        r.setExamName(e.getExamName());
//        r.setAcademicYear(e.getAcademicYear());
//        r.setAssessmentDate(e.getAssessmentDate());
//        r.setTeacherComments(e.getTeacherComments());
//        r.setEnteredBy(e.getEnteredBy());
//
//        r.setTotalObtainedMarks(e.getTotalObtainedMarks());
//        r.setTotalMaxMarks(e.getTotalMaxMarks());
//        r.setPercentage(e.getPercentage());
//        r.setGrade(e.getGrade());
//        r.setResult(e.getResult());
//
//        List<SubjectMarkResponse> subs = new ArrayList<>();
//        for (ExamSubjectMarkEntity sm : e.getSubjectMarks()) {
//            SubjectMarkResponse s = new SubjectMarkResponse();
//            s.setSubjectName(sm.getSubjectName());
//            s.setMaxMarks(sm.getMaxMarks());
//            s.setObtainedMarks(sm.getObtainedMarks());
//            s.setPercentage(sm.getPercentage());
//            s.setGrade(sm.getGrade());
//            s.setStatus(sm.getStatus());
//            s.setRemarks(sm.getRemarks());
//            s.setPerformanceLevel(sm.getPerformanceLevel());
//            subs.add(s);
//        }
//        r.setSubjectMarks(subs);
//
//        r.setCreatedAt(e.getCreatedAt());
//        r.setUpdatedAt(e.getUpdatedAt());
//
//        return r;
//    }
//}