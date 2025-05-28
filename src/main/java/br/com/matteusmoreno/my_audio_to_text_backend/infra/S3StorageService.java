package br.com.matteusmoreno.my_audio_to_text_backend.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class S3StorageService {

    private final String region;
    private final String bucketName;
    private final S3Client s3Client;

    public S3StorageService(@Value("${aws.s3.bucket.region}") String region,
                            @Value("${aws.s3.bucket.name}") String bucketName) {
        this.region = region;
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Faz upload de um arquivo para o S3
     */
    public String uploadFile(byte[] fileData, String fileName, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(fileData));

        return getFileUrl(fileName);
    }

    /**
     * Faz download de um arquivo do S3
     */
    public byte[] downloadFile(String fileName) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            return response.readAllBytes();
        }
    }

    /**
     * Retorna o arquivo como InputStream (útil para grandes arquivos)
     */
    public InputStream getFileAsStream(String fileName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        return s3Client.getObject(request);
    }

    /**
     * Lista os arquivos dentro de um prefixo (pasta)
     */
    public List<String> listFiles(String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }

    /**
     * Deleta um arquivo do S3
     */
    public void deleteFile(String fileName) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        s3Client.deleteObject(request);
    }

    /**
     * Gera a URL pública do arquivo
     */
    public String getFileUrl(String fileName) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, fileName);
    }
}

