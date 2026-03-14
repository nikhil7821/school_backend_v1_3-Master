package com.sc.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkActionDto {

    private List<Long> assignmentIds;
    private String actionType;           // change_status, send_reminders, delete, export
    private String newStatus;             // for change_status action
    private String reminderType;          // due_date, late_submission
    private String customMessage;
    private String exportFormat;          // excel, pdf
    private Boolean includeSubmissions = false;
    private Boolean includeGrades = false;
}