package br.com.matteusmoreno.my_audio_to_text_backend.service;

import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;

@Service
public class SpeechRecognitionService {

    private final Model model;

    public SpeechRecognitionService() throws IOException {
        String userDir = System.getProperty("user.dir");
        String modelPath = userDir + "/src/main/resources/models/vosk-model-small-pt-0.3";
        this.model = new Model(modelPath);
    }

    /**
     * Recebe InputStream de áudio em qualquer formato suportado pelo ffmpeg,
     * converte para PCM 16kHz mono WAV e realiza o reconhecimento.
     */
    public String recognize(InputStream audioStream) throws IOException, InterruptedException {
        // Cria arquivo temporário para áudio original
        File tempInputFile = File.createTempFile("audio_input_", null);
        try (OutputStream os = new FileOutputStream(tempInputFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        // Cria arquivo temporário para áudio convertido
        File tempWavFile = File.createTempFile("audio_converted_", ".wav");

        // Monta comando ffmpeg para converter arquivo para PCM WAV 16k mono
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y", // sobrescreve arquivo de saída se existir
                "-i", tempInputFile.getAbsolutePath(),
                "-ar", "16000",
                "-ac", "1",
                "-c:a", "pcm_s16le",
                tempWavFile.getAbsolutePath()
        );

        // Executa comando e espera terminar
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // Pode capturar erro do ffmpeg aqui para depuração
            try (InputStream errorStream = process.getErrorStream()) {
                String errorMsg = new String(errorStream.readAllBytes());
                throw new IOException("Erro ao converter áudio com ffmpeg: " + errorMsg);
            }
        }

        // Agora reconhece o áudio WAV convertido
        String result;
        try (InputStream wavStream = new FileInputStream(tempWavFile)) {
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            byte[] buf = new byte[4096];
            int n;
            while ((n = wavStream.read(buf)) >= 0) {
                if (!recognizer.acceptWaveForm(buf, n)) {
                    // Pode pegar resultados intermediários aqui, se quiser
                }
            }
            result = recognizer.getFinalResult();
            recognizer.close();
        }

        // Apaga arquivos temporários
        tempInputFile.delete();
        tempWavFile.delete();

        return result;
    }
}
