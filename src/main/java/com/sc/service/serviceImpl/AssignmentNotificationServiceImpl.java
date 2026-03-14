package com.sc.service.serviceImpl;

import com.sc.entity.AssignmentEntity;
import com.sc.entity.AssignmentNotificationEntity;
import com.sc.entity.StudentEntity;
import com.sc.repository.AssignmentNotificationRepository;
import com.sc.repository.AssignmentRepository;
import com.sc.repository.StudentRepository;
import com.sc.repository.SubmissionRepository;
import com.sc.service.AssignmentNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssignmentNotificationServiceImpl implements AssignmentNotificationService {

    @Autowired
    private AssignmentNotificationRepository assignmentNotificationRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

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

    // ============= 📨 NOTIFICATION ON ASSIGNMENT CREATION =============

    @Transactional
    public void notifyStudentsAboutNewAssignment(AssignmentEntity assignment, List<Long> studentIds) {
        logInfo("📨 Sending new assignment notifications to " + studentIds.size() + " students");

        int successCount = 0;

        for (Long studentId : studentIds) {
            try {
                StudentEntity student = studentRepository.findById(studentId).orElse(null);
                if (student == null) continue;

                // Check if already notified
                List<AssignmentNotificationEntity> existing = assignmentNotificationRepository
                        .findByStudentAndAssignment(studentId, assignment.getAssignmentId());

                if (!existing.isEmpty()) {
                    logDebug("Student " + studentId + " already notified about assignment " + assignment.getAssignmentId());
                    continue;
                }

                // Create notification
                AssignmentNotificationEntity notification = new AssignmentNotificationEntity();
                notification.setStudent(student);
                notification.setTitle("📝 New Assignment: " + assignment.getTitle());
                notification.setMessage(String.format(
                        "A new assignment '%s' has been created for %s %s. Due date: %s",
                        assignment.getTitle(),
                        assignment.getClassName(),
                        assignment.getSection(),
                        formatDate(assignment.getDueDate())
                ));
                notification.setNotificationType("ASSIGNMENT");
                notification.setChannel("IN_APP");
                notification.setPriority(getPriorityString(assignment.getPriority()));
                notification.setStatus("SENT");
                notification.setSentDate(new Date());

                // Assignment specific fields
                notification.setAssignmentId(assignment.getAssignmentId());
                notification.setAssignmentTitle(assignment.getTitle());
                notification.setDueDate(Date.from(assignment.getDueDate().atZone(ZoneId.systemDefault()).toInstant()));

                notification.setActionRequired(true);

                Date expiryDate = Date.from(assignment.getDueDate().plusDays(7)
                        .atZone(ZoneId.systemDefault()).toInstant());
                notification.setExpiryDate(expiryDate);

                notification.setActionUrl("/student/assignments/" + assignment.getAssignmentId());
                notification.setActionButtonText("View Assignment");

                assignmentNotificationRepository.save(notification);
                successCount++;

            } catch (Exception e) {
                logError("Failed to send notification to student " + studentId + ": " + e.getMessage(), e);
            }
        }

        logInfo("✅ Sent " + successCount + " new assignment notifications");
    }

    // ============= ⏰ SCHEDULED REMINDERS =============

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void sendDueDateReminders() {
        logInfo("⏰ Checking for assignments due in next 24 hours...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.plusHours(24);

        List<AssignmentEntity> assignmentsDueSoon = assignmentRepository
                .findByDueDateBetween(now, tomorrow);

        logInfo("Found " + assignmentsDueSoon.size() + " assignments due in next 24 hours");

        for (AssignmentEntity assignment : assignmentsDueSoon) {
            try {
                sendRemindersForAssignment(assignment, "DUE_SOON");
            } catch (Exception e) {
                logError("Error sending due reminders for assignment " +
                        assignment.getAssignmentId() + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    @Transactional
    public void sendOverdueReminders() {
        logInfo("⚠️ Checking for overdue assignments...");

        LocalDateTime now = LocalDateTime.now();
        List<AssignmentEntity> overdueAssignments = assignmentRepository
                .findOverdueAssignments(now);

        logInfo("Found " + overdueAssignments.size() + " overdue assignments");

        for (AssignmentEntity assignment : overdueAssignments) {
            try {
                sendRemindersForAssignment(assignment, "OVERDUE");
            } catch (Exception e) {
                logError("Error sending overdue reminders for assignment " +
                        assignment.getAssignmentId() + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 8 * * MON") // Every Monday at 8 AM
    @Transactional
    public void sendWeeklyPendingSummary() {
        logInfo("📊 Sending weekly pending assignments summary...");

        List<StudentEntity> allStudents = studentRepository.findAll();

        for (StudentEntity student : allStudents) {
            try {
                sendPendingSummaryToStudent(student);
            } catch (Exception e) {
                logError("Error sending summary to student " +
                        student.getStdId() + ": " + e.getMessage(), e);
            }
        }
    }

    // ============= 🎯 HELPER METHODS FOR REMINDERS =============

    private void sendRemindersForAssignment(AssignmentEntity assignment, String reminderType) {
        List<StudentEntity> students = studentRepository.findStudentsByClassAndSection(
                assignment.getClassName(),
                assignment.getSection().equals("All Sections") ? null : assignment.getSection()
        );

        List<Long> submittedStudentIds = submissionRepository
                .findByAssignment_AssignmentId(assignment.getAssignmentId())
                .stream()
                .map(s -> s.getStudent().getStdId())
                .collect(Collectors.toList());

        for (StudentEntity student : students) {
            if (!submittedStudentIds.contains(student.getStdId())) {
                sendReminderToStudent(assignment, student, reminderType);
            }
        }
    }

    private void sendReminderToStudent(AssignmentEntity assignment, StudentEntity student, String reminderType) {
        try {
            // Check if already sent in last 24 hours
            Date last24Hours = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            List<AssignmentNotificationEntity> recent = assignmentNotificationRepository
                    .findByStudentAndAssignment(student.getStdId(), assignment.getAssignmentId())
                    .stream()
                    .filter(n -> n.getCreatedAt() != null && n.getCreatedAt().after(last24Hours))
                    .collect(Collectors.toList());

            if (!recent.isEmpty()) {
                return;
            }

            AssignmentNotificationEntity notification = new AssignmentNotificationEntity();
            notification.setStudent(student);

            if ("DUE_SOON".equals(reminderType)) {
                notification.setTitle("⏰ Assignment Due Soon: " + assignment.getTitle());
                notification.setMessage(String.format(
                        "Your assignment '%s' is due in less than 24 hours (Due: %s). Please submit soon!",
                        assignment.getTitle(),
                        formatDate(assignment.getDueDate())
                ));
                notification.setNotificationType("ASSIGNMENT_DUE_SOON");
            } else if ("OVERDUE".equals(reminderType)) {
                notification.setTitle("⚠️ Overdue Assignment: " + assignment.getTitle());
                notification.setMessage(String.format(
                        "Your assignment '%s' was due on %s. Please submit as soon as possible.",
                        assignment.getTitle(),
                        formatDate(assignment.getDueDate())
                ));
                notification.setNotificationType("ASSIGNMENT_OVERDUE");
                notification.setPriority("HIGH");
            }

            notification.setChannel("IN_APP");
            notification.setStatus("SENT");
            notification.setSentDate(new Date());

            // Assignment specific fields
            notification.setAssignmentId(assignment.getAssignmentId());
            notification.setAssignmentTitle(assignment.getTitle());
            notification.setDueDate(Date.from(assignment.getDueDate().atZone(ZoneId.systemDefault()).toInstant()));

            notification.setActionRequired(true);
            notification.setActionUrl("/student/assignments/" + assignment.getAssignmentId());
            notification.setActionButtonText("Submit Now");

            assignmentNotificationRepository.save(notification);
            logInfo("Sent " + reminderType + " reminder to student " + student.getStdId() +
                    " for assignment " + assignment.getAssignmentId());

        } catch (Exception e) {
            logError("Error sending reminder to student " + student.getStdId() + ": " + e.getMessage(), e);
        }
    }

    private void sendPendingSummaryToStudent(StudentEntity student) {
        List<AssignmentEntity> allAssignments = assignmentRepository
                .findAssignmentsForStudent(student.getStdId(), student.getCurrentClass(), student.getSection(), LocalDateTime.now());

        List<Long> submittedIds = submissionRepository
                .findByStudent_StdId(student.getStdId())
                .stream()
                .map(s -> s.getAssignment().getAssignmentId())
                .collect(Collectors.toList());

        List<AssignmentEntity> pendingAssignments = allAssignments.stream()
                .filter(a -> !submittedIds.contains(a.getAssignmentId()))
                .collect(Collectors.toList());

        if (pendingAssignments.isEmpty()) {
            return;
        }

        AssignmentNotificationEntity notification = new AssignmentNotificationEntity();
        notification.setStudent(student);
        notification.setTitle("📋 Weekly Summary: Pending Assignments");
        notification.setMessage(String.format(
                "You have %d pending assignment(s) this week. Please check your dashboard for details.",
                pendingAssignments.size()
        ));
        notification.setNotificationType("WEEKLY_SUMMARY");
        notification.setChannel("IN_APP");
        notification.setStatus("SENT");
        notification.setSentDate(new Date());
        notification.setActionRequired(false);
        notification.setActionUrl("/student/assignments/pending");
        notification.setActionButtonText("View Pending");

        assignmentNotificationRepository.save(notification);
        logInfo("Sent weekly summary to student " + student.getStdId() +
                " (" + pendingAssignments.size() + " pending)");
    }

    // ============= 📊 PUBLIC METHODS =============

    @Transactional
    public void markAssignmentNotificationAsRead(Long studentId, Long assignmentId) {
        List<AssignmentNotificationEntity> notifications = assignmentNotificationRepository
                .findByStudentAndAssignment(studentId, assignmentId);

        for (AssignmentNotificationEntity notification : notifications) {
            if (notification.getReadAt() == null) {
                notification.setReadAt(new Date());
                notification.setStatus("READ");
                assignmentNotificationRepository.save(notification);
            }
        }
    }

    @Transactional
    public void markAssignmentActionCompleted(Long studentId, Long assignmentId) {
        List<AssignmentNotificationEntity> notifications = assignmentNotificationRepository
                .findByStudentAndAssignment(studentId, assignmentId);

        for (AssignmentNotificationEntity notification : notifications) {
            notification.setActionCompleted(true);
            assignmentNotificationRepository.save(notification);
        }
    }

    public long getUnreadNotificationCount(Long studentId) {
        return assignmentNotificationRepository.countUnreadByStudentId(studentId);
    }

    // ============= 🔧 UTILITY METHODS =============

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        return dateTime.format(formatter);
    }

    private String getPriorityString(Object priority) {
        if (priority == null) return "MEDIUM";
        return priority.toString().toUpperCase();
    }
}