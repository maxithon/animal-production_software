package rw.animalproduct.animal.production.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);
    private static final String PHOTO_UPLOAD_DIR = "uploads/photos/";
    private static final String PDF_UPLOAD_DIR = "uploads/contracts/";
    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_PDF_SIZE = 10 * 1024 * 1024; // 10MB

    @PostMapping("/photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        logger.info("Photo upload request received");
        try {
            if (file.isEmpty()) {
                logger.warn("Empty file received");
                return ResponseEntity.badRequest().body(createErrorResponse("Please select a file"));
            }

            String contentType = file.getContentType();
            logger.info("File details - Name: {}, Size: {}, Type: {}",
                    file.getOriginalFilename(), file.getSize(), contentType);

            if (contentType == null || !contentType.startsWith("image/")) {
                logger.warn("Invalid file type: {}", contentType);
                return ResponseEntity.badRequest().body(createErrorResponse("Only image files are allowed"));
            }

            if (file.getSize() > MAX_PHOTO_SIZE) {
                logger.warn("File too large: {} bytes", file.getSize());
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("File size must be less than 5MB"));
            }

            File uploadDir = new File(PHOTO_UPLOAD_DIR);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                logger.info("Upload directory created: {}", created);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String newFilename = UUID.randomUUID().toString() + extension;
            Path filePath = Paths.get(PHOTO_UPLOAD_DIR + newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Photo saved successfully: {}", newFilename);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filename", newFilename);
            response.put("url", "/uploads/photos/" + newFilename);
            response.put("message", "Photo uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error uploading photo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to upload file: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during photo upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Unexpected error: " + e.getMessage()));
        }
    }

    @PostMapping("/pdf")
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {
        logger.info("PDF upload request received");
        try {
            if (file.isEmpty()) {
                logger.warn("Empty PDF file received");
                return ResponseEntity.badRequest().body(createErrorResponse("Please select a PDF file"));
            }

            String contentType = file.getContentType();
            logger.info("PDF file details - Name: {}, Size: {} bytes, Type: {}",
                    file.getOriginalFilename(), file.getSize(), contentType);

            // Validate PDF content type
            if (contentType == null ||
                    (!contentType.equals("application/pdf") && !contentType.equals("application/octet-stream"))) {
                logger.warn("Invalid PDF content type: {}", contentType);

                // Additional check: verify file extension
                String filename = file.getOriginalFilename();
                if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                    return ResponseEntity.badRequest()
                            .body(createErrorResponse("Only PDF files are allowed"));
                }
                logger.info("File extension is .pdf, accepting despite content type");
            }

            // Check file size (max 10MB)
            if (file.getSize() > MAX_PDF_SIZE) {
                logger.warn("PDF file too large: {} bytes", file.getSize());
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("PDF file size must be less than 10MB"));
            }

            // Create upload directory if it doesn't exist
            File uploadDir = new File(PDF_UPLOAD_DIR);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                logger.info("PDF upload directory created: {}", created);
            }

            String originalFilename = file.getOriginalFilename();
            String newFilename = UUID.randomUUID().toString() + ".pdf";
            Path filePath = Paths.get(PDF_UPLOAD_DIR + newFilename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("PDF saved successfully: {} (original: {})", newFilename, originalFilename);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filename", newFilename);
            response.put("url", "/uploads/contracts/" + newFilename);
            response.put("originalName", originalFilename);
            response.put("message", "PDF uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error uploading PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to upload PDF: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during PDF upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Unexpected error: " + e.getMessage()));
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        logger.warn("Error response: {}", message);
        return response;
    }
}