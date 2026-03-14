package com.sc.dto.response;

import com.sc.dto.request.SubjectDetailDTO;

import java.util.List;

public class ClassSubjectsResponseDTO {
    private String className;
    private String section;
    private String classCode;
    private List<SubjectDetailDTO> subjects;
    private int totalSubjects;

    // Constructors
    public ClassSubjectsResponseDTO() {}

    public ClassSubjectsResponseDTO(String className, String section, String classCode,
                                    List<SubjectDetailDTO> subjects, int totalSubjects) {
        this.className = className;
        this.section = section;
        this.classCode = classCode;
        this.subjects = subjects;
        this.totalSubjects = totalSubjects;
    }

    // Getters and Setters
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getClassCode() { return classCode; }
    public void setClassCode(String classCode) { this.classCode = classCode; }

    public List<SubjectDetailDTO> getSubjects() { return subjects; }
    public void setSubjects(List<SubjectDetailDTO> subjects) { this.subjects = subjects; }

    public int getTotalSubjects() { return totalSubjects; }
    public void setTotalSubjects(int totalSubjects) { this.totalSubjects = totalSubjects; }
}