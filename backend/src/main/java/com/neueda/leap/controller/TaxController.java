package com.neueda.leap.controller;

import com.neueda.leap.ApiRoutes;
import com.neueda.leap.models.TaxReport;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.TaxPdfGenerator;
import com.neueda.leap.service.TaxReportService;
import java.io.IOException;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ApiRoutes.TAX)
public class TaxController {
    private final TaxReportService reports;
    private final TaxPdfGenerator pdf;
    private final ClientService clients;

    public TaxController(TaxReportService reports, TaxPdfGenerator pdf, ClientService clients) {
        this.reports = reports;
        this.pdf = pdf;
        this.clients = clients;
    }

    @GetMapping(ApiRoutes.TAX_REPORT)
    public ResponseEntity<TaxReport> report(@RequestParam(required = false) Integer year,
                                            @RequestParam(defaultValue = "1099-B") String document,
                                            Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reports.report(clientId(principal), year == null ? reports.currentYear() : year, document));
    }

    @GetMapping(value = ApiRoutes.TAX_DOCUMENT, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable String document,
                                           @RequestParam(required = false) Integer year,
                                           Principal principal) throws IOException {
        var report = reports.report(clientId(principal), year == null ? reports.currentYear() : year, document);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(report.year() + "-" + report.document() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(pdf.generate(report));
    }

    private int clientId(Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return clients.findActiveClientIdByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
}
