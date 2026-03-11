//package com.sc.controller;
//
//import com.sc.dto.request.SingleMarksEntryRequest;
//import com.sc.dto.response.ExamMarksResponse;
//import com.sc.service.MarksService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.web.bind.annotation.*;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/marks")
//public class MarksController {
//
//    @Autowired
//    private MarksService marksService;
//
//    private String getCurrentUser() {
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//        return (auth != null && auth.getName() != null) ? auth.getName() : "SYSTEM";
//    }
//
//    // 1. Save marks for one student
//    @PostMapping("/save")
//    public ResponseEntity<ExamMarksResponse> saveMarks(@RequestBody SingleMarksEntryRequest request) {
//        String user = getCurrentUser();
//        ExamMarksResponse saved = marksService.saveMarks(request, user);
//        return new ResponseEntity<>(saved, HttpStatus.CREATED);
//    }
//
//    // 2. Get one marks record by ID
//    @GetMapping("/get/{id}")
//    public ResponseEntity<ExamMarksResponse> getMarks(@PathVariable Long id) {
//        return ResponseEntity.ok(marksService.getMarksById(id));
//    }
//
//    // 3. Get all marks of a student
//    @GetMapping("/student/all/{stdId}")
//    public ResponseEntity<List<ExamMarksResponse>> getStudentAllMarks(@PathVariable Long stdId) {
//        return ResponseEntity.ok(marksService.getAllMarksOfStudent(stdId));
//    }
//
//    // 4. Get marks of a student for a specific exam
//    @GetMapping("/student/exam/{stdId}")
//    public ResponseEntity<ExamMarksResponse> getStudentExamMarks(
//            @PathVariable Long stdId,
//            @RequestParam String examType,
//            @RequestParam String academicYear) {
//        return ResponseEntity.ok(marksService.getMarksByStudentAndExam(stdId, examType, academicYear));
//    }
//
//    // 5. Get marks for a class/section + specific exam
//    @GetMapping("/class/exam")
//    public ResponseEntity<List<ExamMarksResponse>> getClassExamMarks(
//            @RequestParam String className,
//            @RequestParam(required = false) String section,
//            @RequestParam String examType,
//            @RequestParam String academicYear) {
//        return ResponseEntity.ok(marksService.getMarksByClassAndExam(className, section, examType, academicYear));
//    }
//
//    // 6. Delete marks record
//    @DeleteMapping("/delete/{id}")
//    public ResponseEntity<Void> deleteMarks(@PathVariable Long id) {
//        marksService.deleteMarks(id);
//        return ResponseEntity.noContent().build();
//    }
//
//    // 7. Check if marks already exist
//    @GetMapping("/exists")
//    public ResponseEntity<Boolean> checkExists(
//            @RequestParam Long stdId,
//            @RequestParam String examType,
//            @RequestParam String academicYear) {
//        return ResponseEntity.ok(marksService.existsMarks(stdId, examType, academicYear));
//    }
//
//    // 8. Get all unique exam types (for dropdown)
//    @GetMapping("/exam-types")
//    public ResponseEntity<List<String>> getExamTypes() {
//        return ResponseEntity.ok(marksService.getAllExamTypes());
//    }
//}