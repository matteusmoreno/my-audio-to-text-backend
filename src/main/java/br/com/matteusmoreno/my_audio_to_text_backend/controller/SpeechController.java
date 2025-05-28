package br.com.matteusmoreno.my_audio_to_text_backend.controller;

import br.com.matteusmoreno.my_audio_to_text_backend.service.SpeechRecognitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/speech")
public class SpeechController {

    private final SpeechRecognitionService speechRecognitionService;

    public SpeechController(SpeechRecognitionService speechRecognitionService) {
        this.speechRecognitionService = speechRecognitionService;
    }

    @PostMapping("/recognize")
    public ResponseEntity<?> recognizeAudio(
            @RequestParam("file") MultipartFile file,
             String language) {

        if (file.isEmpty() || language == null || language.isBlank()) {
            return ResponseEntity.badRequest().body("Arquivo de áudio e idioma são obrigatórios.");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
            }
            if (extension.isEmpty()) {
                return ResponseEntity.badRequest().body("Extensão do arquivo não reconhecida.");
            }

            String transcription = speechRecognitionService.recognize(file.getInputStream(), extension, language);
            return ResponseEntity.ok(transcription);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro na transcrição: " + e.getMessage());
        }
    }
}




