//package com.sc.service;
//
//import com.sc.dto.request.SingleMarksEntryRequest;
//import com.sc.dto.response.ExamMarksResponse;
//
//import java.util.List;
//
//public interface MarksService {
//
//    ExamMarksResponse saveMarks(SingleMarksEntryRequest request, String enteredBy);
//
//    ExamMarksResponse getMarksById(Long marksRecordId);
//
//    List<ExamMarksResponse> getAllMarksOfStudent(Long stdId);
//
//    ExamMarksResponse getMarksByStudentAndExam(Long stdId, String examType, String academicYear);
//
//    List<ExamMarksResponse> getMarksByClassAndExam(String className, String section, String examType, String academicYear);
//
//    void deleteMarks(Long marksRecordId);
//
//    boolean existsMarks(Long stdId, String examType, String academicYear);
//
//    List<String> getAllExamTypes();
//}