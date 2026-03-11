package com.sc.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "classes")
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "class_id")
    private Long classId;

    @Column(name = "class_name", nullable = false)
    private String className;

    @Column(name = "class_code", unique = true, nullable = false)
    private String classCode;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "section", nullable = false)
    private String section;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "current_students")
    private Integer currentStudents;

    @Column(name = "room_number")
    private String roomNumber;

    @Column(name = "start_time")
    private String startTime;

    @Column(name = "end_time")
    private String endTime;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "class_teacher_id")
    private Long classTeacherId;

    @Column(name = "class_teacher_subject")
    private String classTeacherSubject;

    @Column(name = "assistant_teacher_id")
    private Long assistantTeacherId;

    @Column(name = "assistant_teacher_subject")
    private String assistantTeacherSubject;

    @Column(name = "other_teacher_subject_json", columnDefinition = "TEXT")
    private String otherTeacherSubjectJson;

    @Column(name = "working_days_json", columnDefinition = "TEXT")
    private String workingDaysJson;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // Transient fields for easy access
    @Transient
    private List<TeacherSubjectAssignment> otherTeacherSubject = new ArrayList<>();

    @Transient
    private List<String> workingDays = new ArrayList<>();


    //============== StudentClassEnrollment CONNECTION ===================//
    @OneToMany(mappedBy = "classEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StudentClassEnrollment> enrollments = new ArrayList<>();

    public void addEnrollment(StudentClassEnrollment enrollment) {
        enrollments.add(enrollment);
        enrollment.setClassEntity(this);
    }

    // Constructors
    public ClassEntity() {
        this.otherTeacherSubject = new ArrayList<>();
        this.workingDays = new ArrayList<>();
    }

    public ClassEntity(String className, String classCode, String academicYear, String section) {
        this.className = className;
        this.classCode = classCode;
        this.academicYear = academicYear;
        this.section = section;
        this.otherTeacherSubject = new ArrayList<>();
        this.workingDays = new ArrayList<>();
    }

    // Lifecycle methods
    @PostLoad
    private void onLoad() {
        loadOtherTeacherSubjectFromJson();
        loadWorkingDaysFromJson();
    }

    @PrePersist
    public void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
        saveOtherTeacherSubjectToJson();
        saveWorkingDaysToJson();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = new Date();
        saveOtherTeacherSubjectToJson();
        saveWorkingDaysToJson();
    }

    // ────────────────────────────────────────────────
    // JSON Helper Methods for otherTeacherSubject (UPDATED)
    // ────────────────────────────────────────────────

    private void loadOtherTeacherSubjectFromJson() {
        if (otherTeacherSubjectJson != null && !otherTeacherSubjectJson.isEmpty()) {
            try {
                otherTeacherSubject = parseOtherTeacherSubjectJson(otherTeacherSubjectJson);
            } catch (Exception e) {
                otherTeacherSubject = new ArrayList<>();
            }
        } else {
            otherTeacherSubject = new ArrayList<>();
        }
    }

    private void saveOtherTeacherSubjectToJson() {
        if (otherTeacherSubject != null && !otherTeacherSubject.isEmpty()) {
            otherTeacherSubjectJson = convertOtherTeacherSubjectToJson(otherTeacherSubject);
        } else {
            otherTeacherSubjectJson = "[]";
        }
    }

    private List<TeacherSubjectAssignment> parseOtherTeacherSubjectJson(String json) {
        List<TeacherSubjectAssignment> list = new ArrayList<>();

        if (json == null || json.trim().isEmpty() || json.trim().equals("[]")) {
            return list;
        }

        try {
            json = json.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) return list;
            json = json.substring(1, json.length() - 1).trim();

            // Split teacher objects (naive but works for your structure)
            String[] teacherBlocks = json.split("(?<=\\}),\\s*(?=\\{)");

            for (String block : teacherBlocks) {
                block = block.trim();
                if (block.isEmpty()) continue;

                String teacherId = null;
                String teacherName = null;
                List<SubjectDetail> subjects = new ArrayList<>();

                // Extract fields
                int subjectsStart = block.indexOf("\"subjects\":");
                if (subjectsStart == -1) continue;

                String beforeSubjects = block.substring(0, subjectsStart);
                String subjectsPart = block.substring(subjectsStart);

                // Parse teacherId and teacherName
                String[] beforeParts = beforeSubjects.split(",");
                for (String part : beforeParts) {
                    part = part.trim();
                    if (part.contains("\"teacherId\"")) {
                        teacherId = extractStringValue(part);
                    } else if (part.contains("\"teacherName\"")) {
                        teacherName = extractStringValue(part);
                    }
                }

                // Parse subjects array
                int arrayStart = subjectsPart.indexOf('[');
                int arrayEnd = subjectsPart.lastIndexOf(']');
                if (arrayStart == -1 || arrayEnd == -1) continue;

                String subjectsContent = subjectsPart.substring(arrayStart + 1, arrayEnd).trim();

                if (!subjectsContent.isEmpty()) {
                    String[] subjectItems = subjectsContent.split("(?<=\\}),\\s*(?=\\{)");
                    for (String item : subjectItems) {
                        item = item.trim();
                        if (item.startsWith(",")) item = item.substring(1).trim();

                        Integer subId = null;
                        String subjectName = null;
                        Integer totalMarks = null;

                        String[] fields = item.split(",");
                        for (String f : fields) {
                            f = f.trim();
                            if (f.contains("\"sub_id\"")) {
                                String val = extractValue(f);
                                try { subId = Integer.parseInt(val); } catch (Exception ignored) {}
                            } else if (f.contains("\"subjectName\"")) {
                                subjectName = extractStringValue(f);
                            } else if (f.contains("\"totalMarks\"")) {
                                String val = extractValue(f);
                                try { totalMarks = Integer.parseInt(val); } catch (Exception ignored) {}
                            }
                        }

                        if (subjectName != null) {
                            subjects.add(new SubjectDetail(subId, subjectName, totalMarks));
                        }
                    }
                }

                if (teacherId != null && teacherName != null) {
                    list.add(new TeacherSubjectAssignment(teacherId, teacherName, subjects));
                }
            }
        } catch (Exception e) {
            // In production → log
            return new ArrayList<>();
        }

        return list;
    }

    private String convertOtherTeacherSubjectToJson(List<TeacherSubjectAssignment> list) {
        if (list == null || list.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (TeacherSubjectAssignment ts : list) {
            if (!first) sb.append(",");
            first = false;

            sb.append("{")
                    .append("\"teacherId\":\"").append(escape(ts.getTeacherId())).append("\",")
                    .append("\"teacherName\":\"").append(escape(ts.getTeacherName())).append("\",")
                    .append("\"subjects\":[");

            boolean firstSub = true;
            for (SubjectDetail sub : ts.getSubjects()) {
                if (!firstSub) sb.append(",");
                firstSub = false;

                sb.append("{")
                        .append("\"sub_id\":").append(sub.getSubId() != null ? sub.getSubId() : "null").append(",")
                        .append("\"subjectName\":\"").append(escape(sub.getSubjectName())).append("\",")
                        .append("\"totalMarks\":").append(sub.getTotalMarks() != null ? sub.getTotalMarks() : "null")
                        .append("}");
            }

            sb.append("]}");
        }

        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private String extractStringValue(String field) {
        int colon = field.indexOf(':');
        if (colon == -1) return null;
        String val = field.substring(colon + 1).trim();
        if (val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        return null;
    }

    private String extractValue(String field) {
        int colon = field.indexOf(':');
        if (colon == -1) return null;
        return field.substring(colon + 1).trim();
    }

    // ────────────────────────────────────────────────
    // JSON Helper Methods for workingDays (unchanged)
    // ────────────────────────────────────────────────

    private void loadWorkingDaysFromJson() {
        if (workingDaysJson != null && !workingDaysJson.isEmpty()) {
            try {
                workingDays = parseWorkingDaysJson(workingDaysJson);
            } catch (Exception e) {
                workingDays = new ArrayList<>();
            }
        } else {
            workingDays = new ArrayList<>();
        }
    }

    private void saveWorkingDaysToJson() {
        if (workingDays != null && !workingDays.isEmpty()) {
            workingDaysJson = convertWorkingDaysToJson(workingDays);
        } else {
            workingDaysJson = "[]";
        }
    }

    private List<String> parseWorkingDaysJson(String json) {
        List<String> list = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) return list;

        try {
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
                if (!json.isEmpty()) {
                    String[] days = json.split(",");
                    for (String day : days) {
                        list.add(day.trim().replace("\"", ""));
                    }
                }
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return list;
    }

    private String convertWorkingDaysToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String day : list) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(day).append("\"");
        }
        json.append("]");
        return json.toString();
    }

    // ────────────────────────────────────────────────
    // Updated Inner Classes
    // ────────────────────────────────────────────────

    public static class TeacherSubjectAssignment {
        private String teacherId;
        private String teacherName;
        private List<SubjectDetail> subjects;

        public TeacherSubjectAssignment() {
            this.subjects = new ArrayList<>();
        }

        public TeacherSubjectAssignment(String teacherId, String teacherName, List<SubjectDetail> subjects) {
            this.teacherId = teacherId;
            this.teacherName = teacherName;
            this.subjects = subjects != null ? subjects : new ArrayList<>();
        }

        public String getTeacherId() { return teacherId; }
        public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

        public String getTeacherName() { return teacherName; }
        public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

        public List<SubjectDetail> getSubjects() { return subjects; }
        public void setSubjects(List<SubjectDetail> subjects) {
            this.subjects = subjects != null ? subjects : new ArrayList<>();
        }
    }

    public static class SubjectDetail {
        private Integer subId;
        private String subjectName;
        private Integer totalMarks;

        public SubjectDetail() {}

        public SubjectDetail(Integer subId, String subjectName, Integer totalMarks) {
            this.subId = subId;
            this.subjectName = subjectName;
            this.totalMarks = totalMarks;
        }

        public Integer getSubId() { return subId; }
        public void setSubId(Integer subId) { this.subId = subId; }

        public String getSubjectName() { return subjectName; }
        public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

        public Integer getTotalMarks() { return totalMarks; }
        public void setTotalMarks(Integer totalMarks) { this.totalMarks = totalMarks; }
    }

    // ────────────────────────────────────────────────
    // Business Methods – updated add method
    // ────────────────────────────────────────────────

    public void addWorkingDay(String day) {
        if (!workingDays.contains(day)) {
            workingDays.add(day);
        }
    }

    public void removeWorkingDay(String day) {
        workingDays.remove(day);
    }

    // Updated – now takes full subject info
    public void addOtherTeacherAssignment(String teacherId, String teacherName, Integer subId, String subjectName, Integer totalMarks) {
        for (TeacherSubjectAssignment assignment : otherTeacherSubject) {
            if (assignment.getTeacherId().equals(teacherId)) {
                // Avoid duplicate subject names (you can change logic)
                boolean exists = assignment.getSubjects().stream()
                        .anyMatch(s -> s.getSubjectName().equalsIgnoreCase(subjectName));
                if (!exists) {
                    assignment.getSubjects().add(new SubjectDetail(subId, subjectName, totalMarks));
                }
                return;
            }
        }

        // New teacher
        List<SubjectDetail> subs = new ArrayList<>();
        subs.add(new SubjectDetail(subId, subjectName, totalMarks));
        otherTeacherSubject.add(new TeacherSubjectAssignment(teacherId, teacherName, subs));
    }

    public void removeOtherTeacherAssignment(String teacherId, String subjectName) {
        otherTeacherSubject.removeIf(assignment ->
                assignment.getTeacherId().equals(teacherId) &&
                        assignment.getSubjects().stream().anyMatch(s -> s.getSubjectName().equals(subjectName))
        );
    }

    // ────────────────────────────────────────────────
    // Getters & Setters (mostly unchanged)
    // ────────────────────────────────────────────────

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }

    public Integer getCurrentStudents() { return currentStudents; }
    public void setCurrentStudents(Integer currentStudents) { this.currentStudents = currentStudents; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getClassTeacherId() { return classTeacherId; }
    public void setClassTeacherId(Long classTeacherId) { this.classTeacherId = classTeacherId; }

    public String getClassTeacherSubject() { return classTeacherSubject; }
    public void setClassTeacherSubject(String classTeacherSubject) { this.classTeacherSubject = classTeacherSubject; }

    public Long getAssistantTeacherId() { return assistantTeacherId; }
    public void setAssistantTeacherId(Long assistantTeacherId) { this.assistantTeacherId = assistantTeacherId; }

    public String getAssistantTeacherSubject() { return assistantTeacherSubject; }
    public void setAssistantTeacherSubject(String assistantTeacherSubject) { this.assistantTeacherSubject = assistantTeacherSubject; }

    public String getOtherTeacherSubjectJson() { return otherTeacherSubjectJson; }
    public void setOtherTeacherSubjectJson(String json) {
        this.otherTeacherSubjectJson = json;
        loadOtherTeacherSubjectFromJson();
    }

    public String getWorkingDaysJson() { return workingDaysJson; }
    public void setWorkingDaysJson(String json) {
        this.workingDaysJson = json;
        loadWorkingDaysFromJson();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status != null ? status : "ACTIVE"; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public List<TeacherSubjectAssignment> getOtherTeacherSubject() { return otherTeacherSubject; }
    public void setOtherTeacherSubject(List<TeacherSubjectAssignment> assignments) {
        this.otherTeacherSubject = assignments != null ? assignments : new ArrayList<>();
    }

    public List<String> getWorkingDays() { return workingDays; }
    public void setWorkingDays(List<String> days) {
        this.workingDays = days != null ? days : new ArrayList<>();
    }


    public Boolean getDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public List<StudentClassEnrollment> getEnrollments() {
        return enrollments;
    }

    public void setEnrollments(List<StudentClassEnrollment> enrollments) {
        this.enrollments = enrollments;
    }
}