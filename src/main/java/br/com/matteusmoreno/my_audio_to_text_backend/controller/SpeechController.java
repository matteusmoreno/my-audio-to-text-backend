package br.com.matteusmoreno.my_audio_to_text_backend.controller;

import br.com.matteusmoreno.my_audio_to_text_backend.service.SpeechRecognitionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/speech")
public class SpeechController {

    private final SpeechRecognitionService recognitionService;

    public SpeechController(SpeechRecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String recognizeAudio(@RequestParam("file") MultipartFile file) throws IOException, InterruptedException {
        return recognitionService.recognize(file.getInputStream());
    }
}



