/**
 * CG-DOC: Unit or integration tests for File Upload Controller Test in the file service.
 */
package com.circleguard.file.controller;

import com.circleguard.file.service.FileStorage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileUploadController.class)
@Import(SimpleMeterRegistry.class)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorage storageService;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "mock content".getBytes());

        Mockito.when(storageService.saveFile(Mockito.any())).thenReturn("certificate.pdf");

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("certificate.pdf"));
        org.junit.jupiter.api.Assertions.assertEquals(1.0,
                meterRegistry.counter("circleguard_file_uploads_total", "result", "success").count());
    }

    @Test
    void shouldDownloadExistingFile() throws Exception {
        Resource resource = new ByteArrayResource("mock content".getBytes()) {
            @Override
            public String getFilename() {
                return "certificate.pdf";
            }
        };
        Mockito.when(storageService.loadFile("certificate.pdf")).thenReturn(resource);

        mockMvc.perform(get("/api/v1/files/download/certificate.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("mock content".getBytes()));
    }

    @Test
    void shouldReturnNotFoundWhenFileDoesNotExist() throws Exception {
        Mockito.when(storageService.loadFile("missing.pdf")).thenReturn(null);

        mockMvc.perform(get("/api/v1/files/download/missing.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUploadEmptyFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        Mockito.when(storageService.saveFile(Mockito.any())).thenReturn("uuid_empty.pdf");

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("uuid_empty.pdf"));
        org.junit.jupiter.api.Assertions.assertEquals(2.0,
                meterRegistry.counter("circleguard_file_uploads_total", "result", "success").count());
    }

    @Test
    void shouldCountFailedUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.pdf", "application/pdf", "mock content".getBytes());
        Mockito.when(storageService.saveFile(Mockito.any())).thenThrow(new RuntimeException("storage error"));

        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () -> mockMvc.perform(multipart("/api/v1/files/upload").file(file)));
        org.junit.jupiter.api.Assertions.assertEquals(1.0,
                meterRegistry.counter("circleguard_file_uploads_total", "result", "failure").count());
    }

    @Test
    void shouldDeleteExistingFile() throws Exception {
        Mockito.when(storageService.deleteFile("certificate.pdf")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/files/certificate.pdf"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFoundWhenFileToDeleteDoesNotExist() throws Exception {
        Mockito.when(storageService.deleteFile("missing.pdf")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/files/missing.pdf"))
                .andExpect(status().isNotFound());
    }
}