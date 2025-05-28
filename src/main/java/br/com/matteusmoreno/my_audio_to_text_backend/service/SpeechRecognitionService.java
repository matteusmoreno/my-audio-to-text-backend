package br.com.matteusmoreno.my_audio_to_text_backend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Service
public class SpeechRecognitionService {

    private final Map<String, Model> models = new HashMap<>();

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.model.pt}")
    private String ptModelPrefix;

    @Value("${aws.s3.model.en}")
    private String enModelPrefix;

    @Value("${aws.s3.bucket.region}")
    private String region;

    private S3Client s3;

    @PostConstruct
    public void init() throws IOException {
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        // Carrega os modelos PT-BR e EN no mapa cacheando localmente
        models.put("pt-br", loadModelFromS3(ptModelPrefix));
        models.put("en", loadModelFromS3(enModelPrefix));
    }

    @PreDestroy
    public void cleanup() {
        models.values().forEach(Model::close);
        s3.close();
    }

    private Model loadModelFromS3(String prefix) throws IOException {
        Path tempDir = Files.createTempDirectory("vosk-model-");
        System.out.println("Baixando modelo S3 do prefixo: " + prefix);

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix.endsWith("/") ? prefix : prefix + "/")
                .build();

        ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);

        if (listResponse.contents().isEmpty()) {
            throw new IOException("Nenhum arquivo encontrado no prefixo " + prefix);
        }

        for (S3Object s3Object : listResponse.contents()) {
            String key = s3Object.key();
            // Pega o caminho relativo ao prefixo
            String relativePath = key.substring(prefix.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            if (relativePath.isEmpty()) {
                continue;
            }

            Path targetPath = tempDir.resolve(relativePath);
            Files.createDirectories(targetPath.getParent());

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3ObjectStream = s3.getObject(getRequest)) {
                Files.copy(s3ObjectStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        System.out.println("Modelo baixado para: " + tempDir.toAbsolutePath());
        return new Model(tempDir.toFile().getAbsolutePath());
    }

    /**
     * Converte o InputStream do áudio para formato PCM 16kHz mono se necessário,
     * para ser aceito pelo vosk.
     */
    private InputStream convertAudioToPCM16kMono(InputStream audioStream) throws IOException, UnsupportedAudioFileException {
        BufferedInputStream bufferedIn = new BufferedInputStream(audioStream);
        AudioInputStream ais = AudioSystem.getAudioInputStream(bufferedIn);
        AudioFormat baseFormat = ais.getFormat();

        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                16000,
                16,
                1,
                2,
                16000,
                false);

        AudioInputStream converted = AudioSystem.getAudioInputStream(targetFormat, ais);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = converted.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        converted.close();
        ais.close();

        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Reconhece o áudio wav já convertido.
     */
    private String recognizeWav(InputStream wavInputStream, Model model) throws IOException {
        try (Recognizer recognizer = new Recognizer(model, 16000)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = wavInputStream.read(buffer)) >= 0) {
                if (!recognizer.acceptWaveForm(buffer, bytesRead)) {
                    // partial result ignored for now
                }
            }
            return recognizer.getFinalResult();
        }
    }

    /**
     * Reconhece o áudio de entrada em qualquer formato, convertendo com ffmpeg e
     * usando o modelo do idioma solicitado.
     */
    public String recognize(InputStream audioInputStream, String originalFormat, String language) throws IOException, InterruptedException {
        Model model = models.get(language.toLowerCase());
        if (model == null) {
            throw new IllegalArgumentException("Modelo de idioma não suportado: " + language);
        }

        // Salva o arquivo original temporariamente com extensão
        File originalAudio = File.createTempFile("audio_input", "." + originalFormat);
        Files.copy(audioInputStream, originalAudio.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Arquivo wav convertido
        File wavAudio = File.createTempFile("audio_converted", ".wav");

        // Converter para WAV mono 16kHz com ffmpeg
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y",
                "-i", originalAudio.getAbsolutePath(),
                "-ar", "16000",
                "-ac", "1",
                "-f", "wav",
                wavAudio.getAbsolutePath()
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            originalAudio.delete();
            wavAudio.delete();
            throw new IOException("Erro na conversão de áudio com ffmpeg");
        }

        try (InputStream wavInputStream = new FileInputStream(wavAudio)) {
            return recognizeWav(wavInputStream, model);
        } finally {
            originalAudio.delete();
            wavAudio.delete();
        }
    }
}
