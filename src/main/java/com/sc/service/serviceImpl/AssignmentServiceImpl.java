package com.sc.service.serviceImpl;

import com.sc.dto.request.AssignmentRequestDto;
import com.sc.dto.request.BulkActionDto;
import com.sc.dto.request.GradeRequestDto;
import com.sc.dto.response.AssignmentResponseDto;
import com.sc.dto.response.SubmissionResponseDto;
import com.sc.entity.*;

import com.sc.repository.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import com.sc.enum_util.*;

import com.sc.service.AssignmentNotificationService;
import com.sc.service.AssignmentService;
import com.sc.util.AssignmentIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AssignmentServiceImpl implements AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentNotificationRepository assignmentNotificationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AssignmentIdGenerator assignmentIdGenerator;

    @Autowired
    private AssignmentNotificationService notificationService;

    @Value("${file.upload-dir:uploads/assignments}")
    private String uploadDir;

    // Simple logging methods
    private void logInfo(String message) {
        System.out.println("[INFO] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }

    private void logError(String message) {
        System.err.println("[ERROR] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    private void logDebug(String message) {
        System.out.println("[DEBUG] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    private void logWarn(String message) {
        System.out.println("[WARN] " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " - " + message);
    }

    // ============= ENUM CONVERSION METHODS =============

    private GradingType convertToGradingType(String gradingType) {
        if (gradingType == null || gradingType.trim().isEmpty()) {
            return GradingType.marks;
        }
        try {
            return GradingType.valueOf(gradingType.toLowerCase());
        } catch (IllegalArgumentException e) {
            String lowerCase = gradingType.toLowerCase();
            if (lowerCase.contains("mark")) return GradingType.marks;
            if (lowerCase.contains("grade")) return GradingType.grade;
            if (lowerCase.contains("percent")) return GradingType.percentage;
            if (lowerCase.contains("pass")) return GradingType.pass_fail;
            if (lowerCase.contains("rubric")) return GradingType.rubric;
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
            String lowerCase = priority.toLowerCase();
            if (lowerCase.contains("high")) return PriorityType.high;
            if (lowerCase.contains("medium")) return PriorityType.medium;
            if (lowerCase.contains("low")) return PriorityType.low;
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
            String lowerCase = assignTo.toLowerCase();
            if (lowerCase.contains("specific") || lowerCase.contains("class")) {
                return AssignToType.specific_class;
            } else if (lowerCase.contains("multiple")) {
                return AssignToType.multiple_classes;
            } else if (lowerCase.contains("individual")) {
                return AssignToType.individual_students;
            } else if (lowerCase.contains("whole") || lowerCase.contains("school")) {
                return AssignToType.whole_school;
            }
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
            String lowerCase = status.toLowerCase();
            if (lowerCase.contains("active") || lowerCase.contains("act")) return StatusType.active;
            if (lowerCase.contains("complete") || lowerCase.contains("done")) return StatusType.completed;
            if (lowerCase.contains("archive") || lowerCase.contains("old")) return StatusType.archived;
            return StatusType.active;
        }
    }

    private PublishStatus convertToPublishStatus(String publishStatus) {
        if (publishStatus == null || publishStatus.trim().isEmpty()) {
            return PublishStatus.DRAFT;
        }
        try {
            return PublishStatus.valueOf(publishStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            String lowerCase = publishStatus.toLowerCase();
            if (lowerCase.contains("draft")) return PublishStatus.DRAFT;
            if (lowerCase.contains("publish")) return PublishStatus.PUBLISHED;
            if (lowerCase.contains("schedule")) return PublishStatus.SCHEDULED;
            return PublishStatus.DRAFT;
        }
    }

    // Add these methods to your AssignmentServiceImpl class

    @Override
    public void sendBulkReminders(List<Long> assignmentIds, String reminderType, String customMessage) {
        logInfo("Sending bulk reminders for " + assignmentIds.size() + " assignments");

        for (Long assignmentId : assignmentIds) {
            try {
                sendReminders(assignmentId, reminderType, customMessage);
            } catch (Exception e) {
                logError("Failed to send reminder for assignment " + assignmentId + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void sendReminders(Long assignmentId, String reminderType, String customMessage) {
        logInfo("Sending " + reminderType + " reminders for assignment: " + assignmentId);

        AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + assignmentId));

        List<StudentEntity> students = studentRepository.findStudentsByClassAndSection(
                assignment.getClassName(),
                assignment.getSection().equals("All Sections") ? null : assignment.getSection()
        );

        List<Long> submittedStudentIds = submissionRepository
                .findByAssignment_AssignmentId(assignmentId)
                .stream()
                .map(s -> s.getStudent().getStdId())
                .collect(Collectors.toList());

        int reminderCount = 0;
        for (StudentEntity student : students) {
            if (!submittedStudentIds.contains(student.getStdId())) {
                // Create notification
                AssignmentNotificationEntity notification = new AssignmentNotificationEntity();
                notification.setStudent(student);

                if (customMessage != null && !customMessage.isEmpty()) {
                    notification.setTitle("Reminder: " + assignment.getTitle());
                    notification.setMessage(customMessage);
                } else {
                    if ("DUE_SOON".equals(reminderType)) {
                        notification.setTitle("⏰ Assignment Due Soon: " + assignment.getTitle());
                        notification.setMessage(String.format(
                                "Your assignment '%s' is due on %s. Please submit soon!",
                                assignment.getTitle(),
                                formatDate(assignment.getDueDate())
                        ));
                    } else if ("OVERDUE".equals(reminderType)) {
                        notification.setTitle("⚠️ Overdue Assignment: " + assignment.getTitle());
                        notification.setMessage(String.format(
                                "Your assignment '%s' was due on %s. Please submit as soon as possible.",
                                assignment.getTitle(),
                                formatDate(assignment.getDueDate())
                        ));
                    } else {
                        notification.setTitle("Reminder: " + assignment.getTitle());
                        notification.setMessage("Please submit your assignment: " + assignment.getTitle());
                    }
                }

                notification.setNotificationType("ASSIGNMENT_" + reminderType);
                notification.setChannel("IN_APP");
                notification.setStatus("SENT");
                notification.setSentDate(new Date());
                // ADD THESE LINES instead:
                notification.setAssignmentId(assignment.getAssignmentId());
                notification.setAssignmentTitle(assignment.getTitle());
                notification.setDueDate(Date.from(assignment.getDueDate().atZone(ZoneId.systemDefault()).toInstant()));
                notification.setActionRequired(true);
                notification.setActionUrl("/student/assignments/" + assignment.getAssignmentId());
                notification.setActionButtonText("Submit Now");

                assignmentNotificationRepository.save(notification);
                reminderCount++;
            }
        }

        logInfo("Sent " + reminderCount + " reminders for assignment " + assignmentId);
    }

    // Helper method to format date (add this if not already present)
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        return dateTime.format(formatter);
    }

    // ============= CREATE ASSIGNMENT =============

    @Override
    @Transactional
    public AssignmentResponseDto createAssignment(AssignmentRequestDto requestDto) {
        logInfo("Creating assignment: " + requestDto.getTitle());

        try {
            // Validate
            if (assignmentRepository.existsByTitleAndClassNameAndSection(
                    requestDto.getTitle(), requestDto.getClassName(), requestDto.getSection())) {
                throw new IllegalArgumentException("Assignment with same title already exists in this class");
            }

            // Convert to Entity
            AssignmentEntity assignment = convertToEntity(requestDto);

            // Generate unique assignment code
            assignment.setAssignmentCode(assignmentIdGenerator.generateUniqueAssignmentCode());

            // Set teacher
            if (requestDto.getCreatedByTeacherId() != null) {
                TeacherEntity teacher = teacherRepository.findById(requestDto.getCreatedByTeacherId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found with ID: " + requestDto.getCreatedByTeacherId()));
                assignment.setCreatedByTeacher(teacher);
            }

            // Handle publish status
            handlePublishStatus(assignment, requestDto);

            // Save assignment
            AssignmentEntity savedAssignment = assignmentRepository.save(assignment);
            logInfo("Assignment saved with ID: " + savedAssignment.getAssignmentId() +
                    ", Status: " + savedAssignment.getPublishStatus());

            // Send notifications if published immediately
            if (savedAssignment.getPublishStatus() == PublishStatus.PUBLISHED) {
                sendNewAssignmentNotifications(savedAssignment);
            }

            return convertToDto(savedAssignment);

        } catch (Exception e) {
            logError("Error creating assignment: " + e.getMessage(), e);
            throw new RuntimeException("Failed to create assignment: " + e.getMessage());
        }
    }

    // ============= HANDLE PUBLISH STATUS =============

    private void handlePublishStatus(AssignmentEntity assignment, AssignmentRequestDto dto) {
        // If publishNow is true, publish immediately
        if (dto.getPublishNow() != null && dto.getPublishNow()) {
            assignment.setPublishStatus(PublishStatus.PUBLISHED);
            assignment.setPublishedDate(LocalDateTime.now());
            if (dto.getCreatedByTeacherId() != null) {
                teacherRepository.findById(dto.getCreatedByTeacherId()).ifPresent(teacher ->
                        assignment.setPublishedBy(teacher.getFullName())
                );
            }
            return;
        }

        // Check for scheduled publishing
        if (dto.getScheduledPublishDate() != null) {
            assignment.setPublishStatus(PublishStatus.SCHEDULED);
            assignment.setScheduledPublishDate(dto.getScheduledPublishDate());
            return;
        }

        // Check for explicit publish status
        if (dto.getPublishStatus() != null) {
            PublishStatus status = convertToPublishStatus(dto.getPublishStatus());
            assignment.setPublishStatus(status);

            if (status == PublishStatus.PUBLISHED) {
                assignment.setPublishedDate(LocalDateTime.now());
                if (dto.getCreatedByTeacherId() != null) {
                    teacherRepository.findById(dto.getCreatedByTeacherId()).ifPresent(teacher ->
                            assignment.setPublishedBy(teacher.getFullName())
                    );
                }
            }

            if (status == PublishStatus.SCHEDULED && dto.getScheduledPublishDate() != null) {
                assignment.setScheduledPublishDate(dto.getScheduledPublishDate());
            }

            return;
        }

        // Default to DRAFT
        assignment.setPublishStatus(PublishStatus.DRAFT);
    }

    // ============= SCHEDULED PUBLISHING CHECK =============

    @Scheduled(fixedDelay = 60000) // Check every minute
    @Transactional
    public void checkScheduledPublishing() {
        logInfo("Checking for assignments scheduled to publish...");

        LocalDateTime now = LocalDateTime.now();
        List<AssignmentEntity> scheduledAssignments = assignmentRepository.findScheduledForPublishing(now);

        if (!scheduledAssignments.isEmpty()) {
            logInfo("Found " + scheduledAssignments.size() + " assignments to publish");

            for (AssignmentEntity assignment : scheduledAssignments) {
                try {
                    assignment.setPublishStatus(PublishStatus.PUBLISHED);
                    assignment.setPublishedDate(now);
                    assignment.setPublishedBy("System (Scheduled)");
                    assignmentRepository.save(assignment);

                    // Send notifications
                    sendNewAssignmentNotifications(assignment);

                    logInfo("Published scheduled assignment: " + assignment.getAssignmentCode());
                } catch (Exception e) {
                    logError("Failed to publish scheduled assignment " + assignment.getAssignmentId() + ": " + e.getMessage(), e);
                }
            }
        }
    }

    // ============= PUBLISH ASSIGNMENT MANUALLY =============

    @Override
    @Transactional
    public AssignmentResponseDto publishAssignment(Long assignmentId, Long teacherId) {
        logInfo("Manually publishing assignment ID: " + assignmentId);

        AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (assignment.getPublishStatus() == PublishStatus.PUBLISHED) {
            throw new IllegalStateException("Assignment is already published");
        }

        TeacherEntity teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        assignment.setPublishStatus(PublishStatus.PUBLISHED);
        assignment.setPublishedDate(LocalDateTime.now());
        assignment.setPublishedBy(teacher.getFullName());

        AssignmentEntity updated = assignmentRepository.save(assignment);

        // Send notifications
        sendNewAssignmentNotifications(updated);

        return convertToDto(updated);
    }

    // ============= GET DRAFT ASSIGNMENTS =============

    @Override
    public List<AssignmentResponseDto> getDraftAssignments(Long teacherId) {
        logInfo("Getting draft assignments for teacher: " + teacherId);

        List<AssignmentEntity> drafts = assignmentRepository
                .findByTeacherAndPublishStatus(teacherId, PublishStatus.DRAFT);

        return drafts.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // ============= GET SCHEDULED ASSIGNMENTS =============

    @Override
    public List<AssignmentResponseDto> getScheduledAssignments(Long teacherId) {
        logInfo("Getting scheduled assignments for teacher: " + teacherId);

        List<AssignmentEntity> scheduled = assignmentRepository
                .findByTeacherAndPublishStatus(teacherId, PublishStatus.SCHEDULED);

        return scheduled.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // ============= SEND NOTIFICATIONS =============

    private void sendNewAssignmentNotifications(AssignmentEntity assignment) {
        try {
            List<Long> studentIds = getAllStudentIdsForAssignment(assignment);
            CompletableFuture.runAsync(() -> {
                notificationService.notifyStudentsAboutNewAssignment(assignment, studentIds);
            });
            logInfo("Triggered notifications for " + studentIds.size() + " students");
        } catch (Exception e) {
            logError("Failed to send notifications: " + e.getMessage(), e);
        }
    }

    // ============= GET ALL ASSIGNMENTS =============

    @Override
    public Page<AssignmentResponseDto> getAllAssignments(Pageable pageable) {
        try {
            return assignmentRepository.findAll(pageable)
                    .map(this::convertToDtoWithStats);
        } catch (Exception e) {
            logError("Error getting paginated assignments: " + e.getMessage(), e);
            throw new RuntimeException("Failed to fetch assignments: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENT BY ID =============

    @Override
    public AssignmentResponseDto getAssignmentById(Long id) {
        try {
            return assignmentRepository.findById(id)
                    .map(this::convertToDtoWithStats)
                    .orElse(null);
        } catch (Exception e) {
            logError("Error getting assignment by ID " + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to get assignment: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENT BY CODE =============

    @Override
    public AssignmentResponseDto getAssignmentByCode(String assignmentCode) {
        try {
            AssignmentEntity assignment = assignmentRepository.findByAssignmentCode(assignmentCode);
            return assignment != null ? convertToDtoWithStats(assignment) : null;
        } catch (Exception e) {
            logError("Error getting assignment by code " + assignmentCode + ": " + e.getMessage());
            throw new RuntimeException("Failed to get assignment: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENTS BY STATUS =============

    @Override
    public List<AssignmentResponseDto> getAssignmentsByStatus(String status) {
        try {
            StatusType statusType = convertToStatusType(status);
            return assignmentRepository.findByStatus(statusType).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting assignments by status " + status + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get assignments: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENTS BY CLASS =============

    @Override
    public List<AssignmentResponseDto> getAssignmentsByClass(String className) {
        try {
            return assignmentRepository.findByClassNameAndSection(className, "All Sections").stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting assignments by class " + className + ": " + e.getMessage());
            throw new RuntimeException("Failed to get assignments: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENTS BY CLASS & SECTION =============

    @Override
    public List<AssignmentResponseDto> getAssignmentsByClassAndSection(String className, String section) {
        try {
            return assignmentRepository.findByClassNameAndSection(className, section).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting assignments by class " + className + " and section " + section + ": " + e.getMessage());
            throw new RuntimeException("Failed to get assignments: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENTS BY SUBJECT =============

    @Override
    public List<AssignmentResponseDto> getAssignmentsBySubject(String subject) {
        try {
            return assignmentRepository.findBySubject(subject).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting assignments by subject " + subject + ": " + e.getMessage());
            throw new RuntimeException("Failed to get assignments: " + e.getMessage());
        }
    }

    // ============= GET OVERDUE ASSIGNMENTS =============

    @Override
    public List<AssignmentResponseDto> getOverdueAssignments() {
        try {
            return assignmentRepository.findOverdueAssignments(LocalDateTime.now()).stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting overdue assignments: " + e.getMessage());
            throw new RuntimeException("Failed to get overdue assignments: " + e.getMessage());
        }
    }

    // ============= SEARCH ASSIGNMENTS =============

    @Override
    public Page<AssignmentResponseDto> searchAssignments(
            String subject, String className, String status, String priority,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {

        try {
            StatusType statusType = null;
            if (status != null && !status.isEmpty()) {
                statusType = convertToStatusType(status);
            }

            // Note: priority parameter is not used in the repository query
            // You may need to update your repository method to include priority
            return assignmentRepository.searchAssignments(
                            subject, className, statusType, fromDate, toDate, pageable)
                    .map(this::convertToDtoWithStats);
        } catch (Exception e) {
            logError("Error searching assignments: " + e.getMessage(), e);
            throw new RuntimeException("Failed to search assignments: " + e.getMessage());
        }
    }

    // ============= UPDATE ASSIGNMENT (FULL) =============

    @Override
    @Transactional
    public AssignmentResponseDto updateAssignment(Long id, AssignmentRequestDto requestDto) {
        try {
            return assignmentRepository.findById(id)
                    .map(assignment -> {
                        updateAssignmentFields(assignment, requestDto);

                        if (requestDto.getCreatedByTeacherId() != null) {
                            TeacherEntity teacher = teacherRepository.findById(requestDto.getCreatedByTeacherId())
                                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found with ID: " + requestDto.getCreatedByTeacherId()));
                            assignment.setCreatedByTeacher(teacher);
                        }

                        // Update publish status if needed
                        if (requestDto.getPublishStatus() != null) {
                            handlePublishStatus(assignment, requestDto);
                        }

                        assignment.setUpdatedAt(LocalDateTime.now());
                        AssignmentEntity updated = assignmentRepository.save(assignment);

                        // Send notifications if newly published
                        if (assignment.getPublishStatus() == PublishStatus.PUBLISHED &&
                                requestDto.getPublishNow() != null && requestDto.getPublishNow()) {
                            sendNewAssignmentNotifications(updated);
                        }

                        return convertToDto(updated);
                    })
                    .orElse(null);
        } catch (Exception e) {
            logError("Error updating assignment " + id + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to update assignment: " + e.getMessage());
        }
    }

    // ============= UPDATE ASSIGNMENT (PARTIAL) =============

    @Override
    @Transactional
    public AssignmentResponseDto updateAssignmentPartial(Long id, Map<String, Object> updates) {
        try {
            Optional<AssignmentEntity> optional = assignmentRepository.findById(id);
            if (optional.isPresent()) {
                AssignmentEntity assignment = optional.get();

                updates.forEach((key, value) -> {
                    switch (key) {
                        case "title":
                            assignment.setTitle((String) value);
                            break;
                        case "description":
                            assignment.setDescription((String) value);
                            break;
                        case "dueDate":
                            assignment.setDueDate(LocalDateTime.parse((String) value));
                            break;
                        case "gradingType":
                            assignment.setGradingType(convertToGradingType((String) value));
                            break;
                        case "priority":
                            assignment.setPriority(convertToPriorityType((String) value));
                            break;
                        case "assignTo":
                            assignment.setAssignTo(convertToAssignToType((String) value));
                            break;
                        case "status":
                            assignment.setStatus(convertToStatusType((String) value));
                            break;
                        case "totalMarks":
                            assignment.setTotalMarks((Integer) value);
                            break;
                        case "publishStatus":
                            assignment.setPublishStatus(convertToPublishStatus((String) value));
                            if (assignment.getPublishStatus() == PublishStatus.PUBLISHED) {
                                assignment.setPublishedDate(LocalDateTime.now());
                            }
                            break;
                        default:
                            logDebug("Unknown field: " + key);
                    }
                });

                assignment.setUpdatedAt(LocalDateTime.now());
                AssignmentEntity updated = assignmentRepository.save(assignment);
                return convertToDto(updated);
            }
            return null;
        } catch (Exception e) {
            logError("Error partially updating assignment " + id + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to update assignment: " + e.getMessage());
        }
    }

    // ============= UPDATE ASSIGNMENT STATUS =============

    @Override
    @Transactional
    public AssignmentResponseDto updateAssignmentStatus(Long id, String status) {
        try {
            Optional<AssignmentEntity> optional = assignmentRepository.findById(id);
            if (optional.isPresent()) {
                AssignmentEntity assignment = optional.get();
                assignment.setStatus(convertToStatusType(status));
                assignment.setUpdatedAt(LocalDateTime.now());
                AssignmentEntity updated = assignmentRepository.save(assignment);
                return convertToDto(updated);
            }
            return null;
        } catch (Exception e) {
            logError("Error updating assignment status " + id + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to update status: " + e.getMessage());
        }
    }

    // ============= DELETE ASSIGNMENT =============

    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        try {
            if (!assignmentRepository.existsById(id)) {
                throw new IllegalArgumentException("Assignment not found with ID: " + id);
            }
            submissionRepository.deleteByAssignment_AssignmentId(id);
            assignmentRepository.deleteById(id);
            logInfo("Assignment deleted successfully with ID: " + id);
        } catch (Exception e) {
            logError("Error deleting assignment " + id + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to delete assignment: " + e.getMessage());
        }
    }

    // ============= BULK DELETE ASSIGNMENTS =============

    @Override
    @Transactional
    public void bulkDeleteAssignments(List<Long> assignmentIds) {
        try {
            for (Long id : assignmentIds) {
                submissionRepository.deleteByAssignment_AssignmentId(id);
                assignmentRepository.deleteById(id);
            }
            logInfo("Bulk deleted " + assignmentIds.size() + " assignments");
        } catch (Exception e) {
            logError("Error bulk deleting assignments: " + e.getMessage(), e);
            throw new RuntimeException("Failed to bulk delete assignments: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENT STATISTICS =============

    @Override
    public Map<String, Object> getAssignmentStatistics() {
        try {
            Map<String, Object> statistics = new HashMap<>();

            long totalAssignments = assignmentRepository.count();
            statistics.put("totalAssignments", totalAssignments);

            Map<String, Long> byStatus = getCountByStatus();
            statistics.put("assignmentsByStatus", byStatus);

            long activeAssignments = byStatus.getOrDefault("active", 0L);
            statistics.put("activeAssignments", activeAssignments);

            long completedAssignments = byStatus.getOrDefault("completed", 0L);
            statistics.put("completedAssignments", completedAssignments);

            long overdueAssignments = assignmentRepository.findOverdueAssignments(LocalDateTime.now()).size();
            statistics.put("overdueAssignments", overdueAssignments);

            long draftAssignments = assignmentRepository.findByPublishStatus(PublishStatus.DRAFT).size();
            statistics.put("draftAssignments", draftAssignments);

            long scheduledAssignments = assignmentRepository.findByPublishStatus(PublishStatus.SCHEDULED).size();
            statistics.put("scheduledAssignments", scheduledAssignments);

            long totalSubmissions = submissionRepository.count();
            statistics.put("totalSubmissions", totalSubmissions);

            return statistics;

        } catch (Exception e) {
            logError("Error getting assignment statistics: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get statistics: " + e.getMessage());
        }
    }

    // ============= GET COUNT BY STATUS =============

    @Override
    public Map<String, Long> getCountByStatus() {
        try {
            List<Object[]> results = assignmentRepository.countByStatus();
            Map<String, Long> countMap = new HashMap<>();

            for (Object[] result : results) {
                StatusType status = (StatusType) result[0];
                Long count = (Long) result[1];
                countMap.put(status.name(), count);
            }

            return countMap;

        } catch (Exception e) {
            logError("Error getting count by status: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get count: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENT ANALYTICS =============

    @Override
    public Map<String, Object> getAssignmentAnalytics(Long id) {
        try {
            AssignmentEntity assignment = assignmentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

            Map<String, Object> analytics = new HashMap<>();

            analytics.put("assignmentId", assignment.getAssignmentId());
            analytics.put("title", assignment.getTitle());
            analytics.put("publishStatus", assignment.getPublishStatus());
            analytics.put("publishedDate", assignment.getPublishedDate());

            List<Object[]> stats = submissionRepository.getSubmissionStats(id);
            if (stats != null && !stats.isEmpty()) {
                Object[] row = stats.get(0);
                analytics.put("totalStudents", getTotalStudentsForAssignment(assignment));
                analytics.put("submittedCount", row[1] != null ? row[1] : 0);
                analytics.put("lateCount", row[2] != null ? row[2] : 0);
                analytics.put("gradedCount", row[3] != null ? row[3] : 0);
                analytics.put("averageScore", row[4] != null ? Math.round((Double) row[4] * 100) / 100.0 : 0);
            }

            LocalDateTime now = LocalDateTime.now();
            if (assignment.getDueDate().isBefore(now)) {
                analytics.put("dueStatus", "overdue");
            } else if (assignment.getDueDate().minusDays(2).isBefore(now)) {
                analytics.put("dueStatus", "dueSoon");
            } else {
                analytics.put("dueStatus", "normal");
            }

            return analytics;

        } catch (Exception e) {
            logError("Error getting assignment analytics: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get analytics: " + e.getMessage());
        }
    }

    // ============= GRADE SUBMISSION =============

    @Override
    @Transactional
    public SubmissionResponseDto gradeSubmission(GradeRequestDto gradeRequest) {
        logInfo("Grading submission for assignment: " + gradeRequest.getAssignmentId() +
                ", student: " + gradeRequest.getStudentId());

        try {
            SubmissionEntity submission = submissionRepository
                    .findByAssignment_AssignmentIdAndStudent_StdId(
                            gradeRequest.getAssignmentId(), gradeRequest.getStudentId())
                    .orElseGet(() -> createNewSubmission(gradeRequest));

            AssignmentEntity assignment = submission.getAssignment();

            if (gradeRequest.getObtainedMarks() != null) {
                if (gradeRequest.getObtainedMarks() < 0 ||
                        gradeRequest.getObtainedMarks() > assignment.getTotalMarks()) {
                    throw new IllegalArgumentException("Marks must be between 0 and " + assignment.getTotalMarks());
                }
                submission.setObtainedMarks(gradeRequest.getObtainedMarks());
            }

            if (gradeRequest.getGrade() != null && !gradeRequest.getGrade().isEmpty()) {
                submission.setGrade(gradeRequest.getGrade());
            }

            submission.setTeacherFeedback(gradeRequest.getTeacherFeedback());
            submission.setStatus("graded");

            SubmissionEntity saved = submissionRepository.save(submission);

            // Mark notifications as completed
            try {
                notificationService.markAssignmentActionCompleted(gradeRequest.getStudentId(), gradeRequest.getAssignmentId());
            } catch (Exception e) {
                logError("Failed to mark notifications completed: " + e.getMessage(), e);
            }

            logInfo("Submission graded successfully for student: " + gradeRequest.getStudentId());

            return convertToSubmissionDto(saved);

        } catch (Exception e) {
            logError("Error grading submission: " + e.getMessage(), e);
            throw new RuntimeException("Failed to grade submission: " + e.getMessage());
        }
    }

    // ============= GET SUBMISSIONS BY ASSIGNMENT =============

    @Override
    public List<SubmissionResponseDto> getSubmissionsByAssignment(Long assignmentId) {
        try {
            return submissionRepository.findByAssignment_AssignmentId(assignmentId).stream()
                    .map(this::convertToSubmissionDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting submissions for assignment " + assignmentId + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get submissions: " + e.getMessage());
        }
    }

    // ============= GET SUBMISSIONS BY STUDENT =============

    @Override
    public List<SubmissionResponseDto> getSubmissionsByStudent(Long studentId) {
        try {
            return submissionRepository.findByStudent_StdId(studentId).stream()
                    .map(this::convertToSubmissionDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("Error getting submissions for student " + studentId + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get submissions: " + e.getMessage());
        }
    }

    // ============= GET SUBMISSION =============

    @Override
    public SubmissionResponseDto getSubmission(Long assignmentId, Long studentId) {
        try {
            return submissionRepository
                    .findByAssignment_AssignmentIdAndStudent_StdId(assignmentId, studentId)
                    .map(this::convertToSubmissionDto)
                    .orElse(null);
        } catch (Exception e) {
            logError("Error getting submission: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get submission: " + e.getMessage());
        }
    }

    // ============= GET SUBMISSION STATS =============

    @Override
    public Map<String, Object> getSubmissionStats(Long assignmentId) {
        try {
            Map<String, Object> stats = new HashMap<>();

            List<Object[]> results = submissionRepository.getSubmissionStats(assignmentId);
            if (results != null && !results.isEmpty()) {
                Object[] row = results.get(0);
                stats.put("total", row[0] != null ? row[0] : 0);
                stats.put("submitted", row[1] != null ? row[1] : 0);
                stats.put("late", row[2] != null ? row[2] : 0);
                stats.put("graded", row[3] != null ? row[3] : 0);
                stats.put("average", row[4] != null ? Math.round((Double) row[4] * 100) / 100.0 : 0);
            }

            return stats;

        } catch (Exception e) {
            logError("Error getting submission stats: " + e.getMessage(), e);
            throw new RuntimeException("Failed to get submission stats: " + e.getMessage());
        }
    }

    // ============= BULK UPDATE STATUS =============

    @Override
    @Transactional
    public List<AssignmentResponseDto> bulkUpdateStatus(List<Long> assignmentIds, String status) {
        try {
            StatusType statusType = convertToStatusType(status);
            List<AssignmentEntity> assignments = assignmentRepository.findAllById(assignmentIds);
            assignments.forEach(assignment -> {
                assignment.setStatus(statusType);
                assignment.setUpdatedAt(LocalDateTime.now());
            });
            List<AssignmentEntity> updated = assignmentRepository.saveAll(assignments);

            return updated.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logError("Error bulk updating status: " + e.getMessage(), e);
            throw new RuntimeException("Failed to bulk update status: " + e.getMessage());
        }
    }

    // ============= EXECUTE BULK ACTION =============

    @Override
    @Transactional
    public Map<String, Object> executeBulkAction(BulkActionDto bulkActionDto) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Long> ids = bulkActionDto.getAssignmentIds();
            String actionType = bulkActionDto.getActionType();

            switch (actionType) {
                case "change_status":
                    List<AssignmentResponseDto> updated = bulkUpdateStatus(ids, bulkActionDto.getNewStatus());
                    result.put("updatedCount", updated.size());
                    result.put("message", "Status updated successfully");
                    break;

                case "delete":
                    bulkDeleteAssignments(ids);
                    result.put("deletedCount", ids.size());
                    result.put("message", "Assignments deleted successfully");
                    break;

                default:
                    throw new IllegalArgumentException("Unknown action type: " + actionType);
            }

            result.put("success", true);

        } catch (Exception e) {
            logError("Error executing bulk action: " + e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // ============= UPLOAD ATTACHMENTS =============

    @Override
    @Transactional
    public AssignmentResponseDto uploadAttachments(Long id, List<MultipartFile> files) {
        try {
            AssignmentEntity assignment = assignmentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

            processAttachments(assignment, files);
            AssignmentEntity updated = assignmentRepository.save(assignment);

            return convertToDto(updated);

        } catch (Exception e) {
            logError("Error uploading attachments: " + e.getMessage(), e);
            throw new RuntimeException("Failed to upload attachments: " + e.getMessage());
        }
    }

    // ============= GET ATTACHMENT =============

    @Override
    public byte[] getAttachment(Long id, String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(id.toString()).resolve(fileName);
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            }
            return null;
        } catch (IOException e) {
            logError("Error reading attachment: " + e.getMessage(), e);
            return null;
        }
    }

    // ============= GET ASSIGNMENTS FOR STUDENT =============

    @Override
    public List<AssignmentResponseDto> getAssignmentsForStudent(Long studentId) {
        try {
            logInfo("Getting assignments for student ID: " + studentId);

            StudentEntity student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

            List<AssignmentEntity> assignments = assignmentRepository
                    .findAssignmentsForStudent(studentId, student.getCurrentClass(), student.getSection(), LocalDateTime.now());

            logInfo("Found " + assignments.size() + " assignments for student");

            return assignments.stream()
                    .map(this::convertToDtoWithStats)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logError("Error getting assignments for student " + studentId + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get assignments for student: " + e.getMessage());
        }
    }

    // ============= GET PENDING ASSIGNMENTS FOR STUDENT =============

    @Override
    public List<AssignmentResponseDto> getPendingAssignmentsForStudent(Long studentId) {
        try {
            logInfo("Getting pending assignments for student ID: " + studentId);

            List<AssignmentResponseDto> allAssignments = getAssignmentsForStudent(studentId);
            List<AssignmentResponseDto> pending = new ArrayList<>();

            for (AssignmentResponseDto assignment : allAssignments) {
                SubmissionResponseDto submission = getSubmission(assignment.getAssignmentId(), studentId);
                if (submission == null || !("submitted".equals(submission.getStatus()) ||
                        "graded".equals(submission.getStatus()))) {
                    pending.add(assignment);
                }
            }

            logInfo("Found " + pending.size() + " pending assignments for student");
            return pending;

        } catch (Exception e) {
            logError("Error getting pending assignments for student " + studentId + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get pending assignments: " + e.getMessage());
        }
    }

    // ============= GET COMPLETED ASSIGNMENTS FOR STUDENT =============

    @Override
    public List<AssignmentResponseDto> getCompletedAssignmentsForStudent(Long studentId) {
        try {
            logInfo("Getting completed assignments for student ID: " + studentId);

            List<SubmissionResponseDto> submissions = getSubmissionsByStudent(studentId);
            List<AssignmentResponseDto> completed = new ArrayList<>();

            for (SubmissionResponseDto submission : submissions) {
                AssignmentResponseDto assignment = getAssignmentById(submission.getAssignmentId());
                if (assignment != null) {
                    completed.add(assignment);
                }
            }

            logInfo("Found " + completed.size() + " completed assignments for student");
            return completed;

        } catch (Exception e) {
            logError("Error getting completed assignments for student " + studentId + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get completed assignments: " + e.getMessage());
        }
    }

    // ============= GET ASSIGNMENTS BY TEACHER =============

    @Override
    public List<AssignmentResponseDto> getAssignmentsByTeacher(Long teacherId) {
        try {
            logInfo("Getting assignments for teacher ID: " + teacherId);

            if (!teacherRepository.existsById(teacherId)) {
                throw new IllegalArgumentException("Teacher not found with ID: " + teacherId);
            }

            List<AssignmentEntity> assignments = assignmentRepository.findByCreatedByTeacher_Id(teacherId);

            logInfo("Found " + assignments.size() + " assignments for teacher");

            return assignments.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logError("Error getting assignments for teacher " + teacherId + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to get assignments for teacher: " + e.getMessage());
        }
    }

    // ============= PRIVATE HELPER METHODS =============

    private SubmissionEntity createNewSubmission(GradeRequestDto gradeRequest) {
        SubmissionEntity submission = new SubmissionEntity();

        AssignmentEntity assignment = assignmentRepository.findById(gradeRequest.getAssignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        StudentEntity student = studentRepository.findById(gradeRequest.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + gradeRequest.getStudentId()));

        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus("submitted");
        submission.setSubmittedDate(LocalDateTime.now());
        submission.setIsLate(LocalDateTime.now().isAfter(assignment.getDueDate()));

        return submission;
    }

    private List<Long> getAllStudentIdsForAssignment(AssignmentEntity assignment) {
        List<StudentEntity> students = studentRepository.findStudentsByClassAndSection(
                assignment.getClassName(),
                assignment.getSection().equals("All Sections") ? null : assignment.getSection()
        );
        return students.stream().map(StudentEntity::getStdId).collect(Collectors.toList());
    }

    private int getTotalStudentsForAssignment(AssignmentEntity assignment) {
        List<StudentEntity> students = studentRepository.findStudentsByClassAndSection(
                assignment.getClassName(),
                assignment.getSection().equals("All Sections") ? null : assignment.getSection()
        );
        return students.size();
    }

    private void processAttachments(AssignmentEntity assignment, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) return;

        List<String> fileNames = new ArrayList<>();
        Path assignmentDir = Paths.get(uploadDir).resolve(assignment.getAssignmentId().toString());

        if (!Files.exists(assignmentDir)) {
            Files.createDirectories(assignmentDir);
        }

        for (MultipartFile file : files) {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = assignmentDir.resolve(fileName);
            Files.write(filePath, file.getBytes());
            fileNames.add(fileName);
        }

        assignment.setAttachments(fileNames);
    }

    // ============= CONVERSION METHODS =============

    private AssignmentEntity convertToEntity(AssignmentRequestDto dto) {
        AssignmentEntity entity = new AssignmentEntity();

        entity.setTitle(dto.getTitle());
        entity.setSubject(dto.getSubject());
        entity.setClassName(dto.getClassName());
        entity.setSection(dto.getSection());
        entity.setDescription(dto.getDescription());

        entity.setGradingType(convertToGradingType(dto.getGradingType()));
        entity.setTotalMarks(dto.getTotalMarks());
        entity.setStartDate(dto.getStartDate());
        entity.setDueDate(dto.getDueDate());
        entity.setAllowLateSubmission(dto.getAllowLateSubmission());
        entity.setAllowResubmission(dto.getAllowResubmission());

        entity.setPriority(convertToPriorityType(dto.getPriority()));
        entity.setAssignTo(convertToAssignToType(dto.getAssignTo()));

        if (dto.getAssignedClasses() != null) {
            entity.setAssignedClasses(dto.getAssignedClasses());
        }

        if (dto.getAssignedStudents() != null) {
            entity.setAssignedStudents(dto.getAssignedStudents());
        }

        entity.setExternalLink(dto.getExternalLink());
        entity.setNotifyStudents(dto.getNotifyStudents());
        entity.setNotifyParents(dto.getNotifyParents());
        entity.setSendReminders(dto.getSendReminders());
        entity.setSendLateWarnings(dto.getSendLateWarnings());

        entity.setStatus(convertToStatusType(dto.getStatus()));

        entity.setAcademicYear(dto.getAcademicYear());
        entity.setTerm(dto.getTerm());

        return entity;
    }

    private void updateAssignmentFields(AssignmentEntity entity, AssignmentRequestDto dto) {
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getSubject() != null) entity.setSubject(dto.getSubject());
        if (dto.getClassName() != null) entity.setClassName(dto.getClassName());
        if (dto.getSection() != null) entity.setSection(dto.getSection());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());

        if (dto.getGradingType() != null) {
            entity.setGradingType(convertToGradingType(dto.getGradingType()));
        }

        if (dto.getTotalMarks() != null) entity.setTotalMarks(dto.getTotalMarks());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getDueDate() != null) entity.setDueDate(dto.getDueDate());
        if (dto.getAllowLateSubmission() != null) entity.setAllowLateSubmission(dto.getAllowLateSubmission());
        if (dto.getAllowResubmission() != null) entity.setAllowResubmission(dto.getAllowResubmission());

        if (dto.getPriority() != null) {
            entity.setPriority(convertToPriorityType(dto.getPriority()));
        }

        if (dto.getAssignTo() != null) {
            entity.setAssignTo(convertToAssignToType(dto.getAssignTo()));
        }

        if (dto.getAssignedClasses() != null) entity.setAssignedClasses(dto.getAssignedClasses());
        if (dto.getAssignedStudents() != null) entity.setAssignedStudents(dto.getAssignedStudents());
        if (dto.getExternalLink() != null) entity.setExternalLink(dto.getExternalLink());
        if (dto.getNotifyStudents() != null) entity.setNotifyStudents(dto.getNotifyStudents());
        if (dto.getNotifyParents() != null) entity.setNotifyParents(dto.getNotifyParents());
        if (dto.getSendReminders() != null) entity.setSendReminders(dto.getSendReminders());
        if (dto.getSendLateWarnings() != null) entity.setSendLateWarnings(dto.getSendLateWarnings());

        if (dto.getStatus() != null) {
            entity.setStatus(convertToStatusType(dto.getStatus()));
        }

        if (dto.getAcademicYear() != null) entity.setAcademicYear(dto.getAcademicYear());
        if (dto.getTerm() != null) entity.setTerm(dto.getTerm());
    }

    private AssignmentResponseDto convertToDto(AssignmentEntity entity) {
        AssignmentResponseDto dto = new AssignmentResponseDto();

        dto.setAssignmentId(entity.getAssignmentId());
        dto.setAssignmentCode(entity.getAssignmentCode());
        dto.setTitle(entity.getTitle());
        dto.setSubject(entity.getSubject());
        dto.setClassName(entity.getClassName());
        dto.setSection(entity.getSection());
        dto.setDescription(entity.getDescription());

        dto.setGradingType(entity.getGradingType());
        dto.setTotalMarks(entity.getTotalMarks());
        dto.setStartDate(entity.getStartDate());
        dto.setDueDate(entity.getDueDate());
        dto.setAllowLateSubmission(entity.getAllowLateSubmission());
        dto.setAllowResubmission(entity.getAllowResubmission());

        dto.setPriority(entity.getPriority());
        dto.setAssignTo(entity.getAssignTo());

        // New publish fields
        dto.setPublishStatus(entity.getPublishStatus());
        dto.setScheduledPublishDate(entity.getScheduledPublishDate());
        dto.setPublishedDate(entity.getPublishedDate());
        dto.setPublishedBy(entity.getPublishedBy());

        if (entity.getAssignedClasses() != null) {
            dto.setAssignedClasses(new ArrayList<>(entity.getAssignedClasses()));
        }

        if (entity.getAssignedStudents() != null) {
            dto.setAssignedStudents(new ArrayList<>(entity.getAssignedStudents()));
        }

        if (entity.getAttachments() != null) {
            dto.setAttachments(new ArrayList<>(entity.getAttachments()));

            List<String> urls = entity.getAttachments().stream()
                    .map(file -> "/api/assignments/" + entity.getAssignmentId() + "/attachments/" + file)
                    .collect(Collectors.toList());
            dto.setAttachmentUrls(urls);
        }

        dto.setExternalLink(entity.getExternalLink());
        dto.setNotifyStudents(entity.getNotifyStudents());
        dto.setNotifyParents(entity.getNotifyParents());
        dto.setSendReminders(entity.getSendReminders());
        dto.setSendLateWarnings(entity.getSendLateWarnings());

        dto.setStatus(entity.getStatus());

        if (entity.getCreatedByTeacher() != null) {
            dto.setCreatedByTeacherId(entity.getCreatedByTeacher().getId());
            dto.setCreatedByName(entity.getCreatedByName());
        }

        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setAcademicYear(entity.getAcademicYear());
        dto.setTerm(entity.getTerm());

        return dto;
    }

    private AssignmentResponseDto convertToDtoWithStats(AssignmentEntity entity) {
        AssignmentResponseDto dto = convertToDto(entity);

        // Only calculate stats for published assignments
        if (entity.getPublishStatus() == PublishStatus.PUBLISHED) {
            Long assignmentId = entity.getAssignmentId();

            long totalStudents = getTotalStudentsForAssignment(entity);
            long submitted = submissionRepository.countByAssignment_AssignmentIdAndStatus(assignmentId, "submitted") +
                    submissionRepository.countByAssignment_AssignmentIdAndStatus(assignmentId, "graded");
            long late = submissionRepository.countByAssignment_AssignmentIdAndIsLateTrue(assignmentId);
            long graded = submissionRepository.countByAssignment_AssignmentIdAndStatus(assignmentId, "graded");

            dto.setTotalStudents((int) totalStudents);
            dto.setSubmittedCount((int) submitted);
            dto.setPendingCount((int) (totalStudents - submitted));
            dto.setLateCount((int) late);
            dto.setGradedCount((int) graded);

            if (totalStudents > 0) {
                double rate = (submitted * 100.0) / totalStudents;
                dto.setSubmissionRate(Math.round(rate * 100) / 100.0);
            } else {
                dto.setSubmissionRate(0.0);
            }

            List<Object[]> stats = submissionRepository.getSubmissionStats(assignmentId);
            if (stats != null && !stats.isEmpty() && stats.get(0)[4] != null) {
                dto.setAverageScore((Double) stats.get(0)[4]);
            }
        }

        // Set visibility for students
        dto.setIsVisible(entity.isVisibleToStudents());

        return dto;
    }

    private SubmissionResponseDto convertToSubmissionDto(SubmissionEntity entity) {
        SubmissionResponseDto dto = new SubmissionResponseDto();

        dto.setSubmissionId(entity.getSubmissionId());
        dto.setAssignmentId(entity.getAssignment().getAssignmentId());
        dto.setAssignmentTitle(entity.getAssignment().getTitle());

        if (entity.getStudent() != null) {
            dto.setStudentId(entity.getStudent().getStdId());
            dto.setStudentName(entity.getStudentName());
            dto.setRollNumber(entity.getRollNumber());
            dto.setStudentClass(entity.getStudentClass());
            dto.setStudentSection(entity.getStudentSection());
        }

        dto.setSubmittedDate(entity.getSubmittedDate());

        if (entity.getFiles() != null) {
            dto.setFiles(new ArrayList<>(entity.getFiles()));
        }

        dto.setStatus(entity.getStatus());
        dto.setObtainedMarks(entity.getObtainedMarks());
        dto.setGrade(entity.getGrade());
        dto.setTeacherFeedback(entity.getTeacherFeedback());
        dto.setIsLate(entity.getIsLate());
        dto.setIsResubmission(entity.getIsResubmission());
        dto.setRecheckRequested(entity.getRecheckRequested());
        dto.setCreatedAt(entity.getCreatedAt());

        return dto;
    }
}