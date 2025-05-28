package br.com.matteusmoreno.my_audio_to_text_backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class SpeechRecognitionService {

    private final Map<String, Model> models = new HashMap<>();

    @Value("${vosk.model.path.pt}")
    private String ptModelPath;

    @Value("${vosk.model.path.en}")
    private String enModelPath;

    // Carrega os modelos após a injeção das propriedades
    @PostConstruct
    public void init() throws IOException {
        models.put("pt-br", new Model(ptModelPath));
        models.put("en", new Model(enModelPath));
    }

    // Fecha os modelos ao desligar a aplicação
    @PreDestroy
    public void cleanup() {
        models.values().forEach(Model::close);
    }

    public String recognize(InputStream audioStream, String language) throws IOException, InterruptedException {
        Model model = models.get(language.toLowerCase());
        if (model == null) {
            throw new IllegalArgumentException("Idioma não suportado: " + language);
        }

        File tempInputFile = File.createTempFile("audio_input_", null);
        try (OutputStream os = new FileOutputStream(tempInputFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        File tempWavFile = File.createTempFile("audio_converted_", ".wav");

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", tempInputFile.getAbsolutePath(),
                "-ar", "16000",
                "-ac", "1",
                "-c:a", "pcm_s16le",
                tempWavFile.getAbsolutePath()
        );

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            try (InputStream errorStream = process.getErrorStream()) {
                String errorMsg = new String(errorStream.readAllBytes());
                throw new IOException("Erro ao converter áudio com ffmpeg: " + errorMsg);
            }
        }

        String result;
        try (InputStream wavStream = new FileInputStream(tempWavFile)) {
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            byte[] buf = new byte[4096];
            int n;
            while ((n = wavStream.read(buf)) >= 0) {
                recognizer.acceptWaveForm(buf, n);
            }
            result = recognizer.getFinalResult();
            recognizer.close();
        }

        tempInputFile.delete();
        tempWavFile.delete();

        return result;
    }
}
