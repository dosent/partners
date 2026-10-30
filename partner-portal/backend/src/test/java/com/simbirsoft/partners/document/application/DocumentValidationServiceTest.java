package com.simbirsoft.partners.document.application;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class DocumentValidationServiceTest {
    @Test void acceptsDocxAndNormalizesMime() {
        var result=new DocumentValidationService(bytes->MalwareScanner.Result.CLEAN)
            .prepare("CV.DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document; charset=utf-8", new byte[]{'P','K',3,4,0});
        assertThat(result.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(result.bytes()).containsExactly((byte)'P',(byte)'K',(byte)3,(byte)4,(byte)0);
    }
    @Test void rejectsEmptyAndMismatchedDocuments() {
        var validator=new DocumentValidationService(bytes->MalwareScanner.Result.CLEAN);
        for(byte[] content:new byte[][] {null, new byte[0], "%PDF-1.4".getBytes()}) {
            assertThatThrownBy(()->validator.prepare("cv.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",content))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        assertThatThrownBy(()->validator.prepare("cv.pdf","application/octet-stream","%PDF-1.4".getBytes()))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
    @Test void acceptsTheExactSizeLimit() {
        byte[] content=new byte[20*1024*1024];
        System.arraycopy("%PDF-1.4".getBytes(),0,content,0,8);
        assertThat(new DocumentValidationService(bytes->MalwareScanner.Result.CLEAN)
            .prepare("cv.pdf","application/pdf",content).bytes()).hasSize(20*1024*1024);
    }
    @Test void acceptsCleanPdfAndProtectsContent() {
        var input="%PDF-1.4 test CV".getBytes();
        var result=new DocumentValidationService(bytes->MalwareScanner.Result.CLEAN).prepare("../cv.pdf","application/pdf",input);
        input[0]=0;
        assertThat(result.fileName()).isEqualTo("cv.pdf");
        assertThat(result.bytes()[0]).isEqualTo((byte)'%');
        var exposed=result.bytes(); exposed[0]=0;
        assertThat(result.bytes()[0]).isEqualTo((byte)'%');
        assertThat(result.sha256()).hasSize(64);
    }
    @Test void rejectsWrongTypeOversizeAndInfectedFile() {
        var validator=new DocumentValidationService(bytes->MalwareScanner.Result.CLEAN);
        assertThatThrownBy(()->validator.prepare("cv.pdf","application/pdf",new byte[]{1,2})).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->validator.prepare("cv.pdf","application/pdf",new byte[20*1024*1024+1]))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(413));
        assertThatThrownBy(()->new DocumentValidationService(bytes->MalwareScanner.Result.INFECTED)
            .prepare("cv.pdf","application/pdf","%PDF-1.4".getBytes()))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(422));
    }
    @Test void scannerFailureDoesNotAllowFile() {
        assertThatThrownBy(()->new DocumentValidationService(bytes->{throw new IllegalStateException();})
            .prepare("cv.pdf","application/pdf","%PDF-1.4".getBytes()))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(503));
    }
}
