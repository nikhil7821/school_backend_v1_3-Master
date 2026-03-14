package com.sc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sc.dto.request.AssignmentRequestDto;
import com.sc.dto.request.BulkActionDto;
import com.sc.dto.request.GradeRequestDto;
import com.sc.dto.response.AssignmentResponseDto;
import com.sc.dto.response.SubmissionResponseDto;
import com.sc.enum_util.AssignToType;
import com.sc.enum_util.GradingType;
import com.sc.enum_util.PriorityType;
import com.sc.enum_util.StatusType;
import com.sc.service.AssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private ObjectMapper objectMapper;

    // Simple logging methods
    private void logInfo(String message) {
        System.out.println("[INFO] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    private void logError(String message) {
        System.err.println("[ERROR] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }

    private void logWarn(String message) {
        System.out.println("[WARN] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    // ============= Helper methods for enum conversion in controller =============

    private GradingType convertToGradingType(String gradingType) {
        if (gradingType == null || gradingType.trim().isEmpty()) {
            return GradingType.marks;
        }
        try {
            return GradingType.valueOf(gradingType.toLowerCase());
        } catch (IllegalArgumentException e) {
            logWarn("Invalid grading type: " + gradingType + ", using default: marks");
            return GradingType.marks;
        }
    }

    private PriorityType convertToPriorityType(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            return PriorityType.medium;
        }
        try {
            return PriorityType.valueOf(priority.toLowerCase());
        } catch (IllegalArgumentException e) {
            logWarn("Invalid priority: " + priority + ", using default: medium");
            return PriorityType.medium;
        }
    }

    private AssignToType convertToAssignToType(String assignTo) {
        if (assignTo == null || assignTo.trim().isEmpty()) {
            return AssignToType.specific_class;
        }
        try {
            return AssignToType.valueOf(assignTo.toLowerCase());
        } catch (IllegalArgumentException e) {
            logWarn("Invalid assignTo: " + assignTo + ", using default: specific_class");
            return AssignToType.specific_class;
        }
    }

    private StatusType convertToStatusType(String status) {
        if (status == null || status.trim().isEmpty()) {
            return StatusType.active;
        }
        try {
            return StatusType.valueOf(status.toLowerCase());
        } catch (IllegalArgumentException e) {
            logWarn("Invalid status: " + status + ", using default: active");
            return StatusType.active;
        }
    }

    // ============= NEW PUBLISH ENDPOINTS =============

    @GetMapping("/teacher/{teacherId}/drafts")
    public ResponseEntity<?> getDraftAssignments(@PathVariable Long teacherId) {
        try {
            List<AssignmentResponseDto> drafts = assignmentService.getDraftAssignments(teacherId);
            return ResponseEntity.ok(drafts);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/teacher/{teacherId}/scheduled")
    public ResponseEntity<?> getScheduledAssignments(@PathVariable Long teacherId) {
        try {
            List<AssignmentResponseDto> scheduled = assignmentService.getScheduledAssignments(teacherId);
            return ResponseEntity.ok(scheduled);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{assignmentId}/publish")
    public ResponseEntity<?> publishAssignment(
            @PathVariable Long assignmentId,
            @RequestParam Long teacherId) {
        try {
            AssignmentResponseDto published = assignmentService.publishAssignment(assignmentId, teacherId);
            return ResponseEntity.ok(published);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // ============= 📝 CREATE ASSIGNMENT =============

    @PostMapping("/create-assignment")
    public ResponseEntity<?> createAssignment(@RequestBody AssignmentRequestDto requestDto) {
        logInfo("Creating assignment: " + requestDto.getTitle());
        try {
            validateAssignmentRequest(requestDto);
            AssignmentResponseDto response = assignmentService.createAssignment(requestDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logError("Error creating assignment: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ============= 📝 CREATE ASSIGNMENT WITH FILES =============

    @PostMapping(value = "/create-assignment-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createAssignmentWithFiles(
            @RequestPart("assignmentData") String assignmentDataJson,
            @RequestPart(value = "attachmentFiles", required = false) List<MultipartFile> attachmentFiles) {

        logInfo("Creating assignment with multipart data");

        try {
            objectMapper.registerModule(new JavaTimeModule());
            AssignmentRequestDto requestDto = objectMapper.readValue(assignmentDataJson, AssignmentRequestDto.class);

            // Set attachments
            if (attachmentFiles != null && !attachmentFiles.isEmpty()) {
                requestDto.setAttachmentFiles(attachmentFiles);
            }

            validateAssignmentRequest(requestDto);
            AssignmentResponseDto response = assignmentService.createAssignment(requestDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            logError("Error creating assignment with files: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ============= 🔍 GET ALL ASSIGNMENTS (PAGINATED) =============

    @GetMapping("/get-all-assignments")
    public ResponseEntity<Page<AssignmentResponseDto>> getAllAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort;
        if (sortBy != null && !sortBy.isBlank()) {
            Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(dir, sortBy);
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AssignmentResponseDto> assignmentsPage = assignmentService.getAllAssignments(pageable);
        return ResponseEntity.ok(assignmentsPage);
    }

    // ============= 🔍 GET ASSIGNMENT BY ID =============

    @GetMapping("/get-assignment-by-id/{id}")
    public ResponseEntity<?> getAssignmentById(@PathVariable Long id) {
        try {
            AssignmentResponseDto assignment = assignmentService.getAssignmentById(id);
            if (assignment != null) {
                return ResponseEntity.ok(assignment);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Assignment not found with ID: " + id);
        } catch (Exception e) {
            logError("Error getting assignment by ID " + id + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignment: " + e.getMessage());
        }
    }

    // ============= 🔍 GET ASSIGNMENT BY CODE =============

    @GetMapping("/get-assignment-by-code/{code}")
    public ResponseEntity<?> getAssignmentByCode(@PathVariable String code) {
        try {
            AssignmentResponseDto assignment = assignmentService.getAssignmentByCode(code);
            if (assignment != null) {
                return ResponseEntity.ok(assignment);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Assignment not found with Code: " + code);
        } catch (Exception e) {
            logError("Error getting assignment by code " + code + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignment: " + e.getMessage());
        }
    }

    // ============= 🔍 GET ASSIGNMENTS BY STATUS =============

    @GetMapping("/get-assignments-by-status/{status}")
    public ResponseEntity<?> getAssignmentsByStatus(@PathVariable String status) {
        try {
            List<AssignmentResponseDto> assignments = assignmentService.getAssignmentsByStatus(status);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logError("Error getting assignments by status " + status + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignments: " + e.getMessage());
        }
    }

    // ============= 🔍 GET ASSIGNMENTS BY CLASS =============

    @GetMapping("/get-assignments-by-class/{className}")
    public ResponseEntity<?> getAssignmentsByClass(@PathVariable String className) {
        try {
            List<AssignmentResponseDto> assignments = assignmentService.getAssignmentsByClass(className);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logError("Error getting assignments by class " + className + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignments: " + e.getMessage());
        }
    }

    // ============= 🔍 GET ASSIGNMENTS BY CLASS & SECTION =============

    @GetMapping("/get-assignments-by-class-section")
    public ResponseEntity<?> getAssignmentsByClassAndSection(
            @RequestParam String className,
            @RequestParam String section) {
        try {
            List<AssignmentResponseDto> assignments = assignmentService.getAssignmentsByClassAndSection(className, section);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logError("Error getting assignments by class " + className + " and section " + section + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignments: " + e.getMessage());
        }
    }

    // ============= 🔍 GET ASSIGNMENTS BY SUBJECT =============

    @GetMapping("/get-assignments-by-subject/{subject}")
    public ResponseEntity<?> getAssignmentsBySubject(@PathVariable String subject) {
        try {
            List<AssignmentResponseDto> assignments = assignmentService.getAssignmentsBySubject(subject);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logError("Error getting assignments by subject " + subject + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignments: " + e.getMessage());
        }
    }

    // ============= 🔍 GET OVERDUE ASSIGNMENTS =============

    @GetMapping("/get-overdue-assignments")
    public ResponseEntity<?> getOverdueAssignments() {
        try {
            List<AssignmentResponseDto> assignments = assignmentService.getOverdueAssignments();
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logError("Error getting overdue assignments: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting assignments: " + e.getMessage());
        }
    }

    // ============= 🔍 SEARCH ASSIGNMENTS =============

    @GetMapping("/search-assignments")
    public ResponseEntity<?> searchAssignments(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        try {
            Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));

            Page<AssignmentResponseDto> assignments = assignmentService.searchAssignments(
                    subject, className, status, priority, fromDate, toDate, pageable);

            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logError("Error searching assignments: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error searching assignments: " + e.getMessage());
        }
    }

    // ============= 📊 GET ASSIGNMENT STATISTICS =============

    @GetMapping("/get-assignment-statistics")
    public ResponseEntity<?> getAssignmentStatistics() {
        try {
            Map<String, Object> statistics = assignmentService.getAssignmentStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logError("Error getting assignment statistics: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting statistics: " + e.getMessage());
        }
    }

    // ============= 📊 GET COUNT BY STATUS =============

    @GetMapping("/get-count-by-status")
    public ResponseEntity<?> getCountByStatus() {
        try {
            Map<String, Long> countByStatus = assignmentService.getCountByStatus();
            return ResponseEntity.ok(countByStatus);
        } catch (Exception e) {
            logError("Error getting count by status: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting count: " + e.getMessage());
        }
    }

    // ============= 📊 GET ASSIGNMENT ANALYTICS =============

    @GetMapping("/get-assignment-analytics/{id}")
    public ResponseEntity<?> getAssignmentAnalytics(@PathVariable Long id) {
        try {
            Map<String, Object> analytics = assignmentService.getAssignmentAnalytics(id);
            return ResponseEntity.ok(analytics);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logError("Error getting assignment analytics: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting analytics: " + e.getMessage());
        }
    }

    // ============= ✏️ UPDATE ASSIGNMENT (FULL) =============

    @PutMapping("/update-assignment/{id}")
    public ResponseEntity<?> updateAssignment(
            @PathVariable Long id,
            @RequestBody AssignmentRequestDto requestDto) {
        try {
            AssignmentResponseDto response = assignmentService.updateAssignment(id, requestDto);
            if (response != null) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Assignment not found with ID: " + id);
        } catch (Exception e) {
            logError("Error updating assignment " + id + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating assignment: " + e.getMessage());
        }
    }

    // ============= ✏️ UPDATE ASSIGNMENT WITH FILES =============

    @PatchMapping("/update-assignment-with-files/{id}")
    public ResponseEntity<?> updateAssignmentWithFiles(
            @PathVariable Long id,
            @RequestPart("assignmentData") String assignmentDataJson,
            @RequestPart(value = "attachmentFiles", required = false) List<MultipartFile> attachmentFiles) {

        try {
            objectMapper.registerModule(new JavaTimeModule());
            AssignmentRequestDto requestDto = objectMapper.readValue(assignmentDataJson, AssignmentRequestDto.class);

            if (attachmentFiles != null && !attachmentFiles.isEmpty()) {
                requestDto.setAttachmentFiles(attachmentFiles);
            }

            AssignmentResponseDto response = assignmentService.updateAssignment(id, requestDto);
            if (response != null) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Assignment not found with ID: " + id);

        } catch (Exception e) {
            logError("Error updating assignment with files: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating assignment: " + e.getMessage());
        }
    }

    // ============= ✏️ UPDATE ASSIGNMENT (PARTIAL) =============

    @PatchMapping("/update-assignment-partial/{id}")
    public ResponseEntity<?> updateAssignmentPartial(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        try {
            AssignmentResponseDto response = assignmentService.updateAssignmentPartial(id, updates);
            if (response != null) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Assignment not found with ID: " + id);
        } catch (Exception e) {
            logError("Error partially updating assignment " + id + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating assignment: " + e.getMessage());
        }
    }

    // ============= ✏️ UPDATE ASSIGNMENT STATUS =============

    @PatchMapping("/update-assignment-status/{id}")
    public ResponseEntity<?> updateAssignmentStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            AssignmentResponseDto response = assignmentService.updateAssignmentStatus(id, status);
            if (response != null) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Assignment not found with ID: " + id);
        } catch (Exception e) {
            logError("Error updating assignment status " + id + ": " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating status: " + e.getMessage());
        }
    }

    // ============= 🗑️ DELETE ASSIGNMENT =============

    @DeleteMapping("/delete-assignment/{id}")
    public ResponseEntity<?> deleteAssignment(@PathVariable Long id) {
        try {
            assignmentService.deleteAssignment(id);
            return ResponseEntity.ok("Assignment deleted successfully with ID: " + id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logError("Error deleting assignment: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting assignment: " + e.getMessage());
        }
    }

    // ============= 📤 BULK UPDATE STATUS =============

    @PatchMapping("/bulk-update-status")
    public ResponseEntity<?> bulkUpdateStatus(
            @RequestBody List<Long> assignmentIds,
            @RequestParam String status) {
        try {
            List<AssignmentResponseDto> updated = assignmentService.bulkUpdateStatus(assignmentIds, status);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logError("Error bulk updating status: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error bulk updating status: " + e.getMessage());
        }
    }

    // ============= 📤 BULK DELETE =============

    @DeleteMapping("/bulk-delete")
    public ResponseEntity<?> bulkDelete(@RequestBody List<Long> assignmentIds) {
        try {
            assignmentService.bulkDeleteAssignments(assignmentIds);
            return ResponseEntity.ok("Assignments deleted successfully");
        } catch (Exception e) {
            logError("Error bulk deleting: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error bulk deleting: " + e.getMessage());
        }
    }

    // ============= 📤 EXECUTE BULK ACTION =============

    @PostMapping("/execute-bulk-action")
    public ResponseEntity<?> executeBulkAction(@RequestBody BulkActionDto bulkActionDto) {
        try {
            Map<String, Object> result = assignmentService.executeBulkAction(bulkActionDto);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logError("Error executing bulk action: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error executing bulk action: " + e.getMessage());
        }
    }

    // ============= 📝 SUBMISSION ENDPOINTS =============

    @PostMapping("/grade-submission")
    public ResponseEntity<?> gradeSubmission(@RequestBody GradeRequestDto gradeRequest) {
        try {
            SubmissionResponseDto response = assignmentService.gradeSubmission(gradeRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logError("Error grading submission: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error grading submission: " + e.getMessage());
        }
    }

    @GetMapping("/{assignmentId}/get-submissions")
    public ResponseEntity<?> getSubmissionsByAssignment(@PathVariable Long assignmentId) {
        try {
            List<SubmissionResponseDto> submissions = assignmentService.getSubmissionsByAssignment(assignmentId);
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            logError("Error getting submissions: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting submissions: " + e.getMessage());
        }
    }

    @GetMapping("/get-submissions-by-student/{studentId}")
    public ResponseEntity<?> getSubmissionsByStudent(@PathVariable Long studentId) {
        try {
            List<SubmissionResponseDto> submissions = assignmentService.getSubmissionsByStudent(studentId);
            return ResponseEntity.ok(submissions);
        } catch (Exception e) {
            logError("Error getting submissions: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting submissions: " + e.getMessage());
        }
    }

    @GetMapping("/{assignmentId}/get-submission/{studentId}")
    public ResponseEntity<?> getSubmission(
            @PathVariable Long assignmentId,
            @PathVariable Long studentId) {
        try {
            SubmissionResponseDto submission = assignmentService.getSubmission(assignmentId, studentId);
            if (submission != null) {
                return ResponseEntity.ok(submission);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Submission not found");
        } catch (Exception e) {
            logError("Error getting submission: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting submission: " + e.getMessage());
        }
    }

    @GetMapping("/{assignmentId}/get-submission-stats")
    public ResponseEntity<?> getSubmissionStats(@PathVariable Long assignmentId) {
        try {
            Map<String, Object> stats = assignmentService.getSubmissionStats(assignmentId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logError("Error getting submission stats: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error getting stats: " + e.getMessage());
        }
    }

    // ============= 📎 ATTACHMENT ENDPOINTS =============

    @PostMapping(value = "/{id}/upload-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAttachments(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files) {
        try {
            AssignmentResponseDto response = assignmentService.uploadAttachments(id, files);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logError("Error uploading attachments: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading attachments: " + e.getMessage());
        }
    }

    @GetMapping(value = "/{id}/attachments/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getAttachment(
            @PathVariable Long id,
            @PathVariable String fileName) {
        try {
            byte[] fileData = assignmentService.getAttachment(id, fileName);
            if (fileData != null) {
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                        .body(fileData);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logError("Error getting attachment: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============= 🔄 REMINDER ENDPOINTS =============

    @PostMapping("/{id}/send-reminders")
    public ResponseEntity<?> sendReminders(
            @PathVariable Long id,
            @RequestParam String reminderType,
            @RequestParam(required = false) String customMessage) {
        try {
            assignmentService.sendReminders(id, reminderType, customMessage);
            return ResponseEntity.ok("Reminders sent successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logError("Error sending reminders: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error sending reminders: " + e.getMessage());
        }
    }

    @PostMapping("/send-bulk-reminders")
    public ResponseEntity<?> sendBulkReminders(
            @RequestBody List<Long> assignmentIds,
            @RequestParam String reminderType,
            @RequestParam(required = false) String customMessage) {
        try {
            assignmentService.sendBulkReminders(assignmentIds, reminderType, customMessage);
            return ResponseEntity.ok("Bulk reminders sent successfully");
        } catch (Exception e) {
            logError("Error sending bulk reminders: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error sending reminders: " + e.getMessage());
        }
    }

    // ============= 🎯 HELPER METHODS =============

    private void validateAssignmentRequest(AssignmentRequestDto requestDto) {
        if (requestDto.getTitle() == null || requestDto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (requestDto.getSubject() == null || requestDto.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (requestDto.getClassName() == null || requestDto.getClassName().trim().isEmpty()) {
            throw new IllegalArgumentException("Class is required");
        }
        if (requestDto.getSection() == null || requestDto.getSection().trim().isEmpty()) {
            throw new IllegalArgumentException("Section is required");
        }
        if (requestDto.getDescription() == null || requestDto.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (requestDto.getGradingType() == null) {
            throw new IllegalArgumentException("Grading type is required");
        }
        if (requestDto.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        if (requestDto.getDueDate() == null) {
            throw new IllegalArgumentException("Due date is required");
        }
        if (requestDto.getDueDate().isBefore(requestDto.getStartDate())) {
            throw new IllegalArgumentException("Due date must be after start date");
        }
        if (requestDto.getPriority() == null) {
            throw new IllegalArgumentException("Priority is required");
        }
        if (requestDto.getAssignTo() == null) {
            throw new IllegalArgumentException("Assign to is required");
        }
    }
}