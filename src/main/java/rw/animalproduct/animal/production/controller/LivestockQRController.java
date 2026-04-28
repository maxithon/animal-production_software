package rw.animalproduct.animal.production.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.services.LivestockService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

@Controller
@RequestMapping("/livestock/qr")
public class LivestockQRController {

    private final LivestockService livestockService;

    public LivestockQRController(LivestockService livestockService) {
        this.livestockService = livestockService;
    }

    // ── QR Code image endpoint ─────────────────────────────────────────
    // GET /livestock/qr/image/{id}  → returns PNG image bytes
    @GetMapping(value = "/image/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] generateQRCode(@PathVariable UUID id,
                                 @RequestParam(defaultValue = "200") int size) throws WriterException, IOException {

        Livestock animal = livestockService.getById(id)
                .orElseThrow(() -> new RuntimeException("Animal not found"));

        // Build QR content — compact yet complete
        String content = buildQRContent(animal);

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix   = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    // ── Single ear tag view ────────────────────────────────────────────
    @GetMapping("/eartag/{id}")
    public String earTagView(@PathVariable UUID id, Model model) {
        Livestock animal = livestockService.getById(id)
                .orElseThrow(() -> new RuntimeException("Animal not found"));
        model.addAttribute("animal", animal);
        model.addAttribute("qrContent", buildQRContent(animal));
        return "livestock-eartag";
    }

    // ── Bulk ear tag print ─────────────────────────────────────────────
    @GetMapping("/eartags/print")
    public String bulkEarTagPrint(@RequestParam List<UUID> ids, Model model) {
        List<Livestock> animals = new ArrayList<>();
        for (UUID id : ids) {
            livestockService.getById(id).ifPresent(animals::add);
        }
        model.addAttribute("animals", animals);
        return "livestock-eartags-print";
    }

    // ── Print ALL active animals ───────────────────────────────────────
    @GetMapping("/eartags/print-all")
    public String printAllEarTags(Model model) {
        List<Livestock> animals = livestockService.getAll();
        model.addAttribute("animals", animals);
        return "livestock-eartags-print";
    }

    // ── QR data as JSON (for API use) ──────────────────────────────────
    @GetMapping(value = "/data/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getQRData(@PathVariable UUID id) {
        Livestock animal = livestockService.getById(id)
                .orElseThrow(() -> new RuntimeException("Animal not found"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id",       animal.getId());
        data.put("tag",      animal.getTagNumber());
        data.put("gender",   animal.getGender());
        data.put("status",   animal.getStatus());
        data.put("category", animal.getLivestockCategory() != null ? animal.getLivestockCategory().getName() : null);

        data.put("beneficiary", animal.getBeneficiary() != null
                ? animal.getBeneficiary().getFirstName() + " " + animal.getBeneficiary().getLastName()
                : null);
        data.put("location", animal.getLocation() != null ? animal.getLocation().getName() : null);
        data.put("received", animal.getDateReceived());
        data.put("value",    animal.getCurrentValue());
        data.put("qrContent", buildQRContent(animal));
        return data;
    }

    // ── Build QR content string ────────────────────────────────────────
    private String buildQRContent(Livestock a) {
        StringBuilder sb = new StringBuilder();
        sb.append("LIVESTOCK RECORD\n");
        sb.append("Tag: ").append(a.getTagNumber()).append("\n");
        if (a.getLivestockCategory() != null)
            sb.append("Type: ").append(a.getLivestockCategory().getName()).append("\n");
        if (a.getGender() != null)
            sb.append("Gender: ").append(a.getGender()).append("\n");
        sb.append("Status: ").append(a.getStatus()).append("\n");
        if (a.getDateReceived() != null)
            sb.append("Received: ").append(a.getDateReceived()).append("\n");
        if (a.getBeneficiary() != null)
            sb.append("Owner: ").append(a.getBeneficiary().getFirstName())
              .append(" ").append(a.getBeneficiary().getLastName()).append("\n");
        if (a.getLocation() != null)
            sb.append("Location: ").append(a.getLocation().getName()).append("\n");
        if (a.getCurrentValue() != null)
            sb.append("Value: ").append(a.getCurrentValue()).append(" RWF\n");
        sb.append("ID: ").append(a.getId());
        return sb.toString();
    }
}
