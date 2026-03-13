package com.SIGMA.USCO.documents.dto;

import com.SIGMA.USCO.documents.entity.enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailDocumentDTO {

    private Long requiredDocumentId;

    private Long studentDocumentId;

    private String documentName;
    private DocumentType documentType;

    private String status;
    private String statusDescription;

    private String notes;
    private LocalDateTime lastUpdate;

    private boolean uploaded;

}
