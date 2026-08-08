package com.lsn.ragkb.config;

import com.lsn.ragkb.entity.KbDocument;
import com.lsn.ragkb.entity.KnowledgeBase;
import com.lsn.ragkb.repository.KbDocumentRepository;
import com.lsn.ragkb.repository.KnowledgeBaseRepository;
import com.lsn.ragkb.service.document.DocumentLoaderService;
import com.lsn.ragkb.service.document.loader.ParseResult;
import com.lsn.ragkb.service.index.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class SalesKnowledgeInitializer implements ApplicationRunner {

    private static final String SALES_KB_NAME = "销售 Agent 知识库";
    private static final String SALES_KB_MINIO_PREFIX = "sales-kb/";

    private final KnowledgeBaseRepository kbRepository;
    private final KbDocumentRepository documentRepository;
    private final DocumentLoaderService documentLoaderService;
    private final IndexService indexService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        KnowledgeBase kb = findOrCreateSalesKb();
        List<SalesDoc> salesDocs = docs();
        cleanupRetiredDocs(kb, salesDocs);

        for (SalesDoc doc : salesDocs) {
            ClassPathResource resource = new ClassPathResource(doc.classpath());
            byte[] content = resource.getInputStream().readAllBytes();
            var existing = documentRepository.findByKbIdAndFileNameAndIsDeletedFalse(kb.getId(), doc.fileName());
            if (existing.isPresent() && existing.get().getFileSize().equals((long) content.length)) {
                continue;
            }

            KbDocument kbDocument = existing.orElseGet(KbDocument::new);
            kbDocument.setKbId(kb.getId());
            kbDocument.setFileName(doc.fileName());
            kbDocument.setFileType(fileType(doc.fileName()));
            kbDocument.setFileSize((long) content.length);
            kbDocument.setMinioPath(SALES_KB_MINIO_PREFIX + doc.fileName());
            kbDocument.setUploadedBy(1L);
            kbDocument.setStatus(KbDocument.DocumentStatus.PENDING);
            kbDocument.setErrorMsg(null);
            KbDocument saved = documentRepository.save(kbDocument);

            indexService.submitIndexTask(saved.getId(), extractText(resource, doc.fileName()));
            log.info("[SalesKnowledgeInit] sales kb doc submitted: kbId={}, docId={}, file={}",
                    kb.getId(), saved.getId(), doc.fileName());
        }
    }

    private KnowledgeBase findOrCreateSalesKb() {
        return kbRepository.findByIsDeletedFalse().stream()
                .filter(kb -> SALES_KB_NAME.equals(kb.getName()))
                .findFirst()
                .orElseGet(() -> {
                    KnowledgeBase kb = new KnowledgeBase();
                    kb.setName(SALES_KB_NAME);
                    kb.setDescription("销售流程、管道管理、发现式沟通、异议处理和销售改进建议。");
                    kb.setDepartmentId("SALES");
                    kb.setIsPublic(true);
                    kb.setCreatedBy(1L);
                    return kbRepository.save(kb);
                });
    }

    private void cleanupRetiredDocs(KnowledgeBase kb, List<SalesDoc> salesDocs) {
        Set<String> activeFileNames = salesDocs.stream()
                .map(SalesDoc::fileName)
                .collect(Collectors.toSet());

        documentRepository.findByKbIdAndIsDeletedFalse(kb.getId()).stream()
                .filter(doc -> doc.getMinioPath() != null && doc.getMinioPath().startsWith(SALES_KB_MINIO_PREFIX))
                .filter(doc -> !activeFileNames.contains(doc.getFileName()))
                .forEach(doc -> {
                    doc.setIsDeleted(true);
                    documentRepository.save(doc);
                    log.info("[SalesKnowledgeInit] retired sales kb doc: kbId={}, docId={}, file={}",
                            kb.getId(), doc.getId(), doc.getFileName());
                });
    }

    private List<SalesDoc> docs() {
        return List.of(
                new SalesDoc("sales-playbook.md", "sales-kb-docs/sales-playbook.md"),
                new SalesDoc("pipeline-management.txt", "sales-kb-docs/pipeline-management.txt"),
                new SalesDoc("sales-coaching-and-objection-handling.docx",
                        "sales-kb-docs/sales-coaching-and-objection-handling.docx"),
                new SalesDoc("forecasting-and-anomaly-response.pdf",
                        "sales-kb-docs/forecasting-and-anomaly-response.pdf"),
                new SalesDoc("product-pricing-battlecard.md",
                        "sales-kb-docs/product-pricing-battlecard.md"),
                new SalesDoc("customer-success-handoff.txt",
                        "sales-kb-docs/customer-success-handoff.txt")
        );
    }

    private String extractText(ClassPathResource resource, String fileName) throws Exception {
        try (InputStream input = resource.getInputStream()) {
            ParseResult result = documentLoaderService.load(input, fileName);
            if (!result.isSuccess()) {
                throw new IllegalStateException("Parse failed for " + fileName + ": " + result.getErrorMsg());
            }
            return result.getPages().stream()
                    .map(ParseResult.PageContent::getText)
                    .collect(Collectors.joining("\n\n"));
        }
    }

    private String fileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "TXT" : fileName.substring(dot + 1).toUpperCase();
    }

    private record SalesDoc(String fileName, String classpath) {}
}
